package com.kipu.app.feature.accounts.presentation.instruments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.presentation.components.CreditPurchaseDraft
import com.kipu.app.feature.accounts.presentation.components.CreditPurchaseDraftSaver
import com.kipu.app.feature.accounts.presentation.components.toSavedValues
import com.kipu.app.feature.accounts.domain.model.PurchaseType
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import com.kipu.app.feature.accounts.domain.usecase.GetReferentialRates
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.categories.domain.MerchantPreferenceResolution
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.usecase.CreateCategory
import com.kipu.app.feature.categories.domain.usecase.EnsureInitialCategoryCatalog
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.ResolvePreferredCategory
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.movements.presentation.toCategoryOption
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstallmentSimulatorUiState(
    val draft: CreditPurchaseDraft? = null,
    val merchantName: String = "",
    val merchantId: String? = null,
    val categoryId: String? = null,
    val categories: List<CategoryOption> = emptyList(),
    val categoriesLoading: Boolean = true,
    val resolvingMerchant: Boolean = false,
    val categoryError: String? = null,
    val creatingCategory: Boolean = false,
    val categoryCreationError: String? = null,
    val products: List<CreditProductReference> = emptyList(),
    val referenceLoading: Boolean = true,
) {
    val category: CategoryOption? get() = categories.firstOrNull { it.id == categoryId }
}

