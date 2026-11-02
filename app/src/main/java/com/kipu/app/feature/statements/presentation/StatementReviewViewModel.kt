package com.kipu.app.feature.statements.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.DebitCard
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.presentation.RememberOption
import com.kipu.app.feature.movements.presentation.rememberCategoryOption
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.statements.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RememberAliasOption(val label: String, val checked: Boolean = true, val merchantId: String, val sourceText: String)

private data class StatementRememberChoices(
    val merchants: List<MerchantCatalogEntry>,
    val aliases: Map<String, RememberAliasOption>,
    val categories: Map<String, RememberOption>,
)

data class StatementReviewUiState(
    val loading: Boolean = true,
    val merchants: List<MerchantCatalogEntry> = emptyList(),
    val rememberAliasOptions: Map<String, RememberAliasOption> = emptyMap(),
    val rememberCategoryOptions: Map<String, RememberOption> = emptyMap(),
    val learningWarnings: List<StatementLearningWarning> = emptyList(),
    val snapshot: StatementImportSnapshot? = null,
    val cards: List<CreditCard> = emptyList(),
    val debitCards: List<DebitCard> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    val incomeCategories: List<CategoryOption> = emptyList(),
    val busy: Boolean = false,
    val missingCategoryIds: Set<String> = emptySet(),
    val failures: Map<String, StatementLineFailureReason> = emptyMap(),
    val message: String? = null,
    val successCount: Int? = null,
    val discarded: Boolean = false,
    val showPremium: Boolean = false,
) {
    val source: StatementSource get() = snapshot?.statement?.source ?: StatementSource.CREDIT_CARD
    val selectedCard: Card? get() = if (source == StatementSource.DEPOSIT_ACCOUNT) debitCards.find { it.id.value == snapshot?.statement?.cardId } else card
    val card: CreditCard? get() = cards.find { it.id.value == snapshot?.statement?.cardId }
    val selected: List<StatementImportLine> get() = snapshot?.lines.orEmpty().filter {
        it.selected && it.status == StatementImportLineStatus.PENDING
    }
    val canConfirm: Boolean get() = !loading && !busy && selectedCard != null && selected.isNotEmpty() &&
        snapshot?.statement?.status == StatementImportStatus.PENDING && successCount == null

    /** Abonos take income categories; every other movement, the expense ones. */
    fun categoriesFor(kind: StatementLineKind): List<CategoryOption> = if (kind == StatementLineKind.ACCOUNT_CREDIT) incomeCategories else categories

    fun selectable(line: StatementImportLine): Boolean = line.status == StatementImportLineStatus.PENDING &&
        line.kind !in setOf(StatementLineKind.POINTS_REDEMPTION, StatementLineKind.INSTALLMENT_MONTHLY_CHARGE) &&
        (selectedCard == null || selectedCard?.currency == line.currency)
}

