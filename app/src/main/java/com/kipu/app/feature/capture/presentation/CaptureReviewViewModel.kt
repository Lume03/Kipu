package com.kipu.app.feature.capture.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.capture.domain.CaptureAccessGate
import com.kipu.app.feature.capture.domain.CaptureCandidateRecord
import com.kipu.app.feature.capture.domain.CaptureCandidateStatus
import com.kipu.app.feature.capture.domain.CaptureCandidateStore
import com.kipu.app.feature.capture.domain.CaptureConfirmationMode
import com.kipu.app.feature.capture.domain.CaptureConfirmationResult
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.feature.capture.domain.CaptureDiscardResult
import com.kipu.app.feature.capture.domain.CaptureInstrumentKind
import com.kipu.app.feature.capture.domain.CaptureManualConversionResult
import com.kipu.app.feature.capture.domain.CaptureReason
import com.kipu.app.feature.capture.domain.ConfirmCaptureCandidate
import com.kipu.app.feature.capture.domain.ConfirmCaptureCommand
import com.kipu.app.feature.capture.domain.ConvertCandidateToManual
import com.kipu.app.feature.capture.domain.CreditMode
import com.kipu.app.feature.capture.domain.DiscardCaptureCandidate
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.capture.domain.ExpectedMatchState
import com.kipu.app.feature.capture.domain.ExpectedPayment
import com.kipu.app.feature.recurrences.domain.MatchCapturedMovementToExpected
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val CAPTURE_CANDIDATE_ID_ARG = "candidateId"

/** An account, wallet or credit card the user can pick as the instrument of a captured movement. */
data class CaptureInstrumentChoice(
    val kind: CaptureInstrumentKind,
    /** Account id for debit/wallet, credit card id for [CaptureInstrumentKind.CREDIT_CARD]. */
    val id: String,
    val label: String,
    val currency: String,
    /** Effective ledger account, distinct from the selected wallet/card [id]. */
    val accountId: String? = null,
    /** Bank, issuer or wallet name/preset used to show its logo; null shows a generic icon. */
    val brandKey: String? = null,
)

data class CaptureCategoryChoice(
    val id: String,
    val label: String,
    val type: CategoryType,
    val name: String = label,
    val icon: String = "",
    val color: String? = null,
    val parentId: String? = null,
    val parentName: String? = null,
)

data class CaptureReviewUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val candidate: CaptureCandidateRecord? = null,
    val automation: CaptureAutomationStatus = CaptureAutomationStatus.CHECKING,
    /** True after the user converted the candidate to a manual draft (access to capture not current). */
    val manualMode: Boolean = false,
    val direction: CaptureDirection = CaptureDirection.EXPENSE,
    val amountText: String = "",
    val currency: String? = null,
    val occurredAt: Long = 0L,
    val dateEdited: Boolean = false,
    val merchantId: String? = null,
    val categoryId: String? = null,
    val selectedInstrument: CaptureInstrumentChoice? = null,
    val creditMode: CreditMode = CreditMode.DIRECT,
    val installmentsText: String = "",
    val instruments: List<CaptureInstrumentChoice> = emptyList(),
    val categories: List<CaptureCategoryChoice> = emptyList(),
    val amountError: String? = null,
    val instrumentError: String? = null,
    val categoryError: String? = null,
    val installmentsError: String? = null,
    val generalError: String? = null,
    val isSaving: Boolean = false,
    val similarTransaction: Transaction? = null,
    val expectedChoices: List<Pair<ExpectedPayment, String>> = emptyList(),
    val resolvedForChoice: CaptureCandidateRecord? = null,
) {
    val isCredit: Boolean get() = selectedInstrument?.kind == CaptureInstrumentKind.CREDIT_CARD

    /** HU-48 esc. 4: installment controls exist only while the instrument is a credit card. */
    val showCreditMode: Boolean get() = isCredit

    /** HU-46 esc. 2: with several matches the instrument must be chosen explicitly. */
    val instrumentChoiceRequired: Boolean
        get() = candidate?.instrumentAmbiguous == true && selectedInstrument == null

    val reasons: List<CaptureReason> get() = candidate?.confidenceReasons.orEmpty()

    /** Whether the user can confirm: capture access is current, or the candidate was made a manual draft. */
    val canConfirm: Boolean
        get() = candidate?.status == CaptureCandidateStatus.PENDING && (manualMode || automation == CaptureAutomationStatus.ACTIVE)

    /** Offered when the capture gate no longer allows confirming from the inbox. */
    val canConvertToManual: Boolean
        get() = candidate?.status == CaptureCandidateStatus.PENDING && !manualMode &&
            (automation == CaptureAutomationStatus.PREMIUM_REQUIRED || automation == CaptureAutomationStatus.SETUP_REQUIRED)
}

