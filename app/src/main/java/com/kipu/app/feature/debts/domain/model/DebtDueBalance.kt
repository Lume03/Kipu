package com.kipu.app.feature.debts.domain.model

import java.time.Instant
import java.time.LocalDate

/** Read-only installment balances; a null installmentId represents an unscheduled obligation. */
data class DebtDueBalance(
    val debtId: String,
    val installmentId: String?,
    val installmentNumber: Int?,
    val dueDate: LocalDate?,
    val plannedMinor: Long?,
    val remainingMinor: Long?,
    val lastSettlementAt: Instant?,
    val lastGeneralSettlementAt: Instant?,
)
