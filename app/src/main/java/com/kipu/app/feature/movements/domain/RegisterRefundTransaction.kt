package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import com.kipu.app.feature.movements.domain.model.RefundTransactionError
import com.kipu.app.feature.movements.domain.model.RefundTransactionResult
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject

class RegisterRefundTransaction @Inject constructor(
    private val repository: MovementRefundRepository,
) {
    suspend operator fun invoke(command: RefundTransactionCommand): RefundTransactionResult {
        if (command.userId.isBlank() || command.idempotencyKey.isBlank() ||
            command.purchaseTransactionId.isBlank() || command.refundTransactionId.isBlank() ||
            command.currency.isBlank()
        ) return RefundTransactionResult.Rejected(RefundTransactionError.INVALID_COMMAND)
        if (command.amountMinor <= 0L) {
            return RefundTransactionResult.Rejected(RefundTransactionError.INVALID_AMOUNT)
        }

        val requestHash = RefundTransactionRequestHasher.hash(command)
        repository.findRefundReplay(command.userId, command.idempotencyKey, requestHash)?.let { return it }

        val purchase = repository.getPurchaseTransaction(command.userId, command.purchaseTransactionId)
            ?: return RefundTransactionResult.Rejected(RefundTransactionError.PURCHASE_NOT_FOUND)
        if (purchase.userId != command.userId) {
            return RefundTransactionResult.Rejected(RefundTransactionError.OWNER_MISMATCH)
        }
        if (!purchase.isRefundEligible()) {
            return RefundTransactionResult.Rejected(RefundTransactionError.PURCHASE_NOT_ELIGIBLE)
        }
        if (!purchase.currency.equals(command.currency, ignoreCase = true)) {
            return RefundTransactionResult.Rejected(RefundTransactionError.CURRENCY_MISMATCH)
        }

        val previousRefunds = repository.getRefundedAmount(command.userId, purchase.id)
        val remaining = try {
            Math.subtractExact(purchase.amountMinor, previousRefunds)
        } catch (_: ArithmeticException) {
            return RefundTransactionResult.Rejected(RefundTransactionError.REFUND_EXCEEDS_REMAINDER, 0L)
        }
        if (remaining <= 0L || command.amountMinor > remaining) {
            return RefundTransactionResult.Rejected(
                RefundTransactionError.REFUND_EXCEEDS_REMAINDER,
                remaining.coerceAtLeast(0L),
            )
        }

        val refund = Transaction(
            id = command.refundTransactionId,
            userId = command.userId,
            type = MovementType.INCOME,
            amountMinor = command.amountMinor,
            currency = purchase.currency.uppercase(Locale.ROOT),
            sourceAccountId = purchase.sourceAccountId,
            // Refunds are income and the category catalog rejects expense categories on income.
            // Net expense consumption follows the canonical purchase->refund link instead.
            categoryId = null,
            merchantId = purchase.merchantId,
            merchantProvisionalText = purchase.merchantProvisionalText,
            occurredAt = command.occurredAt,
            note = null,
            status = TransactionStatus.CONFIRMED,
            syncStatus = MovementSyncStatus.PENDING,
            cardId = purchase.cardId,
            operationKind = "REFUND",
        )
        return repository.commitRefundAtomic(command, requestHash, refund)
    }
}

internal object RefundTransactionRequestHasher {
    fun hash(command: RefundTransactionCommand): String {
        val canonical = listOf(
            "refund-v1",
            command.purchaseTransactionId.lowercase(Locale.ROOT),
            command.refundTransactionId.lowercase(Locale.ROOT),
            command.amountMinor.toString(),
            command.currency.uppercase(Locale.ROOT),
            command.occurredAt.toString(),
        ).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

fun Transaction.isRefundEligible(): Boolean {
    if (type != MovementType.EXPENSE || status !in setOf(
            TransactionStatus.ACTIVE,
            TransactionStatus.CONFIRMED,
            TransactionStatus.REVISED,
        ) || syncStatus in setOf(MovementSyncStatus.CONFLICT, MovementSyncStatus.FAILED_PERMANENT)
    ) return false

    return when (operationKind?.uppercase(Locale.ROOT)) {
        "CARD_PURCHASE" -> cardId != null && sourceAccountId == null && destinationAccountId == null &&
            installmentCount == 1 && legacyKind == null
        null, "STANDARD" -> status != TransactionStatus.ACTIVE &&
            !sourceAccountId.isNullOrBlank() && destinationAccountId == null && cardId == null &&
            installmentCount == null && legacyKind == null
        else -> false
    }
}
