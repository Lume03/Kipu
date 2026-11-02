package com.kipu.app.feature.debts.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class DebtClosureSummaryTest {
    @Test fun `closure distinguishes payments forgiveness and adjustments and ignores voided entries`() {
        fun activity(type: String, amount: Long, at: Long, delta: Long? = null, voided: Boolean = false) =
            DebtSettlementActivity(type, amount, 0, at, voided, type, delta)
        val summary = summarizeClosure(listOf(
            activity("PAYMENT", 500, 1), activity("FORGIVENESS", 300, 2),
            activity("ADJUSTMENT", 200, 3, -200), activity("PAYMENT", 900, 4, voided = true),
        ))
        assertEquals(DebtClosureSummary(500, 300, -200, 3), summary)
    }
}
