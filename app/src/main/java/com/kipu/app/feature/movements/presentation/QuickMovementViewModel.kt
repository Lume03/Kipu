package com.kipu.app.feature.movements.presentation

import com.kipu.app.feature.accounts.domain.usecase.PayCreditCard
import com.kipu.app.feature.budgets.domain.BudgetCategoryLookup
import com.kipu.app.feature.budgets.domain.BudgetConsumption
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.GetBudgetConsumption
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.categories.domain.MerchantPreferenceResolution
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.usecase.CreateCategory
import com.kipu.app.feature.categories.domain.usecase.EnsureInitialCategoryCatalog
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.ResolvePreferredCategory
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

data class CategoryOption(
    val id: String,
    val name: String,
    val icon: String,
    val categoryType: CategoryType = CategoryType.GENERAL,
    val parentCategoryId: String? = null,
    val parentName: String? = null,
    val color: String? = null,
) {
    val displayName: String get() = parentName?.let { "$it > $name" } ?: name
}

data class QuickMovementUiState(
    val type: MovementType = MovementType.EXPENSE,
    val amountText: String = "",
    val currency: String = "PEN",
    val selectedSourceAccountId: String? = null,
    val selectedSourceCardId: String? = null,
    val selectedDestinationAccountId: String? = null,
    val selectedDestinationCardId: String? = null,
    val budgetImpacts: List<QuickMovementBudgetImpact> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedCategoryName: String? = null,
    val selectedCategoryIcon: String? = null,
    val merchantName: String = "",
    val selectedMerchantId: String? = null,
    val merchantProvisionalText: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val isDateManuallySelected: Boolean = false,
    val note: String = "",
    val isMoreDetailsExpanded: Boolean = false,
    val isSaving: Boolean = false,
    val availableAccounts: List<Account> = emptyList(),
    val availableCreditCards: List<CreditCard> = emptyList(),
    val balancesLoaded: Boolean = false,
    val accountBalances: Map<String, Money> = emptyMap(),
    val creditCardDebts: Map<String, Money> = emptyMap(),
    val creditCardAvailableCredits: Map<String, Money> = emptyMap(),
    val availableCategories: List<CategoryOption> = emptyList(),
    val isLoadingCategories: Boolean = true,
    val isCreatingSubcategory: Boolean = false,
    val categoryCreationError: String? = null,
    val amountError: String? = null,
    val accountError: String? = null,
    val categoryError: String? = null,
    val destinationAccountError: String? = null,
    val generalError: String? = null,
    val showDuplicateWarning: Boolean = false,
    val duplicateExistingTransaction: com.kipu.app.feature.movements.domain.model.Transaction? = null,
    val pendingCommandForDuplicate: RegisterTransactionCommand? = null,
)

data class QuickMovementBudgetImpact(
    val name: String,
    val currency: String,
    val consumption: BudgetConsumption,
    val projected: BudgetConsumption,
)

sealed interface QuickMovementUiEvent {
    data class TransactionSaved(
        val message: String,
        val movementType: MovementType = MovementType.EXPENSE,
    ) : QuickMovementUiEvent
    data class ShowMessage(val message: String) : QuickMovementUiEvent
}