@HiltViewModel
class StatementReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: StatementImportStore,
    private val prepare: PrepareStatementImportReview,
    private val confirmer: ConfirmStatementImport,
    private val instruments: ObserveInstruments,
    private val categories: ObserveCategories,
    private val session: SessionCoordinator,
    private val merchantRepository: CategoriesRepository,
) : ViewModel() {
    private val importId: String = savedStateHandle.get<String>("importId").orEmpty()
    private var owner: String? = (session.localAccess.value as? LocalAccess.Available)?.userId
    private val _state = MutableStateFlow(StatementReviewUiState())
    val state: StateFlow<StatementReviewUiState> = _state.asStateFlow()
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            session.localAccess.collectLatest { access ->
                if (owner == null && access is LocalAccess.Available) owner = access.userId
                if (!hasAccess()) {
                    operation?.cancel()
                    _state.value = StatementReviewUiState(loading = false, message = "Desbloquea tu sesión para revisar el estado de cuenta.")
                } else if (importId.isBlank()) {
                    _state.value = StatementReviewUiState(loading = false, message = StatementLineFailureReason.NotFound.message())
                } else coroutineScope {
                    val user = requireNotNull(owner)
                    launch {
                        try {
                            prepare(user, importId)
                            val rememberInputs = combine(
                                merchantRepository.observeMerchantCatalog(),
                                merchantRepository.observeMerchantAliasRules(UserId(user)),
                                merchantRepository.observeMerchantCategoryPreferences(UserId(user)),
                                categories(UserId(user)),
                                merchantRepository.observePremiumVerified(UserId(user)).catch { emit(false) },
                            ) { catalog, aliases, preferences, categoryItems, premium ->
                                val flatCategories = categoryItems.flatMap { listOf(it) + it.subcategories }
                                val allCategories = flatCategories.map { it.category }
                                val compute: (StatementImportSnapshot?) -> StatementRememberChoices = { snapshot ->
                                    val aliasOptions = mutableMapOf<String, RememberAliasOption>()
                                    val categoryOptions = mutableMapOf<String, RememberOption>()
                                    for (line in snapshot?.lines.orEmpty().filter { it.status == StatementImportLineStatus.PENDING }) {
                                        val merchant = catalog.singleOrNull { it.isActive && it.id.value == line.merchantId } ?: continue
                                        if (premium && line.merchantReviewed && aliases.none { it.ownerId == UserId(user) && it.deletedAt == null &&
                                            it.normalizedPattern == MerchantAliasRules.normalize(line.originalDescription) }) {
                                            aliasOptions[line.id] = RememberAliasOption("Recordar ${merchant.name} para '${line.originalDescription}'",
                                                line.rememberMerchantAlias, merchant.id.value, line.originalDescription)
                                        }
                                        val category = allCategories.singleOrNull { it.id.value == line.categoryId }
                                        rememberCategoryOption(UserId(user), premium, merchant.id.value, merchant.name, line.categoryId,
                                            flatCategories.singleOrNull { it.category.id == category?.id }?.displayName, if (line.kind == StatementLineKind.ACCOUNT_CREDIT) MovementType.INCOME else MovementType.EXPENSE,
                                            preferences, allCategories, null)?.let {
                                                categoryOptions[line.id] = it.copy(checked = line.rememberMerchantCategory)
                                            }
                                    }
                                    StatementRememberChoices(catalog.filter { it.isActive }, aliasOptions, categoryOptions)
                                }
                                compute
                            }
                            combine(store.observe(user, importId), rememberInputs) { snapshot, choices -> snapshot to choices(snapshot) }
                                .collect { (snapshot, choices) ->
                                if (hasAccess()) _state.update { current -> current.copy(loading = false, snapshot = snapshot,
                                    merchants = choices.merchants, rememberAliasOptions = choices.aliases, rememberCategoryOptions = choices.categories,
                                    message = if (snapshot == null) StatementLineFailureReason.NotFound.message() else current.message,
                                    missingCategoryIds = current.missingCategoryIds.intersect(snapshot?.lines.orEmpty()
                                        .filter { it.selected && it.categoryId == null }.map { it.id }.toSet())) }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { if (hasAccess()) _state.update { it.copy(loading = false, message = "No pudimos cargar esta importación. Vuelve a intentarlo.") } }
                    }
                    launch {
                        combine(instruments.observeAccounts(true), instruments.observeCards(true)) { accounts, cards ->
                            accounts.filter { it.userId.value == user && !it.isArchived && !it.isPlanLocked && it.type != AccountType.CREDIT_LIABILITY } to
                                cards.filter { it.userId.value == user && !it.isArchived && !it.isPlanLocked }
                        }.catch { _state.update { it.copy(message = "No pudimos cargar tus cuentas y tarjetas.") } }
                            .collect { (accounts, cards) -> if (hasAccess()) _state.update { it.copy(accounts = accounts, cards = cards.filterIsInstance<CreditCard>(),
                                debitCards = cards.filterIsInstance<DebitCard>().filter { card -> accounts.any { it.id == card.linkedAccountId } }) } }
                    }
                    launch {
                        categories(UserId(user)).catch { _state.update { it.copy(message = "No pudimos cargar tus categorías.") } }
                            .collect { items ->
                                val options = items.filter { it.category.isActive && !it.category.isPlanLocked }
                                    .flatMap { root -> listOf(root to null) + root.subcategories.map { it to root } }
                                    .filter { (item, _) -> item.category.isActive && !item.category.isPlanLocked  }
                                    .map { (item, parent) -> CategoryOption(item.category.id.value, item.displayName, item.icon,
                                        item.category.categoryType, parent?.category?.id?.value, parent?.displayName, item.color) }
                                if (hasAccess()) _state.update { it.copy(categories = options.filter { it.categoryType != CategoryType.INCOME },
                                    incomeCategories = options.filter { it.categoryType != CategoryType.EXPENSE }) }
                            }
                    }
                }
            }
        }
    }

    private fun hasAccess() = owner != null && (session.localAccess.value as? LocalAccess.Available)?.userId == owner

    fun setCard(id: String) = work { user ->
        val card: Card = (if (_state.value.source == StatementSource.DEPOSIT_ACCOUNT) _state.value.debitCards else _state.value.cards).find { it.id.value == id } ?: return@work
        if (!store.setCard(user, importId, id)) {
            if (_state.value.source == StatementSource.DEPOSIT_ACCOUNT && _state.value.snapshot?.lines?.any { it.currency != card.currency } == true) {
                message(StatementLineFailureReason.CurrencyMismatch.message())
                return@work
            }
            val fingerprint = _state.value.snapshot?.statement?.statementFingerprint
            val duplicate = fingerprint?.let { store.findByFingerprint(user, statementCardFingerprint(it, id)) }
            message(if (duplicate != null && duplicate.id != importId) "Este estado de cuenta ya fue importado para esta tarjeta."
                else StatementLineFailureReason.CardUnavailable.message())
            return@work
        }
        store.observe(user, importId).first()?.lines.orEmpty().filter { it.selected &&
            (it.currency != card.currency || it.kind == StatementLineKind.POINTS_REDEMPTION) && it.status == StatementImportLineStatus.PENDING
        }.forEach { store.setLineSelected(user, it.id, false) }
        _state.update { it.copy(message = null, failures = emptyMap()) }
    }

    fun selectLine(line: StatementImportLine, selected: Boolean) {
        if (selected && !_state.value.selectable(line)) return
        work { user ->
            if (!store.setLineSelected(user, line.id, selected)) message("Este movimiento ya no está disponible.")
            else _state.update { it.copy(failures = it.failures - line.id, missingCategoryIds = it.missingCategoryIds - line.id) }
        }
    }

    fun setAllSelected(selected: Boolean) = work { user ->
        store.setAllSelected(user, importId, selected)
        if (selected) store.observe(user, importId).first()?.lines.orEmpty().filter { it.selected && !_state.value.selectable(it) }
            .forEach { store.setLineSelected(user, it.id, false) }
        _state.update { it.copy(missingCategoryIds = emptySet(), failures = emptyMap()) }
    }

    fun editLine(id: String, edit: StatementImportLineEdit, onSaved: () -> Unit = {}) = work { user ->
        if (store.editLine(user, id, edit)) {
            _state.update { it.copy(missingCategoryIds = it.missingCategoryIds - id, failures = it.failures - id) }
            onSaved()
        } else message("No pudimos guardar los cambios. Revisa los datos e inténtalo otra vez.")
    }

    fun acceptMerchantSuggestion(lineId: String) = work { user ->
        val line = store.observe(user, importId).first()?.lines?.singleOrNull { it.id == lineId } ?: return@work
        val merchant = line.suggestedMerchantId ?: return@work
        if (!prepare.chooseMerchant(user, lineId, merchant, importId)) message("No pudimos elegir el comercio.")
    }

    fun rejectMerchantSuggestion(lineId: String) = work { user ->
        val line = store.observe(user, importId).first()?.lines?.singleOrNull { it.id == lineId } ?: return@work
        store.chooseMerchant(user, lineId, null, line.categoryId)
    }

    fun chooseMerchant(lineId: String, merchantId: String) = work { user ->
        if (!prepare.chooseMerchant(user, lineId, merchantId, importId)) message("No pudimos elegir el comercio.")
    }

    fun removeMerchant(lineId: String) = work { user ->
        prepare.chooseMerchant(user, lineId, null, importId)
    }

    fun setRememberMerchantAlias(lineId: String, remember: Boolean) = work { user ->
        store.setRememberMerchantAlias(user, lineId, remember)
    }

    fun setRememberMerchantCategory(lineId: String, remember: Boolean) = work { user ->
        store.setRememberMerchantCategory(user, lineId, remember)
    }

    fun assignCategory(id: String) = work { user ->
        if ((_state.value.categories + _state.value.incomeCategories).none { it.id == id }) return@work
        val lines = store.observe(user, importId).first()?.lines.orEmpty().filter {
            it.selected && it.categoryId == null && it.status == StatementImportLineStatus.PENDING && it.needsPurchaseCategory() &&
                (_state.value.categories + _state.value.incomeCategories).any { category -> category.id == id &&
                    (if (it.kind == StatementLineKind.ACCOUNT_CREDIT) category.categoryType != CategoryType.EXPENSE else category.categoryType != CategoryType.INCOME) }
        }
        for (line in lines) {
            if (!store.editLine(user, line.id, StatementImportLineEdit(line.description, line.amountMinor,
                    line.occurredDate, id, line.kind, line.merchantId, line.sourceAccountId))) {
                message("No pudimos asignar todas las categorías. Revisa los movimientos.")
                return@work
            }
        }
        _state.update { it.copy(missingCategoryIds = emptySet()) }
    }

    fun confirm() = work { user ->
        val current = _state.value
        if (current.snapshot?.statement?.cardId == null) { message(StatementLineFailureReason.CardRequired.message()); return@work }
        if (current.selectedCard == null) { message(StatementLineFailureReason.CardUnavailable.message()); return@work }
        if (current.selected.isEmpty()) { message("Selecciona al menos un movimiento para registrar."); return@work }
        val missing = prepare.missingCategoryLineIds(user, importId).toSet()
        if (missing.isNotEmpty()) {
            _state.update { it.copy(missingCategoryIds = missing, message = "Elige una categoría para los movimientos resaltados antes de registrar.") }
            return@work
        }
        val previous = current.snapshot.lines.count { it.status == StatementImportLineStatus.REGISTERED }
        val result = confirmer(user, importId)
        if (!hasAccess()) return@work
        _state.update { it.copy(failures = result.failed.associate { failure -> failure.lineId to failure.reason },
            learningWarnings = result.learningWarnings, missingCategoryIds = emptySet(), successCount = if (result.completed) previous + result.registered else null,
            showPremium = result.error == StatementLineFailureReason.PremiumRequired,
            message = result.error?.message() ?: if (result.failed.isNotEmpty())
                "Registraste ${result.registered} movimientos. Revisa los que necesitan atención y vuelve a intentarlo."
                else if (!result.completed) "No pudimos terminar la importación. Vuelve a intentarlo." else null) }
    }

    fun discard() = work { user ->
        if (store.discard(user, importId)) _state.update { it.copy(discarded = true) }
        else message("No pudimos descartar esta importación. Vuelve a intentarlo.")
    }
    fun dismissMessage() { _state.update { it.copy(message = null) } }
    private fun message(text: String) { if (hasAccess()) _state.update { it.copy(message = text) } }

    private fun work(block: suspend (String) -> Unit) {
        if (!hasAccess() || _state.value.busy || _state.value.loading || _state.value.successCount != null) return
        _state.update { it.copy(busy = true, message = null) }
        operation = viewModelScope.launch {
            try { block(requireNotNull(owner)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("No pudimos completar esta acción. Vuelve a intentarlo.") }
            finally { if (hasAccess()) _state.update { it.copy(busy = false) } }
        }
    }
}
