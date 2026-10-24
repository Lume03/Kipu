package com.kipu.app.feature.budgets.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetConsumptionTest {
    @Test fun spent120Of500IsRemaining380And24Percent() {
        val c = BudgetConsumption(limitMinor = 50_000, consumedMinor = 12_000)
        assertEquals(38_000L, c.remainingMinor)
        assertEquals(2400L, c.percentBps)
        assertEquals(24L, c.percentWhole)
        assertFalse(c.isExceeded)
    }

    @Test fun percentRoundsDown() {
        assertEquals(3333L, BudgetConsumption(30_000, 10_000).percentBps)
        assertEquals(0L, BudgetConsumption(1_000_000, 99).percentBps)
        assertEquals(0L, BudgetConsumption(1_000, 0).percentBps)
    }

    @Test fun exceededHasNegativeRemainingAndOver100() {
        val c = BudgetConsumption(50_000, 60_000)
        assertTrue(c.isExceeded)
        assertEquals(-10_000L, c.remainingMinor)
        assertEquals(12000L, c.percentBps)
    }

    @Test fun exactLimitIsNotExceeded() {
        val c = BudgetConsumption(50_000, 50_000)
        assertFalse(c.isExceeded)
        assertEquals(0L, c.remainingMinor)
        assertEquals(10000L, c.percentBps)
    }

    @Test fun hugeAmountsDoNotOverflow() {
        assertEquals(4999L, BudgetConsumption(Long.MAX_VALUE, Long.MAX_VALUE / 2).percentBps)
    }

    @Test fun useCaseDerivesConsumptionFromExpensesOfBudgetCurrencyOnly() = runBlocking {
        val env = Env()
        env.createBudget(limit = 50_000, today = "2026-10-03")
        env.expense("transporte", "2026-10-05", 12_000)
        env.expense("transporte", "2026-10-06", 99_000, currency = "USD") // otra moneda: no cuenta
        env.expense("transporte", "2026-09-30", 7_000) // periodo anterior
        val r = (env.consumption.current(OWNER, "b1", day("2026-10-10"), TEST_ZONE) as BudgetOutcome.Success).value
        assertEquals(12_000L, r.consumption.consumedMinor)
        assertEquals(38_000L, r.consumption.remainingMinor)
        assertEquals(24L, r.consumption.percentWhole)
    }

    @Test fun consumptionIsRecomputedNotStored() = runBlocking {
        val env = Env()
        env.createBudget(today = "2026-10-03")
        env.expense("transporte", "2026-10-05", 1_000)
        val a = (env.consumption.current(OWNER, "b1", day("2026-10-10"), TEST_ZONE) as BudgetOutcome.Success).value
        env.reader.expenses.clear() // p.ej. reembolso total o anulacion
        val b = (env.consumption.current(OWNER, "b1", day("2026-10-10"), TEST_ZONE) as BudgetOutcome.Success).value
        assertEquals(1_000L, a.consumption.consumedMinor)
        assertEquals(0L, b.consumption.consumedMinor)
    }
}