@HiltViewModel
class QuickMovementViewModel @Inject constructor(
    private val registerTransactionUseCase: RegisterTransaction,
    private val confirmCreditPurchaseUseCase: ConfirmCreditPurchase,
    private val observeInstruments: ObserveInstruments,
    private val observeFinancialDashboard: ObserveFinancialDashboard,
    private val observeCategories: ObserveCategories,
    private val resolvePreferredCategory: ResolvePreferredCategory,
    private val createCategoryUseCase: CreateCategory,
    private val ensureInitialCategoryCatalog: EnsureInitialCategoryCatalog,
    private val categorySyncScheduler: CategorySyncScheduler,
    private val sessionCoordinator: SessionCoordinator,
    private val payCreditCard: PayCreditCard,
    private val budgetRepository: BudgetRepository,
    private val getBudgetConsumption: GetBudgetConsumption,
    private val budgetCategories: BudgetCategoryLookup,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickMovementUiState())
    val uiState: StateFlow<QuickMovementUiState> = _uiState.asStateFlow()

    private var allCategoryOptions: List<CategoryOption> = emptyList()
    private val rememberedMerchantCategories = mutableMapOf<String, String>()
    private var merchantPreferenceJob: Job? = null
    private var categorySelectionRevision = 0L
    private var categoryWasExplicitlySelected = false
    private var receiptDraftId: String? = null
    private var receiptConfirmation: (suspend (RegisterTransactionCommand) -> RegisterTransactionResult)? = null

    /** Reuses the financial editor; importing a draft never writes a movement or a balance. */
    fun prepareReceiptDraft(
        candidateId: String,
        amountMinor: Long?,
        occurredAt: Long?,
        recipient: String?,
        movementType: MovementType = MovementType.EXPENSE,
        onConfirm: suspend (RegisterTransactionCommand) -> RegisterTransactionResult,
    ) {
        receiptConfirmation = onConfirm
        if (receiptDraftId == candidateId) return
        resetForm()
        receiptDraftId = candidateId
        receiptConfirmation = onConfirm
        onTypeSelected(movementType)
        _uiState.update { current ->
            val penAccounts = current.availableAccounts.filter { it.currency == Currency.PEN }
            current.copy(
                amountText = amountMinor?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty(),
                occurredAt = occurredAt ?: current.occurredAt,
                isDateManuallySelected = occurredAt != null,
                merchantName = recipient.orEmpty(),
                merchantProvisionalText = recipient?.takeIf(String::isNotBlank),
                selectedSourceAccountId = (penAccounts.firstOrNull { it.preset?.name == "YAPE" }
                    ?: penAccounts.firstOrNull())?.id?.value,
                selectedSourceCardId = null,
                currency = "PEN",
                isMoreDetailsExpanded = true,
            )
        }
    }

    private val _events = Channel<QuickMovementUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val ownerId = getUserId()
        if (ownerId != null) {
            viewModelScope.launch {
                combine(
                    budgetRepository.observeBudgets(ownerId).catch { emit(emptyList()) },
                    _uiState.map { listOf(it.type, it.currency, it.selectedCategoryId, it.amountText, it.occurredAt) }
                        .distinctUntilChanged(),
                ) { budgets, _ -> budgets }.collectLatest { budgets ->
                    val state = _uiState.value
                    val amount = parseAmountMinor(state.amountText)
                    _uiState.update { it.copy(budgetImpacts = emptyList()) }
                    if (state.type != MovementType.EXPENSE || amount == null || amount <= 0L) return@collectLatest
                    val zone = ZoneId.systemDefault()
                    val date = Instant.ofEpochMilli(state.occurredAt).atZone(zone).toLocalDate()
                    val impacts = try {
                        budgets.filter { it.isActive && it.currency == state.currency }.mapNotNull { budget ->
                            val result = getBudgetConsumption.current(ownerId, budget.id, date, zone)
                            val value = (result as? BudgetOutcome.Success)?.value ?: return@mapNotNull null
                            if (date !in value.period.interval) return@mapNotNull null
                            val scope = value.period.scope
                            if (scope is BudgetScope.Category && state.selectedCategoryId !in
                                budgetCategories.idsIncludingDescendants(ownerId, scope.categoryId)) return@mapNotNull null
                            QuickMovementBudgetImpact(budget.name, budget.currency, value.consumption,
                                BudgetConsumption(value.period.limitMinor, Math.addExact(value.consumption.consumedMinor, amount)))
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                    _uiState.update { it.copy(budgetImpacts = impacts) }
                }
            }

            viewModelScope.launch {
                ensureInitialCategoryCatalog(UserId(ownerId))
                categorySyncScheduler.scheduleSync(ownerId)
                observeCategories(UserId(ownerId))
                    .catch {
                        _uiState.update { current ->
                            current.copy(
                                isLoadingCategories = false,
                                generalError = "No se pudieron cargar las categorías"
                            )
                        }
                    }
                    .collect { items ->
                    allCategoryOptions = items.filter { it.category.isActive }
                        .flatMap { root -> listOf(root to null) + root.subcategories.map { it to root } }
                        .filter { (item, _) -> item.category.isActive && !item.category.isPlanLocked }
                        .map { (item, parent) ->
                            CategoryOption(
                                id = item.category.id.value,
                                name = item.displayName,
                                icon = item.icon,
                                categoryType = item.category.categoryType,
                                parentCategoryId = parent?.category?.id?.value,
                                parentName = parent?.displayName,
                                color = item.color,
                            )
                        }
                    _uiState.update { current ->
                        val options = categoriesFor(current.type)
                        val selection = options.find { it.id == current.selectedCategoryId }
                        current.copy(
                            isLoadingCategories = false,
                            availableCategories = options,
                            selectedCategoryId = selection?.id,
                            selectedCategoryName = selection?.displayName,
                            selectedCategoryIcon = selection?.icon,
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isLoadingCategories = false) }
        }
        viewModelScope.launch {
            combine(
                observeInstruments.observeAccounts(activeOnly = true),
                observeInstruments.observeCards(activeOnly = true),
            ) { accounts, cards ->
                accounts to cards.filterIsInstance<CreditCard>().filterNot { it.isArchived || it.isPlanLocked }
            }.collect { (accounts, creditCards) ->
                _uiState.update { current ->
                    val selectedCard = creditCards.find { it.id.value == current.selectedSourceCardId }
                    val selectedAccount = if (selectedCard == null) {
                        accounts.find { it.id.value == current.selectedSourceAccountId } ?: accounts.firstOrNull()
                    } else null
                    current.copy(
                        availableAccounts = accounts,
                        availableCreditCards = creditCards,
                        selectedSourceAccountId = selectedAccount?.id?.value,
                        selectedSourceCardId = selectedCard?.id?.value,
                        currency = (selectedCard?.currency ?: selectedAccount?.currency)?.name ?: current.currency,
                        selectedDestinationAccountId = current.selectedDestinationAccountId
                            ?.takeIf { destination -> accounts.any { it.id.value == destination } },
                    )
                }
            }
        }
        viewModelScope.launch {
            observeFinancialDashboard().collect { dashboard ->
                _uiState.update { current ->
                    current.copy(
                        balancesLoaded = true,
                        accountBalances = dashboard.liquidAccounts.associate { item ->
                            item.account.id.value to item.balance
                        },
                        creditCardDebts = dashboard.creditCards.associate { item ->
                            item.card.id.value to item.debt
                        },
                        creditCardAvailableCredits = dashboard.creditCards.associate { item ->
                            item.card.id.value to item.availableCredit
                        },
                    )
                }
            }
        }
    }

    fun onTypeSelected(type: MovementType) {
        val typeChanged = _uiState.value.type != type
        if (typeChanged) {
            categorySelectionRevision++
            categoryWasExplicitlySelected = false
            merchantPreferenceJob?.cancel()
            merchantPreferenceJob = null
        }
        _uiState.update { current ->
            val options = categoriesFor(type)
            val selection = if (current.type == type) {
                options.find { it.id == current.selectedCategoryId }
            } else {
                null
            }
            current.copy(
                type = type,
                availableCategories = options,
                selectedCategoryId = selection?.id,
                selectedCategoryName = selection?.displayName,
                selectedCategoryIcon = selection?.icon,
                merchantName = if (type == MovementType.EXPENSE) current.merchantName else "",
                selectedMerchantId = if (type == MovementType.EXPENSE) current.selectedMerchantId else null,
                merchantProvisionalText = if (type == MovementType.EXPENSE) current.merchantProvisionalText else null,
                amountError = null,
                accountError = null,
                categoryError = null,
                destinationAccountError = null,
                selectedDestinationCardId = if (type == MovementType.TRANSFER) current.selectedDestinationCardId else null,
                selectedSourceCardId = if (type == MovementType.EXPENSE) current.selectedSourceCardId else null,
                selectedSourceAccountId = if (type == MovementType.EXPENSE && current.selectedSourceCardId != null) {
                    null
                } else {
                    current.selectedSourceAccountId ?: current.availableAccounts.firstOrNull()?.id?.value
                },
                currency = if (type == MovementType.EXPENSE && current.selectedSourceCardId != null) {
                    current.availableCreditCards.find { it.id.value == current.selectedSourceCardId }?.currency?.name ?: current.currency
                } else if (type != MovementType.EXPENSE) {
                    current.availableAccounts.find { it.id.value == current.selectedSourceAccountId }?.currency?.name
                        ?: current.availableAccounts.firstOrNull()?.currency?.name
                        ?: current.currency
                } else current.currency,
            )
        }
    }

    fun onAmountChanged(amount: String) {
        val sanitized = amount.filter { it.isDigit() || it == '.' || it == ',' }
            .replace(',', '.')
        _uiState.update { it.copy(amountText = sanitized, amountError = null) }
    }

    fun onSourceAccountSelected(accountId: String) {
        _uiState.update { current ->
            val account = current.availableAccounts.find { it.id.value == accountId } ?: return@update current
            current.copy(
                selectedSourceAccountId = accountId,
                selectedSourceCardId = null,
                currency = account.currency.name,
                selectedDestinationAccountId = current.selectedDestinationAccountId
                    ?.takeIf { destination ->
                        current.availableAccounts.any { it.id.value == destination && it.currency == account.currency }
                    },
                accountError = null,
                destinationAccountError = null,
                selectedDestinationCardId = null,
            )
        }
    }

    fun onSourceCardSelected(cardId: String) {
        _uiState.update { current ->
            if (current.type != MovementType.EXPENSE) return@update current
            val card = current.availableCreditCards.find { it.id.value == cardId } ?: return@update current
            current.copy(
                selectedSourceAccountId = null,
                selectedSourceCardId = card.id.value,
                currency = card.currency.name,
                selectedDestinationAccountId = null,
                accountError = null,
                destinationAccountError = null,
                selectedDestinationCardId = null,
            )
        }
    }

    fun onDestinationAccountSelected(accountId: String) {
        _uiState.update { it.copy(selectedDestinationAccountId = accountId, selectedDestinationCardId = null, destinationAccountError = null) }
    }

    fun onCategorySelected(category: CategoryOption) {
        if (category !in _uiState.value.availableCategories || _uiState.value.type == MovementType.TRANSFER) return
        categorySelectionRevision++
        categoryWasExplicitlySelected = true
        merchantPreferenceJob?.cancel()
        merchantPreferenceJob = null
        _uiState.update {
            it.copy(
                selectedCategoryId = category.id,
                selectedCategoryName = category.displayName,
                selectedCategoryIcon = category.icon,
                categoryError = null,
            )
        }
    }

    suspend fun createCategoryOrSubcategory(
        parentCategoryId: String?,
        name: String,
        icon: String,
        color: String,
        rememberFrequentMerchant: Boolean,
    ): Result<CategoryOption> {
        val current = _uiState.value
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Escribe un nombre para la categoría."))
        }
        val ownerId = getUserId()?.let { UserId(it) }
            ?: return Result.failure(IllegalStateException("No se pudo identificar el perfil local."))

        val parent = if (!parentCategoryId.isNullOrBlank()) {
            current.availableCategories.firstOrNull {
                it.id == parentCategoryId && it.parentCategoryId == null
            } ?: return Result.failure(IllegalArgumentException("Selecciona una categoría principal válida."))
        } else null

        val categoryType = parent?.categoryType ?: when (current.type) {
            MovementType.INCOME -> CategoryType.INCOME
            else -> CategoryType.EXPENSE
        }

        _uiState.update { it.copy(isCreatingSubcategory = true, categoryCreationError = null) }
        val result = createCategoryUseCase(
            ownerId = ownerId,
            name = trimmedName,
            icon = icon,
            color = color,
            parentId = parent?.let { CategoryId(it.id) },
            categoryType = categoryType,
        )
        return result.fold(
            onSuccess = { created ->
                val option = CategoryOption(
                    id = created.id.value,
                    name = trimmedName,
                    icon = icon,
                    categoryType = created.categoryType,
                    parentCategoryId = parent?.id,
                    parentName = parent?.name,
                    color = color,
                )
                allCategoryOptions = (allCategoryOptions + option).distinctBy { it.id }
                if (rememberFrequentMerchant) {
                    current.merchantName.trim().takeIf(String::isNotBlank)?.let { merchant ->
                        rememberedMerchantCategories[normalizeMerchantName(merchant)] = option.id
                    }
                }
                categorySelectionRevision++
                categoryWasExplicitlySelected = true
                merchantPreferenceJob?.cancel()
                merchantPreferenceJob = null
                categorySyncScheduler.scheduleSync(ownerId.value)
                _uiState.update { state ->
                    val options = categoriesFor(state.type)
                    state.copy(
                        availableCategories = options,
                        selectedCategoryId = option.id,
                        selectedCategoryName = option.displayName,
                        selectedCategoryIcon = option.icon,
                        isCreatingSubcategory = false,
                        categoryCreationError = null,
                    )
                }
                Result.success(option)
            },
            onFailure = { failure ->
                _uiState.update {
                    it.copy(
                        isCreatingSubcategory = false,
                        categoryCreationError = failure.message ?: "No se pudo crear la categoría.",
                    )
                }
                Result.failure(failure)
            },
        )
    }

    suspend fun createSubcategory(
        parentCategoryId: String,
        name: String,
        icon: String,
        color: String,
        rememberFrequentMerchant: Boolean,
    ): Result<CategoryOption> = createCategoryOrSubcategory(
        parentCategoryId = parentCategoryId,
        name = name,
        icon = icon,
        color = color,
        rememberFrequentMerchant = rememberFrequentMerchant,
    )

    fun onMerchantSelected(merchant: MerchantCatalogEntry) {
        merchantPreferenceJob?.cancel()
        merchantPreferenceJob = null
        val before = _uiState.value
        val shouldResolveSuggestion = !categoryWasExplicitlySelected && before.type != MovementType.TRANSFER
        val generalSuggestion = if (shouldResolveSuggestion) {
            merchant.defaultCategoryId?.value?.let { id -> before.availableCategories.firstOrNull { it.id == id } }
                ?: rememberedCategoryFor(merchant.name, before.availableCategories)
        } else null
        val requestRevision = categorySelectionRevision
        _uiState.update { current ->
            val selected = if (categoryWasExplicitlySelected) {
                current.availableCategories.find { it.id == current.selectedCategoryId }
            } else generalSuggestion
            current.copy(
                merchantName = merchant.name,
                selectedMerchantId = merchant.id.value,
                merchantProvisionalText = null,
                selectedCategoryId = selected?.id,
                selectedCategoryName = selected?.displayName,
                selectedCategoryIcon = selected?.icon,
                categoryError = null,
            )
        }
        val ownerId = getUserId()?.let(::UserId) ?: return
        if (!shouldResolveSuggestion) return

        merchantPreferenceJob = viewModelScope.launch {
            val resolution = try {
                resolvePreferredCategory(
                    ownerId = ownerId,
                    merchantId = merchant.id,
                    movementType = before.type,
                    generalSuggestion = generalSuggestion?.let { CategoryId(it.id) },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!categoryWasExplicitlySelected && categorySelectionRevision == requestRevision) {
                    _uiState.update { current ->
                        if (current.selectedMerchantId == merchant.id.value) {
                            current.copy(
                                selectedCategoryId = null,
                                selectedCategoryName = null,
                                selectedCategoryIcon = null,
                                categoryError = "No se pudo validar la preferencia del comercio.",
                            )
                        } else current
                    }
                }
                return@launch
            }
            val resolvedCategoryId = when (resolution) {
                is MerchantPreferenceResolution.Preferred -> resolution.categoryId.value
                is MerchantPreferenceResolution.Suggested -> resolution.categoryId.value
                MerchantPreferenceResolution.NeedsNewChoice,
                MerchantPreferenceResolution.Unclassified -> null
            }
            val latest = _uiState.value
            if (categoryWasExplicitlySelected || categorySelectionRevision != requestRevision ||
                latest.selectedMerchantId != merchant.id.value
            ) return@launch

            val resolved = resolvedCategoryId?.let { id -> latest.availableCategories.firstOrNull { it.id == id } }
            _uiState.update { current ->
                if (current.selectedMerchantId != merchant.id.value || categorySelectionRevision != requestRevision) {
                    current
                } else {
                    current.copy(
                        selectedCategoryId = resolved?.id,
                        selectedCategoryName = resolved?.displayName,
                        selectedCategoryIcon = resolved?.icon,
                    )
                }
            }
        }
    }

    fun onMerchantProvisionalText(text: String) {
        val name = text.trim()
        merchantPreferenceJob?.cancel()
        merchantPreferenceJob = null
        _uiState.update {
            val remembered = if (categoryWasExplicitlySelected) null else rememberedCategoryFor(name, it.availableCategories)
            val selected = if (categoryWasExplicitlySelected) {
                it.availableCategories.find { option -> option.id == it.selectedCategoryId }
            } else remembered
            it.copy(
                merchantName = name,
                selectedMerchantId = null,
                merchantProvisionalText = name.takeIf(String::isNotBlank),
                selectedCategoryId = selected?.id,
                selectedCategoryName = selected?.displayName,
                selectedCategoryIcon = selected?.icon,
                categoryError = null,
            )
        }
    }

    fun onMerchantCleared() {
        _uiState.update { it.copy(merchantName = "", selectedMerchantId = null, merchantProvisionalText = null) }
    }

    fun refreshAutomaticTime(now: Long = System.currentTimeMillis()) {
        _uiState.update { current ->
            if (current.isDateManuallySelected || current.isSaving) current
            else current.copy(occurredAt = now)
        }
    }

    fun onOccurredAtChanged(occurredAt: Long) {
        _uiState.update { it.copy(occurredAt = occurredAt, isDateManuallySelected = true) }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun onToggleMoreDetails() {
        _uiState.update { it.copy(isMoreDetailsExpanded = !it.isMoreDetailsExpanded) }
    }

    fun onDestinationCardSelected(cardId: String) {
        _uiState.update { current ->
            val card = current.availableCreditCards.find { it.id.value == cardId && it.currency.name == current.currency }
                ?: return@update current
            current.copy(selectedDestinationCardId = card.id.value, selectedDestinationAccountId = null,
                destinationAccountError = null, generalError = null)
        }
    }

    fun onSave() {
        if (_uiState.value.isSaving) return
        if (merchantPreferenceJob?.isActive == true) {
            _uiState.update { it.copy(isSaving = true, generalError = null) }
            viewModelScope.launch {
                awaitMerchantPreferenceResolution()
                _uiState.update { it.copy(isSaving = false) }
                saveCurrentMovement()
            }
            return
        }
        saveCurrentMovement()
    }

    private suspend fun awaitMerchantPreferenceResolution() {
        while (true) {
            val pending = merchantPreferenceJob ?: return
            pending.join()
            if (merchantPreferenceJob === pending) return
        }
    }

    private fun saveCurrentMovement() {
        refreshAutomaticTime()
        val state = _uiState.value
        if (state.isSaving) return
        if (receiptDraftId != null && (state.selectedSourceCardId != null ||
                state.type == MovementType.TRANSFER || state.currency != "PEN")) {
            _uiState.update { it.copy(generalError = "El comprobante Yape registra un ingreso o gasto en soles en una cuenta, no una compra a crédito.") }
            return
        }
        val amountMinor = parseAmountMinor(state.amountText)
        if (amountMinor == null || amountMinor <= 0) {
            _uiState.update { it.copy(amountError = "Ingresa un monto válido mayor a 0") }
            return
        }

        if (state.selectedSourceAccountId == null && state.selectedSourceCardId == null) {
            _uiState.update { it.copy(accountError = "Selecciona una cuenta o tarjeta de crédito") }
            return
        }
        if (state.selectedSourceCardId != null && state.type != MovementType.EXPENSE) {
            _uiState.update { it.copy(accountError = "Las tarjetas de crédito solo pueden registrar gastos") }
            return
        }

        if (state.type == MovementType.EXPENSE && state.selectedCategoryId == null) {
            _uiState.update { it.copy(categoryError = "Selecciona una categoría") }
            return
        }

        if (state.type == MovementType.TRANSFER && state.selectedDestinationCardId == null) {
            if (state.selectedDestinationAccountId == null) {
                _uiState.update { it.copy(destinationAccountError = "Selecciona la cuenta de destino") }
                return
            }
            if (state.selectedSourceAccountId == state.selectedDestinationAccountId) {
                _uiState.update { it.copy(destinationAccountError = "La cuenta de origen y destino deben ser distintas") }
                return
            }
            if (state.availableAccounts.find { it.id.value == state.selectedDestinationAccountId }?.currency?.name != state.currency) {
                _uiState.update { it.copy(destinationAccountError = "Las cuentas deben usar la misma moneda") }
                return
            }
        }

        val userId = getUserId() ?: run {
            _uiState.update { it.copy(generalError = "Inicia sesión para registrar un movimiento") }
            return
        }
        _uiState.update { it.copy(isSaving = true, generalError = null) }
        if (state.selectedSourceCardId != null) {
            executeCreditPurchase(state, amountMinor)
            return
        }

        if (state.type == MovementType.TRANSFER && state.selectedDestinationCardId != null) {
            val source = state.availableAccounts.firstOrNull { it.id.value == state.selectedSourceAccountId }
            if (source == null || source.type !in setOf(
                    com.kipu.app.feature.accounts.domain.model.AccountType.BANK,
                    com.kipu.app.feature.accounts.domain.model.AccountType.SAVINGS)) {
                _uiState.update { it.copy(isSaving = false, accountError = "El pago de tarjeta requiere una cuenta bancaria o de ahorros") }
                return
            }
            viewModelScope.launch {
                val result = try {
                    payCreditCard(CardId(state.selectedDestinationCardId), source.id,
                        Money(amountMinor, Currency.valueOf(state.currency)), Instant.ofEpochMilli(state.occurredAt))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
                result.fold(onSuccess = {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(QuickMovementUiEvent.TransactionSaved(
                        "Pago de tarjeta guardado · Pendiente de sincronización", MovementType.TRANSFER))
                    resetForm()
                }, onFailure = { error ->
                    _uiState.update { it.copy(isSaving = false, generalError = when (error.message) {
                        "Insufficient funds in source account" -> "Saldo insuficiente en la cuenta de origen"
                        "Payment amount exceeds current card debt" -> "El pago supera la deuda actual de la tarjeta"
                        else -> "No se pudo guardar el pago de tarjeta. Revisa la cuenta y el monto e intenta de nuevo."
                    }) }
                })
            }
            return
        }

        val selectedWallet = state.availableAccounts.firstOrNull { it.id.value == state.selectedSourceAccountId }
            ?.takeIf { it.linkedAccountId != null }
        val command = RegisterTransactionCommand(
            idempotencyKey = receiptDraftId?.let { "yape-candidate:$it" } ?: UUID.randomUUID().toString(),
            userId = userId,
            type = state.type,
            amountMinor = amountMinor,
            currency = state.currency,
            sourceAccountId = selectedWallet?.linkedAccountId?.value ?: state.selectedSourceAccountId,
            destinationAccountId = if (state.type == MovementType.TRANSFER) state.selectedDestinationAccountId else null,
            categoryId = state.selectedCategoryId.takeUnless { state.type == MovementType.TRANSFER },
            merchantId = state.selectedMerchantId,
            merchantProvisionalText = state.merchantProvisionalText,
            occurredAt = state.occurredAt,
            note = state.note.takeIf { it.isNotBlank() },
            ignoreSimilarityWarning = false,
            paymentChannel = if (receiptDraftId != null) "YAPE" else selectedWallet?.preset?.name,
        )

        executeRegister(command)
    }

    private fun executeCreditPurchase(state: QuickMovementUiState, amountMinor: Long) {
        val cardId = state.selectedSourceCardId?.let(::CardId) ?: return
        val merchant = state.merchantProvisionalText
            ?: state.merchantName.takeIf(String::isNotBlank)
            ?: "Compra con tarjeta"
        val currency = runCatching { Currency.valueOf(state.currency) }.getOrDefault(Currency.PEN)
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, generalError = null) }
            confirmCreditPurchaseUseCase(
                cardId = cardId,
                amount = Money(amountMinor, currency),
                merchant = merchant,
                effectiveAt = Instant.ofEpochMilli(state.occurredAt),
                installments = 1,
                categoryId = requireNotNull(state.selectedCategoryId),
                merchantId = state.selectedMerchantId,
                note = state.note.takeIf(String::isNotBlank),
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(QuickMovementUiEvent.TransactionSaved("Compra con tarjeta registrada y pendiente de sincronización"))
                    resetForm()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSaving = false, generalError = error.message ?: "No se pudo registrar la compra") }
                },
            )
        }
    }

    fun onConfirmDuplicate() {
        val pending = _uiState.value.pendingCommandForDuplicate ?: return
        if (_uiState.value.isSaving) return
        // Generate new idempotencyKey to treat as separate legitimate purchase
        val newCommand = pending.copy(
            idempotencyKey = if (receiptDraftId != null) pending.idempotencyKey else UUID.randomUUID().toString(),
            ignoreSimilarityWarning = true,
        )
        _uiState.update { it.copy(showDuplicateWarning = false, pendingCommandForDuplicate = null, duplicateExistingTransaction = null, isSaving = true) }
        executeRegister(newCommand)
    }

    fun onDismissDuplicate() {
        _uiState.update { it.copy(showDuplicateWarning = false, pendingCommandForDuplicate = null, duplicateExistingTransaction = null) }
    }

    private fun executeRegister(command: RegisterTransactionCommand) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, generalError = null) }
            val result = try {
                receiptConfirmation?.invoke(command) ?: registerTransactionUseCase(command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RegisterTransactionResult.Failure("No se pudo guardar. Puedes reintentar sin duplicar la operación.")
            }
            when (result) {
                is RegisterTransactionResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    val message = when (result.transaction.syncStatus) {
                        MovementSyncStatus.SYNCED, MovementSyncStatus.MIGRATED_LOCAL ->
                            "Movimiento guardado y sincronizado"
                        MovementSyncStatus.CONFLICT, MovementSyncStatus.FAILED_PERMANENT ->
                            "Guardado en este dispositivo · Revisión de sincronización pendiente"
                        MovementSyncStatus.PENDING, MovementSyncStatus.IN_FLIGHT ->
                            "Guardado en este dispositivo · Pendiente de sincronización"
                    }
                    _events.send(QuickMovementUiEvent.TransactionSaved(message, command.type))
                    resetForm()
                }
                is RegisterTransactionResult.SimilarTransactionWarning -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showDuplicateWarning = true,
                            pendingCommandForDuplicate = command,
                            duplicateExistingTransaction = result.existingTransaction,
                        )
                    }
                }
                is RegisterTransactionResult.Conflict -> {
                    _uiState.update { it.copy(isSaving = false, generalError = result.message) }
                }
                is RegisterTransactionResult.ValidationError -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            generalError = result.message,
                            amountError = if (result.field == "amount") result.message else it.amountError,
                            categoryError = if (result.field == "category") result.message else it.categoryError,
                        )
                    }
                }
                is RegisterTransactionResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false, generalError = result.message) }
                }
            }
        }
    }

    fun resetForm() {
        receiptDraftId = null
        receiptConfirmation = null
        categorySelectionRevision++
        categoryWasExplicitlySelected = false
        merchantPreferenceJob?.cancel()
        merchantPreferenceJob = null
        _uiState.update { current ->
            QuickMovementUiState(
                type = MovementType.EXPENSE,
                availableAccounts = current.availableAccounts,
                availableCreditCards = current.availableCreditCards,
                balancesLoaded = current.balancesLoaded,
                accountBalances = current.accountBalances,
                creditCardDebts = current.creditCardDebts,
                creditCardAvailableCredits = current.creditCardAvailableCredits,
                selectedSourceAccountId = current.availableAccounts.firstOrNull()?.id?.value,
                currency = current.availableAccounts.firstOrNull()?.currency?.name ?: current.currency,
                availableCategories = categoriesFor(MovementType.EXPENSE),
            )
        }
    }

    private fun parseAmountMinor(amountText: String): Long? {
        return MoneyInputParser.parseMinorUnits(amountText)
    }

    private fun categoriesFor(type: MovementType): List<CategoryOption> {
        val expectedType = when (type) {
            MovementType.EXPENSE -> CategoryType.EXPENSE
            MovementType.INCOME -> CategoryType.INCOME
            MovementType.TRANSFER -> return emptyList()
        }
        return allCategoryOptions.filter {
            it.categoryType == expectedType || it.categoryType == CategoryType.GENERAL
        }
    }

    private fun rememberedCategoryFor(merchantName: String, categories: List<CategoryOption>): CategoryOption? {
        val normalizedName = normalizeMerchantName(merchantName)
        if (normalizedName.isBlank()) return null
        val categoryId = rememberedMerchantCategories[normalizedName] ?: return null
        return categories.firstOrNull { it.id == categoryId }
    }

    private fun normalizeMerchantName(name: String): String = name.trim().lowercase(Locale.ROOT)

    private fun getUserId(): String? {
        return sessionCoordinator.currentOwner?.verifiedUserId
            ?: (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId
    }
}
