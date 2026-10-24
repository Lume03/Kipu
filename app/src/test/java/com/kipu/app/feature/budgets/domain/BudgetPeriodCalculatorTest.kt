package com.kipu.app.feature.budgets.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPeriodCalculatorTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun rec(t: BudgetPeriodType) = BudgetSchedule.Recurring(t)
    private fun iv(a: String, b: String) = BudgetInterval(d(a), d(b))

    @Test fun daily() {
        assertEquals(iv("2026-10-24", "2026-10-25"), BudgetPeriodCalculator.periodContaining(rec(BudgetPeriodType.DAILY), d("2026-10-24")))
    }

    @Test fun weeklyRunsMondayToSunday() {
        // 2026-10-24 es sabado; semana 19 -> 25.
        val p = BudgetPeriodCalculator.periodContaining(rec(BudgetPeriodType.WEEKLY), d("2026-10-24"))
        assertEquals(iv("2026-10-19", "2026-10-26"), p)
        assertEquals(iv("2026-10-19", "2026-10-26"), BudgetPeriodCalculator.periodContaining(rec(BudgetPeriodType.WEEKLY), d("2026-10-19")))
        assertEquals(iv("2026-10-19", "2026-10-26"), BudgetPeriodCalculator.periodContaining(rec(BudgetPeriodType.WEEKLY), d("2026-10-25")))
        assertEquals(iv("2026-10-26", "2026-11-02"), BudgetPeriodCalculator.periodContaining(rec(BudgetPeriodType.WEEKLY), d("2026-10-26")))
    }

    @Test fun biweeklySplitsAt15And16() {
        val s = rec(BudgetPeriodType.BIWEEKLY)
        assertEquals(iv("2026-10-01", "2026-10-16"), BudgetPeriodCalculator.periodContaining(s, d("2026-10-15")))
        assertEquals(iv("2026-10-16", "2026-11-01"), BudgetPeriodCalculator.periodContaining(s, d("2026-10-16")))
        assertEquals(iv("2026-02-16", "2026-03-01"), BudgetPeriodCalculator.periodContaining(s, d("2026-02-28")))
        assertEquals(iv("2026-12-16", "2027-01-01"), BudgetPeriodCalculator.periodContaining(s, d("2026-12-31")))
    }

    @Test fun monthlyIsCalendarMonth() {
        val s = rec(BudgetPeriodType.MONTHLY)
        assertEquals(iv("2026-09-01", "2026-10-01"), BudgetPeriodCalculator.periodContaining(s, d("2026-09-30")))
        assertEquals(iv("2024-02-01", "2024-03-01"), BudgetPeriodCalculator.periodContaining(s, d("2024-02-29")))
    }

    @Test fun nextPeriodIsContiguous() {
        val s = rec(BudgetPeriodType.MONTHLY)
        assertEquals(iv("2026-10-01", "2026-11-01"), BudgetPeriodCalculator.nextPeriod(s, iv("2026-09-01", "2026-10-01")))
        assertEquals(iv("2026-10-16", "2026-11-01"), BudgetPeriodCalculator.nextPeriod(rec(BudgetPeriodType.BIWEEKLY), iv("2026-10-01", "2026-10-16")))
    }

    @Test fun customRangeIsInclusiveAndNeverRenews() {
        val s = BudgetSchedule.CustomRange(d("2026-10-10"), d("2026-10-20"))
        val p = BudgetPeriodCalculator.periodContaining(s, d("2026-10-20"))
        assertEquals(iv("2026-10-10", "2026-10-21"), p)
        assertNull(BudgetPeriodCalculator.periodContaining(s, d("2026-10-21")))
        assertNull(BudgetPeriodCalculator.periodContaining(s, d("2026-10-09")))
        assertNull(BudgetPeriodCalculator.nextPeriod(s, p!!))
        assertEquals(p, BudgetPeriodCalculator.firstPeriod(s, d("2026-10-01")))
    }

    @Test fun missingPeriodsFillsGapWithoutDuplicates() {
        val s = rec(BudgetPeriodType.MONTHLY)
        val missing = BudgetPeriodCalculator.missingPeriods(s, iv("2026-08-01", "2026-09-01"), d("2026-10-24"))
        assertEquals(listOf(iv("2026-09-01", "2026-10-01"), iv("2026-10-01", "2026-11-01")), missing)
        assertTrue(BudgetPeriodCalculator.missingPeriods(s, iv("2026-10-01", "2026-11-01"), d("2026-10-24")).isEmpty())
    }

    @Test fun intervalConvertsToInstantsWithGivenZone() {
        val lima = ZoneId.of("America/Lima")
        val i = iv("2026-10-01", "2026-11-01")
        assertEquals(java.time.Instant.parse("2026-10-01T05:00:00Z").toEpochMilli(), i.fromInclusiveMillis(lima))
        assertEquals(java.time.Instant.parse("2026-11-01T05:00:00Z").toEpochMilli(), i.toExclusiveMillis(lima))
    }
}
