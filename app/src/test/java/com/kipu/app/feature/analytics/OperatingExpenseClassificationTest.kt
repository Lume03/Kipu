package com.kipu.app.feature.analytics

import com.kipu.app.feature.analytics.domain.OperatingExpenseClassification
import org.junit.Assert.*
import org.junit.Test

class OperatingExpenseClassificationTest {
    @Test fun expenseOf100ExcludesTransferOf200AndDebtPrincipalAndVoided() {
        val records = listOf(
            Triple(10000L, "EXPENSE", null), Triple(20000L, "TRANSFER", null),
            Triple(30000L, "EXPENSE", "DEBT_PAYMENT"), Triple(40000L, "EXPENSE", "VOIDED"),
        )
        val total = records.filter { (_, type, kind) ->
            OperatingExpenseClassification.includes(type, if (kind == "VOIDED") "VOIDED" else "ACTIVE", "SYNCED", if (kind == "VOIDED") null else kind, null)
        }.sumOf { it.first }
        assertEquals(10000L, total)
    }
    @Test fun validInterestAndCardPurchasesCountButRejectedAndLegacyNonOperatingDoNot() {
        assertTrue(OperatingExpenseClassification.includes("EXPENSE", "REVISED", "PENDING", "DEBT_AMORTIZATION", null))
        assertTrue(OperatingExpenseClassification.includes("EXPENSE", "CONFIRMED", "SYNCED", "CARD_PURCHASE", null))
        assertFalse(OperatingExpenseClassification.includes("EXPENSE", "ACTIVE", "FAILED_PERMANENT", "STANDARD", null))
        assertFalse(OperatingExpenseClassification.includes("EXPENSE", "ACTIVE", "SYNCED", null, "GOAL_FUNDING"))
    }
}
