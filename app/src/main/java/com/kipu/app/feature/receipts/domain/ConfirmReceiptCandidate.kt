package com.kipu.app.feature.receipts.domain

import com.kipu.app.feature.movements.domain.RegisterTransaction
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import javax.inject.Inject

/** Always routes through the existing movement validator/repository and uses a stable retry key. */
class ConfirmReceiptCandidate @Inject constructor(
    private val store: ReceiptCandidateStore,
    private val registerTransaction: RegisterTransaction,
) {
    suspend operator fun invoke(ownerId: String, candidateId: String, command: RegisterTransactionCommand): RegisterTransactionResult {
        val original = store.get(ownerId, candidateId)
            ?.takeIf { it.status == ReceiptCandidateStatus.PENDING }
            ?: return RegisterTransactionResult.ValidationError("receipt", "El comprobante ya no está pendiente")
        if (command.userId != ownerId || command.currency != "PEN" ||
            command.type == com.kipu.app.feature.movements.domain.model.MovementType.TRANSFER) {
            return RegisterTransactionResult.ValidationError("receipt", "Revisa los datos del comprobante antes de confirmar")
        }
        // Financial editor corrections become durable candidate fields, while the receipt reference
        // (the deduplication/idempotency evidence) is never copied from a movement command.
        store.edit(ownerId, candidateId, ReceiptCandidateEdit(
            amountMinor = command.amountMinor,
            occurredAt = command.occurredAt,
            recipient = command.merchantProvisionalText ?: original.recipient,
            reference = original.reference,
            isIncome = command.type == com.kipu.app.feature.movements.domain.model.MovementType.INCOME,
        )) ?: return RegisterTransactionResult.ValidationError("receipt", "No se pudo actualizar el comprobante")
        // Candidate identity is stable even when a missing or misread reference is corrected.
        // The unique reference index separately links repeat imports to this same candidate.
        val key = receiptRegistrationKey(ownerId, candidateId)
        // Keep the same idempotency key across duplicate-warning confirmation and retries.
        val result = registerTransaction(command.copy(idempotencyKey = key))
        if (result is RegisterTransactionResult.Success) store.markConfirmed(ownerId, candidateId)
        return result
    }
}