/** Classification uses the same catalog and preference rules as quick movement entry. */
@HiltViewModel
class InstallmentSimulatorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val session: SessionCoordinator,
    private val observeCategories: ObserveCategories,
    private val ensureCatalog: EnsureInitialCategoryCatalog,
    private val createCategory: CreateCategory,
    private val resolvePreferredCategory: ResolvePreferredCategory,
    private val syncScheduler: CategorySyncScheduler,
    private val getReferentialRates: GetReferentialRates,
) : ViewModel() {
    private val _state = MutableStateFlow(InstallmentSimulatorUiState())
    val state = _state.asStateFlow()
    private var initializedId: String? = null
    private var categoryExplicit = false
    private var preferenceJob: Job? = null
    private val rememberedCategories = mutableMapOf<String, String>()

    fun initialize(candidate: PurchaseCandidate) {
        if (initializedId == candidate.id) return
        initializedId = candidate.id
        val restoring = savedStateHandle.get<String>("simulator.id") == candidate.id
        categoryExplicit = restoring && savedStateHandle.get<Boolean>("simulator.categoryExplicit") == true
        val restoredDraft = if (restoring) savedStateHandle.get<ArrayList<String>>("simulator.terms")
            ?.let(CreditPurchaseDraftSaver::restore) else null
        _state.update { it.copy(
            draft = restoredDraft ?: CreditPurchaseDraft(
                purchaseType = if (candidate.suggestedInstallments == 1) PurchaseType.DIRECT else PurchaseType.INSTALLMENTS,
                installmentsText = candidate.suggestedInstallments.coerceAtLeast(2).toString(),
            ),
            merchantName = if (restoring) savedStateHandle["simulator.merchant"] ?: candidate.merchant else candidate.merchant,
            merchantId = if (restoring) savedStateHandle["simulator.merchantId"] else null,
            categoryId = if (restoring) savedStateHandle["simulator.categoryId"] else candidate.categoryId,
        ) }
        persist()
        val owner = ownerId()
        if (owner == null) {
            _state.update { it.copy(categoriesLoading = false, categoryError = "No se pudo identificar el perfil local.") }
        } else viewModelScope.launch {
            try {
                ensureCatalog(owner)
                syncScheduler.scheduleSync(owner.value)
                observeCategories(owner).catch {
                    _state.update { current -> current.copy(categoriesLoading = false, categoryError = "No se pudieron cargar las categorías.") }
                }.collect { roots ->
                    val options = roots.filter { it.category.isActive }
                        .flatMap { root -> listOf(root to null) + root.subcategories.map { it to root } }
                        .filter { (item, _) -> item.category.isActive && !item.category.isPlanLocked && item.category.categoryType != CategoryType.INCOME }
                        .map { (item, parent) -> item.toCategoryOption(parent) }
                    _state.update { current -> current.copy(
                        categories = options, categoriesLoading = false,
                        categoryId = current.categoryId?.takeIf { id -> options.any { it.id == id } },
                    ) }
                    persist()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(categoriesLoading = false, categoryError = "No se pudieron cargar las categorías.") } }
        }
        viewModelScope.launch {
            try {
                getReferentialRates().onSuccess { products -> _state.update { it.copy(products = products) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A missing reference leaves the contractual TEA editable. */ }
            finally { _state.update { it.copy(referenceLoading = false) } }
        }
    }

    fun updateDraft(draft: CreditPurchaseDraft) {
        _state.update { it.copy(draft = draft) }
        savedStateHandle["simulator.terms"] = draft.toSavedValues()
    }

    fun selectCategory(category: CategoryOption) {
        if (_state.value.categories.none { it.id == category.id }) return
        categoryExplicit = true
        preferenceJob?.cancel()
        _state.update { it.copy(categoryId = category.id, categoryError = null, resolvingMerchant = false) }
        persist()
    }

    fun selectMerchant(merchant: MerchantCatalogEntry) {
        preferenceJob?.cancel()
        val before = _state.value
        val suggestion = merchant.defaultCategoryId?.value?.let { id -> before.categories.firstOrNull { it.id == id } }
            ?: rememberedCategory(merchant.name)
        _state.update { it.copy(merchantName = merchant.name, merchantId = merchant.id.value,
            categoryId = if (categoryExplicit) it.categoryId else suggestion?.id,
            categoryError = null, resolvingMerchant = !categoryExplicit) }
        persist()
        if (categoryExplicit) return
        val owner = ownerId()
        if (owner == null) {
            _state.update { it.copy(resolvingMerchant = false) }
            return
        }
        preferenceJob = viewModelScope.launch {
            try {
                val resolved = resolvePreferredCategory(owner, merchant.id, MovementType.EXPENSE, suggestion?.let { CategoryId(it.id) })
                val resolvedId = when (resolved) {
                    is MerchantPreferenceResolution.Preferred -> resolved.categoryId.value
                    is MerchantPreferenceResolution.Suggested -> resolved.categoryId.value
                    MerchantPreferenceResolution.NeedsNewChoice, MerchantPreferenceResolution.Unclassified -> null
                }
                _state.update { current -> if (categoryExplicit || current.merchantId != merchant.id.value) current else
                    current.copy(categoryId = resolvedId?.takeIf { id -> current.categories.any { it.id == id } }, resolvingMerchant = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.update { current -> if (categoryExplicit || current.merchantId != merchant.id.value) current else
                    current.copy(categoryId = null, resolvingMerchant = false, categoryError = "No se pudo validar la preferencia del comercio.") }
            }
            persist()
        }
    }

    fun setProvisionalMerchant(text: String) {
        preferenceJob?.cancel()
        val name = text.trim()
        _state.update { it.copy(merchantName = name, merchantId = null, categoryError = null,
            resolvingMerchant = false, categoryId = if (categoryExplicit) it.categoryId else rememberedCategory(name)?.id) }
        persist()
    }

    fun clearMerchant() {
        preferenceJob?.cancel()
        _state.update { it.copy(merchantName = "", merchantId = null, resolvingMerchant = false) }
        persist()
    }

    suspend fun createCategoryOrSubcategory(parentId: String?, name: String, icon: String, color: String,
        rememberFrequentMerchant: Boolean): Result<CategoryOption> {
        val owner = ownerId() ?: return Result.failure(IllegalStateException("No se pudo identificar el perfil local."))
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Escribe un nombre para la categoría."))
        val parent = parentId?.let { id -> _state.value.categories.firstOrNull { it.id == id && it.parentCategoryId == null } }
        if (parentId != null && parent == null) return Result.failure(IllegalArgumentException("Selecciona una categoría principal válida."))
        _state.update { it.copy(creatingCategory = true, categoryCreationError = null) }
        return try {
            createCategory(owner, name.trim(), icon, color, parent?.let { CategoryId(it.id) }, parent?.categoryType ?: CategoryType.EXPENSE).fold(
                onSuccess = { created ->
                    val option = CategoryOption(created.id.value, name.trim(), icon, created.categoryType, parent?.id, parent?.name, color)
                    _state.update { it.copy(categories = (it.categories + option).distinctBy { item -> item.id }) }
                    if (rememberFrequentMerchant) _state.value.merchantName.takeIf(String::isNotBlank)?.let { rememberedCategories[normalize(it)] = option.id }
                    selectCategory(option)
                    syncScheduler.scheduleSync(owner.value)
                    Result.success(option)
                },
                onFailure = { error ->
                    _state.update { it.copy(categoryCreationError = "No se pudo crear la categoría.") }
                    Result.failure(error)
                },
            )
        } finally { _state.update { it.copy(creatingCategory = false) } }
    }

    private fun rememberedCategory(name: String): CategoryOption? = rememberedCategories[normalize(name)]?.let { id -> _state.value.categories.firstOrNull { it.id == id } }
    private fun normalize(name: String) = name.trim().lowercase(Locale.ROOT)
    private fun ownerId() = (session.currentOwner?.verifiedUserId ?: (session.localAccess.value as? LocalAccess.Available)?.userId)?.let(::UserId)
    private fun persist() {
        val current = _state.value
        savedStateHandle["simulator.id"] = initializedId
        savedStateHandle["simulator.merchant"] = current.merchantName
        savedStateHandle["simulator.merchantId"] = current.merchantId
        savedStateHandle["simulator.categoryId"] = current.categoryId
        savedStateHandle["simulator.categoryExplicit"] = categoryExplicit
    }
}
