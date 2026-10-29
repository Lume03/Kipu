package com.kipu.app.feature.capture.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import com.kipu.app.core.database.DatabaseTransactionRunner

/** Fields the user may correct before confirming; stored as codes in `user_corrected_fields`. */
enum class CaptureCorrectedField { AMOUNT, OCCURRED_AT, MERCHANT, CATEGORY, INSTRUMENT, DIRECTION, CREDIT_MODE }

/** Who resolves the candidate. Only [AUTOMATIC] produces AUTO_REGISTERED. */
enum class CaptureConfirmationMode {
    /** User confirms from the inbox: requires the full capture gate (Premium vigente). */
    INBOX,

    /** User confirms the prefilled manual draft (Premium expired): requires only the session owner. */
    MANUAL_DRAFT,

    /** HU-47 auto-registration: gate revalidated before saving, never credit, never ignores similarity. */
    AUTOMATIC,
}

/** Final values to register. Build with [fromCandidate] and apply the user's edits with copy(). */
data class ConfirmCaptureCommand(
    val ownerId: String,
    val candidateId: String,
    val direction: CaptureDirection,
    val amountMinor: Long,
    val currency: String,
    val occurredAt: Long,
    val merchantId: String?,
    val merchantText: String?,
    val categoryId: String?,
    val instrumentKind: CaptureInstrumentKind,
    val accountId: String?,
    val cardId: String?,
    val creditMode: CreditMode? = null,
    val installmentCount: Int? = null,
    val note: String? = null,
    /** Set only after the user saw and accepted a [CaptureConfirmationResult.SimilarTransactionWarning]. */
    val acceptSimilar: Boolean = false,
    /** Explicit UI selection; [accountId] is always the effective ledger account. */
    val selectedAccountId: String? = null,
) {
    companion object {
        /** Null when the candidate lacks a value the ledger requires (amount, currency, direction, instrument). */
        fun fromCandidate(candidate: CaptureCandidateRecord): ConfirmCaptureCommand? {
            return ConfirmCaptureCommand(
                ownerId = candidate.ownerId,
                candidateId = candidate.id,
                direction = candidate.direction ?: return null,
                amountMinor = candidate.amountMinor ?: return null,
                currency = candidate.currency ?: return null,
                occurredAt = candidate.occurredAt ?: candidate.createdAt,
                merchantId = candidate.merchantId,
                merchantText = candidate.merchantSourceText,
                categoryId = candidate.categoryId,
                instrumentKind = candidate.instrumentKind ?: return null,
                accountId = candidate.accountId,
                cardId = candidate.cardId,
                creditMode = candidate.creditMode,
                installmentCount = candidate.installmentCount,
            )
        }
    }
}

sealed interface CaptureConfirmationResult {
    /** [alreadyResolved] is true when a previous confirmation registered it (HU-51 esc. 3). */
    data class Registered(val transactionId: String, val alreadyResolved: Boolean) : CaptureConfirmationResult

    /** Returned to the caller; the UI decides. The candidate stays PENDING. */
    data class SimilarTransactionWarning(val existingTransaction: Transaction) : CaptureConfirmationResult

    /** The capture gate denied the operation; the candidate stays PENDING (use ConvertCandidateToManual). */
    data class AccessDenied(val decision: CaptureAccessDecision) : CaptureConfirmationResult

    data class Rejected(val field: String?, val message: String) : CaptureConfirmationResult
    data object Discarded : CaptureConfirmationResult
    data object NotFound : CaptureConfirmationResult
}

/** Runs after a confirmation actually resolved a candidate (learning HU-50, expected payments HU-34). */
interface CaptureCandidateResolvedListener {
    val followupFlag: Int get() = CaptureFollowup.LEARN
    suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String)
}

/** Payment channel for a wallet movement, or null when the account has no affiliated wallet for [provider]. */
fun interface CaptureWalletChannelResolver {
    suspend fun channelFor(ownerId: String, accountId: String, provider: CaptureProvider): String?
    suspend fun resolve(ownerId: String, selectedId: String, currency: String, provider: CaptureProvider): CaptureWalletPayment? =
        CaptureWalletPayment(selectedId, channelFor(ownerId, selectedId, provider))
}

