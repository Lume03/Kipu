package com.kipu.app.feature.debts.presentation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DebtDueAlertsTest {
    private val today = LocalDate.of(2026, 10, 24)

    @Test fun relativeDatesUseSpanishSingulars() {
        assertEquals("Vencida hace 1 día", debtDueDayLabel(today.minusDays(1), today))
        assertEquals("Vence hoy", debtDueDayLabel(today, today))
        assertEquals("Vence mañana", debtDueDayLabel(today.plusDays(1), today))
        assertEquals("Vence en 2 días", debtDueDayLabel(today.plusDays(2), today))
    }
}
