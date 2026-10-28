package com.kipu.app.feature.budgets.presentation

import com.kipu.app.feature.budgets.domain.BudgetConsumption
import com.kipu.app.feature.budgets.domain.BudgetInterval
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetPaceTest {
    private val interval = BudgetInterval(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
    private fun row(consumed: Long) = BudgetPeriodRow("period", interval, "Todos los gastos", BudgetConsumption(10000, consumed), true)

    @Test fun projectedOverspendUsesElapsedAndRemainingDays() {
        assertEquals(BudgetPace(8600, 600, 190), budgetPace(row(6000), true, LocalDate.of(2026, 10, 10)))
    }

    @Test fun noBannerWhenWithinLimitOrOutsideActivePeriod() {
        assertNull(budgetPace(row(1000), true, LocalDate.of(2026, 10, 10)))
        assertNull(budgetPace(row(6000), false, LocalDate.of(2026, 10, 10)))
        assertNull(budgetPace(row(6000), true, LocalDate.of(2026, 11, 1)))
        assertNull(budgetPace(row(12000), true, LocalDate.of(2026, 10, 31)))
    }
}

