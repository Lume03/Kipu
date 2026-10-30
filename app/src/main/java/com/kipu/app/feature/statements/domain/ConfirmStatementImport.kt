package com.kipu.app.feature.statements.domain

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.accounts.domain.usecase.PayCreditCard
import com.kipu.app.feature.capture.domain.CaptureRegistrationReceipts
import com.kipu.app.feature.categories.domain.MerchantAliasRules
import com.kipu.app.feature.movements.domain.MovementRefundRepository
import com.kipu.app.feature.movements.domain.MovementRepository
import com.kipu.app.feature.movements.domain.RegisterRefundTransaction
import com.kipu.app.feature.movements.domain.isRefundEligible
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import com.kipu.app.feature.movements.domain.model.RefundTransactionError
import com.kipu.app.feature.movements.domain.model.RefundTransactionResult
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

enum class StatementLineFailureReason {
    PremiumRequired, CardRequired, CardUnavailable, CurrencyMismatch, CategoryRequired,
    SourceAccountRequired, SourceAccountInvalid, InsufficientFunds, PaymentExceedsDebt,
    RefundPurchaseRequired, ClassificationRequired, InvalidInstallments, InvalidLine,
    NotSupported, NotFound, ImportNotPending, RegistrationFailed,
}

data class StatementLineFailure(val lineId: String, val reason: StatementLineFailureReason)

data class ConfirmStatementResult(
    val registered: Int,
    val failed: List<StatementLineFailure>,
    val completed: Boolean,
    val error: StatementLineFailureReason? = null,
)

object StatementResolutionKey {
    fun of(importId: String, lineFingerprint: String): String = UUID.nameUUIDFromBytes(
        "kipu:statement:$importId:$lineFingerprint".toByteArray(Charsets.UTF_8),
    ).toString()

    internal fun transactionId(ownerId: String, key: String, role: String): String = UUID.nameUUIDFromBytes(
        "kipu:$ownerId:$key:$role".toByteArray(Charsets.UTF_8),
    ).toString()
}