data class CaptureWalletPayment(val accountId: String, val channel: String?)

object CaptureResolutionKey {
    /** Deterministic idempotency key shared by inbox, manual-draft and automatic confirmation. */
    fun of(ownerId: String, candidateId: String): String =
        UUID.nameUUIDFromBytes("$ownerId|CAPTURE|candidate|$candidateId".toByteArray(StandardCharsets.UTF_8)).toString()

    /**
     * Ledger id of a credit purchase registered with [key] as its operation id. Mirrors the stable id
     * derived by OfflineFirstFinancialInstrumentsRepository.confirmCreditPurchase.
     */
    fun creditTransactionId(ownerId: String, key: String): String =
        UUID.nameUUIDFromBytes("kipu:$ownerId:$key:purchase".toByteArray(Charsets.UTF_8)).toString()
}

/**
 * The only door from a capture candidate to the ledger (HU-47/48/51). Debit, income and wallet movements
 * go through [RegisterTransaction]; credit card purchases through the same [ConfirmCreditPurchase] used by
 * the manual flow. The candidate is resolved only after the ledger succeeded.
 */
class ConfirmCaptureCandidate @Inject constructor(
    private val candidates: CaptureCandidateStore,
    private val gate: CaptureAccessGate,
    private val registerTransaction: RegisterTransaction,
    private val confirmCreditPurchase: ConfirmCreditPurchase,
    private val walletChannels: CaptureWalletChannelResolver,
    private val clock: Clock,
    private val resolvedListeners: Set<@JvmSuppressWildcards CaptureCandidateResolvedListener>,
    private val transactions: DatabaseTransactionRunner,
    private val receipts: CaptureRegistrationReceipts,
    private val followups: CaptureResolutionFollowups,
) {
    suspend operator fun invoke(
        command: ConfirmCaptureCommand,
        mode: CaptureConfirmationMode = CaptureConfirmationMode.INBOX,
    ): CaptureConfirmationResult {
        val result = try {
            transactions { confirmAtomic(command, mode) }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
            return CaptureConfirmationResult.Rejected(null, "No se pudo guardar. Puedes reintentar sin duplicar la operación.")
        }
        // Outside the ledger transaction. The persisted bits survive a failure or process death here.
        if (result is CaptureConfirmationResult.Registered) {
            try { followups.process(command.ownerId, command.candidateId) { gate.currentOwnerId() == command.ownerId } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Still durable, retried by the worker. */ }
        }
        return result
    }

    private suspend fun confirmAtomic(command: ConfirmCaptureCommand, mode: CaptureConfirmationMode): CaptureConfirmationResult {
        if (gate.currentOwnerId() != command.ownerId) return CaptureConfirmationResult.AccessDenied(CaptureAccessDecision.NoSession)
        val candidate = candidates.get(command.ownerId, command.candidateId) ?: return CaptureConfirmationResult.NotFound
        settled(candidate)?.let { return it }
        val key = CaptureResolutionKey.of(command.ownerId, command.candidateId)
        // Recover a legacy split commit BEFORE similarity can mistake it for another movement.
        receipts.find(command.ownerId, key)?.let { transaction ->
            val recovered = candidate.copy(
                amountMinor = transaction.amountMinor, currency = transaction.currency, occurredAt = transaction.occurredAt,
                accountId = transaction.sourceAccountId ?: candidate.accountId, cardId = transaction.cardId,
                merchantId = transaction.merchantId, categoryId = transaction.categoryId,
                direction = if (transaction.type == MovementType.INCOME) CaptureDirection.INCOME else CaptureDirection.EXPENSE,
                instrumentKind = when {
                    transaction.cardId != null -> CaptureInstrumentKind.CREDIT_CARD
                    transaction.paymentChannel != null -> CaptureInstrumentKind.WALLET
                    candidate.instrumentKind != CaptureInstrumentKind.CREDIT_CARD -> candidate.instrumentKind
                    else -> CaptureInstrumentKind.DEBIT_ACCOUNT
                },
                creditMode = if (transaction.cardId == null) null else if ((transaction.installmentCount ?: 1) > 1) CreditMode.INSTALLMENTS else CreditMode.DIRECT,
                installmentCount = transaction.installmentCount?.takeIf { it > 1 },
            )
            val finalCommand = ConfirmCaptureCommand.fromCandidate(recovered)
            val corrected = finalCommand?.let { correctedFields(candidate, it) } ?: candidate.userCorrectedFields
            check(candidates.resolve(recovered.copy(userCorrectedFields = corrected), CaptureCandidateStatus.CONFIRMED, transaction.id, key, clock.millis()))
            return CaptureConfirmationResult.Registered(transaction.id, alreadyResolved = true)
        }
        if (mode == CaptureConfirmationMode.AUTOMATIC && !AutoRegisterCaptureCandidate.isEligible(candidate)) {
            return CaptureConfirmationResult.Rejected("instrument", "La captura necesita revisión")
        }
        var normalized = command.normalized()
        validate(normalized, mode)?.let { return it }
        val wallet = if (normalized.instrumentKind == CaptureInstrumentKind.WALLET) {
            walletChannels.resolve(command.ownerId, command.selectedAccountId ?: command.accountId.orEmpty(), command.currency, candidate.provider)
                ?: return CaptureConfirmationResult.Rejected("instrument", "La billetera o su cuenta vinculada ya no están disponibles")
        } else null
        if (wallet != null) normalized = normalized.copy(accountId = wallet.accountId)
        val status = if (mode == CaptureConfirmationMode.AUTOMATIC) {
            CaptureCandidateStatus.AUTO_REGISTERED
        } else {
            CaptureCandidateStatus.CONFIRMED
        }

        // Revalidate access immediately before writing to the ledger (HU-47 esc. 5).
        accessDenied(command.ownerId, mode)?.let { return it }
        val outcome = if (normalized.instrumentKind == CaptureInstrumentKind.CREDIT_CARD) {
            registerCredit(normalized, key)
        } else {
            registerMovement(normalized, key, mode, wallet?.channel)
        }
        val transactionId = when (outcome) {
            is LedgerOutcome.Registered -> outcome.transactionId
            is LedgerOutcome.Warning -> return CaptureConfirmationResult.SimilarTransactionWarning(outcome.existing)
            is LedgerOutcome.Failed -> {
                // A concurrent confirmation may have resolved it with a different payload.
                candidates.get(command.ownerId, command.candidateId)?.let { settled(it) }?.let { return it }
                return CaptureConfirmationResult.Rejected(outcome.field, outcome.message)
            }
        }

        val resolved = candidate.withFinalValues(normalized, correctedFields(candidate, normalized))
        val now = clock.millis()
        // A failed resolution must roll back the financial command too.
        check(candidates.resolve(resolved, status, transactionId, key, now)) { "Capture resolution failed" }
        return CaptureConfirmationResult.Registered(transactionId, alreadyResolved = false)
    }

    private fun settled(candidate: CaptureCandidateRecord): CaptureConfirmationResult? = when (candidate.status) {
        CaptureCandidateStatus.PENDING -> null
        CaptureCandidateStatus.DISCARDED -> CaptureConfirmationResult.Discarded
        CaptureCandidateStatus.CONFIRMED, CaptureCandidateStatus.AUTO_REGISTERED ->
            candidate.resolvedTransactionId?.let { CaptureConfirmationResult.Registered(it, alreadyResolved = true) }
                ?: CaptureConfirmationResult.Rejected(null, "La captura ya fue resuelta")
    }

    private suspend fun accessDenied(ownerId: String, mode: CaptureConfirmationMode): CaptureConfirmationResult? =
        when (mode) {
            CaptureConfirmationMode.MANUAL_DRAFT ->
                if (gate.currentOwnerId() == ownerId) null
                else CaptureConfirmationResult.AccessDenied(CaptureAccessDecision.NoSession)
            CaptureConfirmationMode.INBOX, CaptureConfirmationMode.AUTOMATIC ->
                gate.evaluate(ownerId).takeIf { it !is CaptureAccessDecision.Allowed }
                    ?.let { CaptureConfirmationResult.AccessDenied(it) }
                    ?: gate.liveDenial(ownerId)?.let { CaptureConfirmationResult.AccessDenied(it) }
        }

    private fun validate(command: ConfirmCaptureCommand, mode: CaptureConfirmationMode): CaptureConfirmationResult? {
        if (command.amountMinor <= 0L) return CaptureConfirmationResult.Rejected("amount", "El monto debe ser mayor a cero")
        if (command.currency.isBlank()) return CaptureConfirmationResult.Rejected("currency", "La moneda es requerida")
        if (command.instrumentKind != CaptureInstrumentKind.CREDIT_CARD) return null
        // RN-17 / HU-47 esc. 4: credit card purchases always need the user's review.
        if (mode == CaptureConfirmationMode.AUTOMATIC) {
            return CaptureConfirmationResult.Rejected("instrument", CaptureReason.CREDIT_CARD_REQUIRES_REVIEW.label)
        }
        if (command.direction != CaptureDirection.EXPENSE) {
            return CaptureConfirmationResult.Rejected("direction", "Con tarjeta de crédito solo se registran compras")
        }
        if (command.cardId.isNullOrBlank()) return CaptureConfirmationResult.Rejected("instrument", "Selecciona la tarjeta de crédito")
        if (command.categoryId.isNullOrBlank()) {
            return CaptureConfirmationResult.Rejected("category", "La categoría es obligatoria para un gasto")
        }
        if (command.creditMode == CreditMode.INSTALLMENTS && command.installmentCount !in INSTALLMENT_RANGE) {
            return CaptureConfirmationResult.Rejected("installments", "Elige entre 2 y 36 cuotas")
        }
        return null
    }

    private suspend fun registerCredit(command: ConfirmCaptureCommand, key: String): LedgerOutcome {
        val currency = runCatching { Currency.valueOf(command.currency) }.getOrNull()
            ?: return LedgerOutcome.Failed("currency", "Moneda no soportada para la tarjeta")
        val result = try {
            confirmCreditPurchase(
                cardId = CardId(requireNotNull(command.cardId)),
                amount = Money(command.amountMinor, currency),
                merchant = command.merchantText?.takeIf(String::isNotBlank) ?: DEFAULT_CREDIT_MERCHANT,
                effectiveAt = Instant.ofEpochMilli(command.occurredAt),
                installments = if (command.creditMode == CreditMode.INSTALLMENTS) requireNotNull(command.installmentCount) else 1,
                operationId = OperationId(key),
                categoryId = requireNotNull(command.categoryId),
                merchantId = command.merchantId,
                note = command.note,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) { throw error }
        return result.fold(
            onSuccess = { LedgerOutcome.Registered(CaptureResolutionKey.creditTransactionId(command.ownerId, key)) },
            onFailure = { LedgerOutcome.Failed(null, it.message ?: "No se pudo registrar la compra") },
        )
    }

    private suspend fun registerMovement(
        command: ConfirmCaptureCommand,
        key: String,
        mode: CaptureConfirmationMode,
        channel: String?,
    ): LedgerOutcome {
        val accountId = command.accountId?.takeIf(String::isNotBlank)
            ?: return LedgerOutcome.Failed("source_account", "La cuenta es obligatoria")
        val result = try {
            registerTransaction(
                RegisterTransactionCommand(
                    idempotencyKey = key,
                    userId = command.ownerId,
                    type = if (command.direction == CaptureDirection.INCOME) MovementType.INCOME else MovementType.EXPENSE,
                    amountMinor = command.amountMinor,
                    currency = command.currency,
                    sourceAccountId = accountId,
                    categoryId = command.categoryId,
                    merchantId = command.merchantId,
                    merchantProvisionalText = command.merchantText?.takeIf { command.merchantId == null && it.isNotBlank() },
                    occurredAt = command.occurredAt,
                    note = command.note,
                    // Automatic registration never overrides a similarity warning (HU-47).
                    ignoreSimilarityWarning = command.acceptSimilar && mode != CaptureConfirmationMode.AUTOMATIC,
                    paymentChannel = channel,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) { throw error }
        return when (result) {
            is RegisterTransactionResult.Success -> LedgerOutcome.Registered(result.transaction.id)
            is RegisterTransactionResult.SimilarTransactionWarning -> LedgerOutcome.Warning(result.existingTransaction)
            is RegisterTransactionResult.ValidationError -> LedgerOutcome.Failed(result.field, result.message)
            is RegisterTransactionResult.Conflict -> LedgerOutcome.Failed(null, "La captura ya se registró con otros datos")
            is RegisterTransactionResult.Failure -> LedgerOutcome.Failed(null, result.message)
        }
    }

    private sealed interface LedgerOutcome {
        data class Registered(val transactionId: String) : LedgerOutcome
        data class Warning(val existing: Transaction) : LedgerOutcome
        data class Failed(val field: String?, val message: String) : LedgerOutcome
    }

    companion object {
        val INSTALLMENT_RANGE = 2..36
        const val DEFAULT_CREDIT_MERCHANT = "Compra con tarjeta"
    }
}

/** Installments exist only for credit cards: switching to another instrument drops them (HU-48 regla 2). */
internal fun ConfirmCaptureCommand.normalized(): ConfirmCaptureCommand = when {
    instrumentKind == CaptureInstrumentKind.WALLET -> copy(creditMode = null, installmentCount = null, cardId = null)
    instrumentKind == CaptureInstrumentKind.DEBIT_ACCOUNT -> copy(creditMode = null, installmentCount = null)
    creditMode == CreditMode.INSTALLMENTS -> this
    else -> copy(creditMode = CreditMode.DIRECT, installmentCount = null)
}

internal fun correctedFields(candidate: CaptureCandidateRecord, command: ConfirmCaptureCommand): List<String> = buildList {
    if (candidate.amountMinor != command.amountMinor || candidate.currency != command.currency) add(CaptureCorrectedField.AMOUNT)
    if (candidate.occurredAt != command.occurredAt) add(CaptureCorrectedField.OCCURRED_AT)
    if (candidate.merchantId != command.merchantId || candidate.merchantSourceText != command.merchantText) {
        add(CaptureCorrectedField.MERCHANT)
    }
    if (candidate.categoryId != command.categoryId) add(CaptureCorrectedField.CATEGORY)
    if (candidate.instrumentKind != command.instrumentKind || candidate.accountId != command.accountId ||
        candidate.cardId != command.cardId
    ) add(CaptureCorrectedField.INSTRUMENT)
    if (candidate.direction != command.direction) add(CaptureCorrectedField.DIRECTION)
    if (candidate.creditMode != command.creditMode || candidate.installmentCount != command.installmentCount) {
        add(CaptureCorrectedField.CREDIT_MODE)
    }
}.map { it.name }

internal fun CaptureCandidateRecord.withFinalValues(command: ConfirmCaptureCommand, corrected: List<String>) = copy(
    direction = command.direction,
    amountMinor = command.amountMinor,
    currency = command.currency,
    occurredAt = command.occurredAt,
    occurredAtInferred = occurredAtInferred && CaptureCorrectedField.OCCURRED_AT.name !in corrected,
    merchantSourceText = merchantSourceText,
    merchantId = command.merchantId,
    categoryId = command.categoryId,
    instrumentKind = command.instrumentKind,
    accountId = command.accountId,
    cardId = command.cardId,
    instrumentAmbiguous = instrumentAmbiguous && CaptureCorrectedField.INSTRUMENT.name !in corrected,
    creditMode = command.creditMode,
    installmentCount = command.installmentCount,
    userCorrectedFields = corrected,
)
