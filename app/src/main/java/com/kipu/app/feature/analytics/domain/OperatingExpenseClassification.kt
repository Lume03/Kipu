package com.kipu.app.feature.analytics.domain

import java.util.Locale

/** Mirrors the operating-expense projection and rejects invalid or non-operating movements. */
object OperatingExpenseClassification {
    fun includes(type: String, status: String, syncStatus: String, operationKind: String?, legacyKind: String?): Boolean {
        fun String.normalized() = uppercase(Locale.ROOT)
        return type == "EXPENSE" && status in setOf("ACTIVE", "CONFIRMED", "REVISED") &&
            syncStatus !in setOf("CONFLICT", "FAILED_PERMANENT") &&
            (operationKind == null || operationKind.normalized() in setOf("STANDARD", "CARD_PURCHASE", "DEBT_AMORTIZATION")) &&
            legacyKind?.normalized() !in setOf("DEBT_DISBURSEMENT", "DEBT_PAYMENT", "GOAL_FUNDING", "REFUND", "OPENING", "ADJUSTMENT", "REVERSAL", "CARD_PAYMENT_CASH")
    }
}