/** Each selected line is its own transaction; partial failures keep the import pending for retry. */
class ConfirmStatementImport @Inject constructor(
    private val store: StatementImportStore,
    private val access: StatementImportAccess,
    private val matching: StatementImportMatching,
    private val registerCredit: ConfirmCreditPurchase,
    private val payCreditCard: PayCreditCard,
    private val registerRefund: RegisterRefundTransaction,
    private val movements: MovementRepository,
    private val refunds: MovementRefundRepository,
    private val receipts: CaptureRegistrationReceipts,
    private val review: PrepareStatementImportReview,
    private val transactions: DatabaseTransactionRunner,
) {
    suspend operator fun invoke(ownerId: String, importId: String): ConfirmStatementResult {
        if (!access.canImport(ownerId)) return rejected(StatementLineFailureReason.PremiumRequired)
        val snapshot = store.observe(ownerId, importId).first() ?: return rejected(StatementLineFailureReason.NotFound)
        if (snapshot.statement.ownerId != ownerId) return rejected(StatementLineFailureReason.NotFound)
        if (snapshot.statement.status == StatementImportStatus.COMPLETED) return ConfirmStatementResult(0, emptyList(), true)
        if (snapshot.statement.status != StatementImportStatus.PENDING) return rejected(StatementLineFailureReason.ImportNotPending)
        if (snapshot.statement.cardId.isNullOrBlank()) return rejected(StatementLineFailureReason.CardRequired)

        var registered = 0
        val failed = mutableListOf<StatementLineFailure>()
        // Refund matching must see purchases created in this very batch. Payments follow the purchases too.
        val selected = snapshot.lines.filter { it.selected && it.status == StatementImportLineStatus.PENDING }.sortedBy {
            when {
                it.needsPurchaseCategory() -> 0
                it.kind == StatementLineKind.REFUND -> 1
                else -> 2
            }
        }
        for (candidate in selected) {
            try {
                val resolvedLine = transactions { confirmLine(ownerId, importId, candidate.id) }
                if (resolvedLine != null) {
                    registered++
                    // Learning is a follow-up, never a reason to retry a successful ledger command.
                    try {
                        if (access.canImport(ownerId)) review.learn(ownerId, resolvedLine)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Financial registration remains durable. */ }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (rejected: LineRejected) { failed += StatementLineFailure(candidate.id, rejected.reason) }
            catch (_: Exception) { failed += StatementLineFailure(candidate.id, StatementLineFailureReason.RegistrationFailed) }
        }
        val completed = if (failed.isEmpty()) {
            try {
                transactions {
                    val current = store.observe(ownerId, importId).first()
                    when {
                        current == null -> false
                        current.statement.status == StatementImportStatus.COMPLETED -> true
                        current.lines.any { it.selected && it.status == StatementImportLineStatus.PENDING } -> false
                        else -> store.complete(ownerId, importId)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        } else false
        return ConfirmStatementResult(registered, failed, completed)
    }

    private suspend fun confirmLine(ownerId: String, importId: String, lineId: String): StatementImportLine? {
        if (!access.canImport(ownerId)) reject(StatementLineFailureReason.PremiumRequired)
        val snapshot = store.observe(ownerId, importId).first() ?: reject(StatementLineFailureReason.NotFound)
        if (snapshot.statement.ownerId != ownerId || snapshot.statement.status != StatementImportStatus.PENDING) {
            reject(StatementLineFailureReason.ImportNotPending)
        }
        val line = snapshot.lines.singleOrNull { it.id == lineId && it.ownerId == ownerId && it.importId == importId }
            ?: reject(StatementLineFailureReason.NotFound)
        if (line.status != StatementImportLineStatus.PENDING || !line.selected) return null
        val key = StatementResolutionKey.of(importId, line.lineFingerprint)
        // Recover an applied command before edited values or similarity could mistake it for another movement.
        receipts.find(ownerId, key)?.let {
            check(store.markLineRegistered(ownerId, lineId, it.id))
            // Learn the applied category, not draft edits made after a legacy split commit.
            return line.copy(categoryId = it.categoryId, merchantId = it.merchantId,
                description = it.merchantProvisionalText ?: line.description)
        }
        val cardId = snapshot.statement.cardId?.takeIf(String::isNotBlank) ?: reject(StatementLineFailureReason.CardRequired)
        val card = matching.activeCreditCards(ownerId).singleOrNull { it.id == cardId }
            ?: reject(StatementLineFailureReason.CardUnavailable)
        if (card.currency != line.currency) reject(StatementLineFailureReason.CurrencyMismatch)
        if (line.amountMinor <= 0 || line.description.isBlank()) reject(StatementLineFailureReason.InvalidLine)
        val effectiveAt = line.occurredDate.atTime(12, 0).atZone(LIMA).toInstant()
        val money = Money(line.amountMinor, line.currency)

        val movementId = when (line.kind) {
            StatementLineKind.PURCHASE, StatementLineKind.INSTALLMENT_PURCHASE,
            StatementLineKind.INTEREST, StatementLineKind.INSURANCE, StatementLineKind.FEE -> {
                val category = review.effectiveCategory(ownerId, line) ?: reject(StatementLineFailureReason.CategoryRequired)
                val installments = if (line.kind == StatementLineKind.INSTALLMENT_PURCHASE) {
                    line.installmentCount?.takeIf { it in 2..36 } ?: reject(StatementLineFailureReason.InvalidInstallments)
                } else 1
                // This existing credit path has no similarity prompt. A selected line has been explicitly
                // reviewed; possible duplicates are initially unselected by ImportCardStatement.
                if (!access.canImport(ownerId)) reject(StatementLineFailureReason.PremiumRequired)
                registerCredit(CardId(cardId), money, line.description, effectiveAt, installments,
                    OperationId(key), category, line.merchantId, null).getOrThrow()
                StatementResolutionKey.transactionId(ownerId, key, "purchase")
            }
            StatementLineKind.CARD_PAYMENT -> {
                val source = line.sourceAccountId?.takeIf(String::isNotBlank) ?: reject(StatementLineFailureReason.SourceAccountRequired)
                if (!access.canImport(ownerId)) reject(StatementLineFailureReason.PremiumRequired)
                val result = payCreditCard(CardId(cardId), AccountId(source), money, effectiveAt, OperationId(key))
                result.exceptionOrNull()?.let {
                    if (it is CancellationException) throw it
                    reject(paymentFailure(it))
                }
                StatementResolutionKey.transactionId(ownerId, key, "payment")
            }
            StatementLineKind.REFUND -> {
                val purchaseId = refundPurchase(ownerId, cardId, line) ?: reject(StatementLineFailureReason.RefundPurchaseRequired)
                if (!access.canImport(ownerId)) reject(StatementLineFailureReason.PremiumRequired)
                when (val result = registerRefund(RefundTransactionCommand(key, ownerId, purchaseId,
                    StatementResolutionKey.transactionId(ownerId, key, "refund"), line.amountMinor, line.currency.name, effectiveAt.toEpochMilli()))) {
                    is RefundTransactionResult.Success -> result.transaction.id
                    is RefundTransactionResult.Rejected -> reject(when (result.error) {
                        RefundTransactionError.CURRENCY_MISMATCH -> StatementLineFailureReason.CurrencyMismatch
                        RefundTransactionError.PURCHASE_NOT_FOUND, RefundTransactionError.PURCHASE_NOT_ELIGIBLE,
                        RefundTransactionError.REFUND_EXCEEDS_REMAINDER -> StatementLineFailureReason.RefundPurchaseRequired
                        else -> StatementLineFailureReason.RegistrationFailed
                    })
                    is RefundTransactionResult.Conflict -> reject(StatementLineFailureReason.RegistrationFailed)
                }
            }
            StatementLineKind.UNKNOWN -> reject(StatementLineFailureReason.ClassificationRequired)
            StatementLineKind.POINTS_REDEMPTION, StatementLineKind.INSTALLMENT_MONTHLY_CHARGE -> reject(StatementLineFailureReason.NotSupported)
        }
        check(store.markLineRegistered(ownerId, lineId, movementId))
        return line
    }

    private suspend fun refundPurchase(ownerId: String, cardId: String, line: StatementImportLine): String? {
        val earliest = line.occurredDate.minusDays(120)
        val candidates = movements.observeTransactions(ownerId).first().map { it.transaction }.filter {
            val date = java.time.Instant.ofEpochMilli(it.occurredAt).atZone(LIMA).toLocalDate()
            it.userId == ownerId && it.cardId == cardId && it.currency == line.currency.name && it.isRefundEligible() &&
                date >= earliest && date <= line.occurredDate &&
                it.amountMinor - refunds.getRefundedAmount(ownerId, it.id) >= line.amountMinor
        }
        val normalized = MerchantAliasRules.normalize(line.description)
        val matching = candidates.filter {
            val description = it.merchantProvisionalText ?: it.merchantId?.let { merchantId ->
                // Catalog ids are preferable when the user resolved the merchant in review.
                line.description.takeIf { line.merchantId == merchantId }
            }
            description != null && MerchantAliasRules.normalize(description) == normalized
        }
        return (if (matching.isNotEmpty()) matching else candidates).singleOrNull()?.id
    }

    private fun paymentFailure(error: Throwable): StatementLineFailureReason = when {
        error.message?.contains("exceeds current card debt", ignoreCase = true) == true -> StatementLineFailureReason.PaymentExceedsDebt
        error.message?.contains("Insufficient funds", ignoreCase = true) == true -> StatementLineFailureReason.InsufficientFunds
        error.message?.contains("currency mismatch", ignoreCase = true) == true -> StatementLineFailureReason.CurrencyMismatch
        error.message?.contains("account", ignoreCase = true) == true -> StatementLineFailureReason.SourceAccountInvalid
        else -> StatementLineFailureReason.RegistrationFailed
    }

    private fun rejected(reason: StatementLineFailureReason) = ConfirmStatementResult(0, emptyList(), false, reason)
    private fun reject(reason: StatementLineFailureReason): Nothing = throw LineRejected(reason)
    private class LineRejected(val reason: StatementLineFailureReason) : RuntimeException()

    private companion object { val LIMA: ZoneId = ZoneId.of("America/Lima") }
}