sealed interface CaptureReviewEvent {
    data class Registered(val message: String) : CaptureReviewEvent
    data class Discarded(val message: String) : CaptureReviewEvent
}

@HiltViewModel
class CaptureReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val candidates: CaptureCandidateStore,
    private val gate: CaptureAccessGate,
    private val observeInstruments: ObserveInstruments,
    private val observeCategories: ObserveCategories,
    private val confirmCandidate: ConfirmCaptureCandidate,
    private val discardCandidate: DiscardCaptureCandidate,
    private val convertToManual: ConvertCandidateToManual,
    private val matchExpected: MatchCapturedMovementToExpected? = null,
    private val recurrences: RecurrenceRepository? = null,
) : ViewModel() {
    private val candidateId: String = savedStateHandle.get<String>(CAPTURE_CANDIDATE_ID_ARG).orEmpty()
    private val ownerId: String? = gate.currentOwnerId()

    private val _state = MutableStateFlow(CaptureReviewUiState())
    val state: StateFlow<CaptureReviewUiState> = _state.asStateFlow()

    private val _events = Channel<CaptureReviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var allCategories: List<CaptureCategoryChoice> = emptyList()

    init {
        if (ownerId == null || candidateId.isBlank()) {
            _state.update { it.copy(isLoading = false, notFound = true) }
        } else {
            viewModelScope.launch { loadCandidate(ownerId) }
            observeOptions(ownerId)
        }
    }

    private suspend fun loadCandidate(owner: String) {
        val candidate = try {
            candidates.get(owner, candidateId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (candidate?.expectedMatchState == ExpectedMatchState.PENDING_CHOICE &&
            candidate.status in setOf(CaptureCandidateStatus.CONFIRMED, CaptureCandidateStatus.AUTO_REGISTERED)) {
            _state.update { it.copy(isLoading = false, candidate = candidate, resolvedForChoice = candidate) }
            loadExpectedChoices(candidate)
            return
        }
        if (candidate == null || candidate.status != CaptureCandidateStatus.PENDING) {
            _state.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        val automation = try {
            gate.evaluateCurrent().toAutomationStatus()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CaptureAutomationStatus.SETUP_REQUIRED
        }
        _state.update { current ->
            val direction = candidate.direction ?: CaptureDirection.EXPENSE
            current.copy(
                isLoading = false,
                candidate = candidate,
                automation = automation,
                direction = direction,
                amountText = amountTextOf(candidate.amountMinor),
                currency = candidate.currency,
                occurredAt = candidate.occurredAt ?: candidate.createdAt,
                merchantId = candidate.merchantId,
                categoryId = candidate.categoryId,
                creditMode = candidate.creditMode ?: CreditMode.DIRECT,
                installmentsText = candidate.installmentCount?.toString().orEmpty(),
                selectedInstrument = preselectedInstrument(candidate, current.instruments),
                categories = categoriesFor(direction),
            )
        }
    }

    private fun observeOptions(owner: String) {
        viewModelScope.launch {
            combine(
                observeInstruments.observeAccounts(activeOnly = true),
                observeInstruments.observeCards(activeOnly = true),
            ) { accounts, cards ->
                val accountChoices = accounts
                    .filter { !it.isArchived && !it.isPlanLocked && it.type != AccountType.CREDIT_LIABILITY }
                    .map { account ->
                        CaptureInstrumentChoice(
                            kind = if (account.type == AccountType.DIGITAL_WALLET) {
                                CaptureInstrumentKind.WALLET
                            } else {
                                CaptureInstrumentKind.DEBIT_ACCOUNT
                            },
                            id = account.id.value,
                            label = account.alias,
                            currency = account.currency.name,
                            accountId = account.linkedAccountId?.value ?: account.id.value,
                            brandKey = account.preset?.name,
                        )
                    }
                val cardChoices = cards.filterIsInstance<CreditCard>()
                    .filterNot { it.isArchived || it.isPlanLocked }
                    .map { card ->
                        CaptureInstrumentChoice(
                            kind = CaptureInstrumentKind.CREDIT_CARD,
                            id = card.id.value,
                            label = "${card.alias?.takeIf(String::isNotBlank) ?: card.issuer} ····${card.lastFourDigits}",
                            currency = card.currency.name,
                            accountId = card.liabilityAccountId.value,
                            brandKey = card.issuer,
                        )
                    }
                accountChoices + cardChoices
            }.catch { emit(emptyList()) }.collect { choices ->
                _state.update { current ->
                    val candidate = current.candidate
                    val selected = current.selectedInstrument
                        ?.let { s -> choices.find { it.kind == s.kind && it.id == s.id } }
                        ?: candidate?.takeIf { current.selectedInstrument == null && !current.manualMode }
                            ?.let { preselectedInstrument(it, choices) }
                    current.copy(instruments = choices, selectedInstrument = selected)
                }
            }
        }
        viewModelScope.launch {
            observeCategories(UserId(owner)).catch { emit(emptyList()) }.collect { items ->
                allCategories = items.filter { it.category.isActive }
                    .flatMap { root -> listOf(root to null) + root.subcategories.map { it to root } }
                    .filter { (item, _) -> item.category.isActive && !item.category.isPlanLocked }
                    .map { (item, parent) ->
                        CaptureCategoryChoice(
                            id = item.category.id.value,
                            label = parent?.let { "${it.displayName} > ${item.displayName}" } ?: item.displayName,
                            type = item.category.categoryType,
                            name = item.displayName,
                            icon = item.icon,
                            color = item.color,
                            parentId = parent?.category?.id?.value,
                            parentName = parent?.displayName,
                        )
                    }
                _state.update { it.copy(categories = categoriesFor(it.direction)) }
            }
        }
    }

    private fun categoriesFor(direction: CaptureDirection): List<CaptureCategoryChoice> {
        val wanted = if (direction == CaptureDirection.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        return allCategories.filter { it.type == wanted || it.type == CategoryType.GENERAL }
    }

    private fun preselectedInstrument(
        candidate: CaptureCandidateRecord,
        choices: List<CaptureInstrumentChoice>,
    ): CaptureInstrumentChoice? {
        // Never guess: with several matches the user must choose (HU-46 esc. 2 / HU-48).
        if (candidate.instrumentAmbiguous) return null
        return when (candidate.instrumentKind) {
            CaptureInstrumentKind.CREDIT_CARD ->
                choices.find { it.kind == CaptureInstrumentKind.CREDIT_CARD && it.id == candidate.cardId }
            CaptureInstrumentKind.WALLET -> choices.singleOrNull {
                it.kind == CaptureInstrumentKind.WALLET && (it.accountId ?: it.id) == candidate.accountId
            }
            CaptureInstrumentKind.DEBIT_ACCOUNT ->
                choices.find { it.kind != CaptureInstrumentKind.CREDIT_CARD && it.id == candidate.accountId }
            null -> null
        }
    }

    fun onDirectionSelected(direction: CaptureDirection) = _state.update { current ->
        if (current.direction == direction) return@update current
        val categories = categoriesFor(direction)
        // A credit card only records purchases; switching to income drops it.
        val instrument = current.selectedInstrument
            ?.takeUnless { direction == CaptureDirection.INCOME && it.kind == CaptureInstrumentKind.CREDIT_CARD }
        current.copy(
            direction = direction,
            categories = categories,
            categoryId = current.categoryId?.takeIf { id -> categories.any { it.id == id } },
            selectedInstrument = instrument,
            generalError = null,
        )
    }

    fun onAmountChanged(text: String) {
        if (text.any { !(it.isDigit() || it == '.' || it == ',') }) return
        _state.update { it.copy(amountText = text, amountError = null, generalError = null) }
    }

    fun onCurrencySelected(currency: String) = _state.update { it.copy(currency = currency, amountError = null) }

    fun onDateSelected(occurredAt: Long) = _state.update { it.copy(occurredAt = occurredAt, dateEdited = true) }

    fun onMerchantChanged(id: String) = _state.update { it.copy(merchantId = id.ifBlank { null }, generalError = null) }

    fun onCategorySelected(categoryId: String?) = _state.update { it.copy(categoryId = categoryId, categoryError = null) }

    fun onInstrumentSelected(choice: CaptureInstrumentChoice) = _state.update { current ->
        val credit = choice.kind == CaptureInstrumentKind.CREDIT_CARD
        val direction = if (credit) CaptureDirection.EXPENSE else current.direction
        current.copy(
            selectedInstrument = choice,
            currency = choice.currency,
            instrumentError = null,
            installmentsError = null,
            // Switching away from credit eliminates installments (HU-48 regla 2).
            creditMode = if (credit) current.creditMode else CreditMode.DIRECT,
            installmentsText = if (credit) current.installmentsText else "",
            direction = direction,
            categories = categoriesFor(direction),
            categoryId = current.categoryId?.takeIf { id -> categoriesFor(direction).any { it.id == id } },
        )
    }

    fun onCreditModeSelected(mode: CreditMode) = _state.update {
        it.copy(
            creditMode = mode,
            installmentsError = null,
            installmentsText = if (mode == CreditMode.DIRECT) "" else it.installmentsText,
        )
    }

    fun onInstallmentsChanged(text: String) {
        if (text.length > 2 || text.any { !it.isDigit() }) return
        _state.update { it.copy(installmentsText = text, installmentsError = null) }
    }

    fun confirm() = submit(acceptSimilar = false)

    /** The user saw the similar-movement sheet and chose to save anyway. */
    fun confirmDespiteSimilar() {
        _state.update { it.copy(similarTransaction = null) }
        submit(acceptSimilar = true)
    }

    fun dismissSimilar() = _state.update { it.copy(similarTransaction = null) }

    fun dismissError() = _state.update { it.copy(generalError = null) }

    private fun submit(acceptSimilar: Boolean) {
        val owner = ownerId ?: return
        val current = _state.value
        val candidate = current.candidate ?: return
        if (current.isSaving || !current.canConfirm) return
        val command = buildCommand(owner, candidate, current, acceptSimilar) ?: return
        val mode = if (current.manualMode) CaptureConfirmationMode.MANUAL_DRAFT else CaptureConfirmationMode.INBOX
        _state.update { it.copy(isSaving = true, generalError = null) }
        viewModelScope.launch {
            val result = try {
                confirmCandidate(command, mode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CaptureConfirmationResult.Rejected(null, "No se pudo guardar. Puedes reintentar sin duplicar la operación.")
            }
            handle(result)
        }
    }

    private fun buildCommand(
        owner: String,
        candidate: CaptureCandidateRecord,
        state: CaptureReviewUiState,
        acceptSimilar: Boolean,
    ): ConfirmCaptureCommand? {
        val amount = parseCaptureAmountMinor(state.amountText)
        val instrument = state.selectedInstrument
        val installments = state.installmentsText.toIntOrNull()
        val isCredit = instrument?.kind == CaptureInstrumentKind.CREDIT_CARD
        val needsInstallments = isCredit && state.creditMode == CreditMode.INSTALLMENTS
        val amountError = when {
            amount == null -> "Ingresa un monto válido mayor a cero"
            state.currency.isNullOrBlank() -> "Elige la moneda"
            else -> null
        }
        val instrumentError = if (instrument == null) "Elige la cuenta o tarjeta" else null
        val categoryError = if (state.direction == CaptureDirection.EXPENSE && state.categoryId == null) {
            "Elige una categoría para el gasto"
        } else {
            null
        }
        val installmentsError = if (needsInstallments && installments !in ConfirmCaptureCandidate.INSTALLMENT_RANGE) {
            "Elige entre 2 y 36 cuotas"
        } else {
            null
        }
        if (amountError != null || instrumentError != null || categoryError != null || installmentsError != null) {
            _state.update {
                it.copy(
                    amountError = amountError,
                    instrumentError = instrumentError,
                    categoryError = categoryError,
                    installmentsError = installmentsError,
                )
            }
            return null
        }
        return ConfirmCaptureCommand(
            ownerId = owner,
            candidateId = candidate.id,
            direction = state.direction,
            amountMinor = requireNotNull(amount),
            currency = requireNotNull(state.currency),
            occurredAt = state.occurredAt,
            merchantId = state.merchantId,
            merchantText = candidate.merchantSourceText,
            categoryId = state.categoryId,
            instrumentKind = requireNotNull(instrument).kind,
            accountId = instrument.accountId ?: instrument.id,
            selectedAccountId = instrument.id.takeUnless { isCredit },
            cardId = if (isCredit) instrument.id else null,
            creditMode = if (isCredit) state.creditMode else null,
            installmentCount = if (needsInstallments) installments else null,
            acceptSimilar = acceptSimilar,
        )
    }

    private suspend fun handle(result: CaptureConfirmationResult) {
        when (result) {
            is CaptureConfirmationResult.Registered -> {
                val resolved = ownerId?.let { candidates.get(it, candidateId) }
                if (resolved?.expectedMatchState == ExpectedMatchState.PENDING_CHOICE) {
                    loadExpectedChoices(resolved)
                    return
                }
                _state.update { it.copy(isSaving = false) }
                _events.send(
                    CaptureReviewEvent.Registered(
                        if (result.alreadyResolved) "Esta captura ya estaba registrada." else "Movimiento registrado.",
                    ),
                )
            }
            is CaptureConfirmationResult.SimilarTransactionWarning ->
                _state.update { it.copy(isSaving = false, similarTransaction = result.existingTransaction) }
            is CaptureConfirmationResult.AccessDenied -> _state.update {
                it.copy(
                    isSaving = false,
                    automation = result.decision.toAutomationStatus(),
                    generalError = "La captura automática ya no está disponible. Puedes convertirla en un registro manual.",
                )
            }
            is CaptureConfirmationResult.Rejected -> _state.update { current ->
                when (result.field) {
                    "amount", "currency" -> current.copy(isSaving = false, amountError = result.message)
                    "category" -> current.copy(isSaving = false, categoryError = result.message)
                    "instrument", "source_account" -> current.copy(isSaving = false, instrumentError = result.message)
                    "installments" -> current.copy(isSaving = false, installmentsError = result.message)
                    else -> current.copy(isSaving = false, generalError = result.message)
                }
            }
            CaptureConfirmationResult.Discarded -> {
                _state.update { it.copy(isSaving = false) }
                _events.send(CaptureReviewEvent.Discarded("Esta captura ya fue descartada."))
            }
            CaptureConfirmationResult.NotFound -> _state.update { it.copy(isSaving = false, notFound = true) }
        }
    }

    private suspend fun loadExpectedChoices(candidate: CaptureCandidateRecord) {
        try {
            val matches = matchExpected?.options(candidate).orEmpty()
            val rules = recurrences?.observeAll(candidate.ownerId)?.first().orEmpty()
            val choices = matches.map { payment ->
                val rule = rules.firstOrNull { it.id == payment.recurrenceId }
                payment to ((rule?.templateTransaction?.get("concept") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Pago recurrente")
            }
            _state.update { it.copy(isSaving = false, candidate = candidate, expectedChoices = choices, resolvedForChoice = candidate) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _state.update { it.copy(isSaving = false, candidate = candidate, resolvedForChoice = candidate, generalError = "No se pudieron cargar los pagos esperados. Vuelve a abrir la captura.") }
        }
    }

    fun chooseExpected(id: String?) {
        val candidate = _state.value.resolvedForChoice ?: return
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, generalError = null) }
        viewModelScope.launch {
            run {
                val linked = try {
                    if (id == null) matchExpected?.chooseNone(candidate) == true
                    else matchExpected?.link(candidate, candidate.resolvedTransactionId ?: "", id) == true
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
                if (!linked) {
                    _state.update { it.copy(isSaving = false, generalError = "No se pudo asociar el pago. Inténtalo de nuevo o elige Ninguno.") }
                    return@launch
                }
            }
            _state.update { it.copy(isSaving = false, expectedChoices = emptyList(), resolvedForChoice = null) }
            _events.send(CaptureReviewEvent.Registered("Movimiento registrado."))
        }
    }

    /** Premium (or consent/permission) no longer current: keeps the candidate and unlocks the manual form. */
    fun convertToManual() {
        val owner = ownerId ?: return
        if (_state.value.isSaving) return
        viewModelScope.launch {
            val result = try {
                convertToManual(owner, candidateId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            when (result) {
                is CaptureManualConversionResult.Draft -> _state.update { current ->
                    val draft = result.draft
                    val direction = draft.direction ?: current.direction
                    current.copy(
                        manualMode = true,
                        generalError = null,
                        direction = direction,
                        categories = categoriesFor(direction),
                        amountText = amountTextOf(draft.amountMinor).ifBlank { current.amountText },
                        currency = draft.currency ?: current.currency,
                        occurredAt = draft.occurredAt ?: current.occurredAt,
                        categoryId = draft.categoryId ?: current.categoryId,
                    )
                }
                CaptureManualConversionResult.NotPending, CaptureManualConversionResult.NotFound ->
                    _state.update { it.copy(notFound = true) }
                null -> _state.update { it.copy(generalError = "No se pudo convertir. Inténtalo de nuevo.") }
            }
        }
    }

    fun discard() {
        val owner = ownerId ?: return
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val message = try {
                when (discardCandidate(owner, candidateId)) {
                    is CaptureDiscardResult.Discarded -> "Captura descartada. No se registró ningún movimiento."
                    CaptureDiscardResult.AlreadyRegistered -> "Esta captura ya se registró."
                    CaptureDiscardResult.NotFound -> "La captura ya no está disponible."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isSaving = false, generalError = "No se pudo descartar. Inténtalo de nuevo.") }
                return@launch
            }
            _state.update { it.copy(isSaving = false) }
            _events.send(CaptureReviewEvent.Discarded(message))
        }
    }

}
