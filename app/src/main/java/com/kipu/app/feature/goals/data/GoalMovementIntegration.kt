package com.kipu.app.feature.goals.data

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.goals.domain.usecase.GoalTransferIntent

/** Keeps optional internal transfers inside the existing movement command/feed boundary. */
object GoalMovementIntegration {
    sealed interface TransferDecision {
        data object ReservationOnly : TransferDecision
        data class InternalTransfer(val intent: GoalTransferIntent) : TransferDecision
        data class Rejected(val code: String) : TransferDecision
    }

    fun prepareTransfer(
        intent: GoalTransferIntent?,
        backingAccountId: String,
        currency: Currency,
    ): TransferDecision {
        if (backingAccountId.isBlank()) return TransferDecision.Rejected("BACKING_ACCOUNT_REQUIRED")
        if (intent == null) return TransferDecision.ReservationOnly
        if (intent.sourceAccountId.isBlank() || intent.destinationAccountId.isBlank()) {
            return TransferDecision.Rejected("INVALID_TRANSFER")
        }
        if (intent.destinationAccountId != backingAccountId) return TransferDecision.Rejected("INVALID_TRANSFER_DESTINATION")
        if (intent.currency != currency) return TransferDecision.Rejected("CURRENCY_MISMATCH")
        if (intent.sourceAccountId == intent.destinationAccountId) return TransferDecision.ReservationOnly
        return TransferDecision.InternalTransfer(intent)
    }
}
