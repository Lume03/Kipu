package com.kipu.app.core.finance.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class BillingCycleCalculatorTest {
    private val qore = BillingCycleConfiguration(26, 19)
    private fun date(value: String) = LocalDate.parse(value)

    @Test fun `Qore day before closing belongs to current bill`() {
        val cycle = BillingCycleCalculator.cycleForPurchase(qore, date("2026-06-25"))
        assertEquals(date("2026-05-26"), cycle.consumptionStartDate)
        assertEquals(date("2026-06-25"), cycle.consumptionEndDate)
        assertEquals(date("2026-06-26"), cycle.closingDate)
        assertEquals(date("2026-07-19"), cycle.dueDate)
    }
    @Test fun `Qore closing day opens following bill`() {
        val cycle = BillingCycleCalculator.cycleForPurchase(qore, date("2026-06-26"))
        assertEquals(date("2026-06-26"), cycle.consumptionStartDate)
        assertEquals(date("2026-07-25"), cycle.consumptionEndDate)
        assertEquals(date("2026-07-26"), cycle.closingDate)
        assertEquals(date("2026-08-19"), cycle.dueDate)
    }
    @Test fun `inclusive issuer retains the closing day in current bill`() {
        val cycle = BillingCycleCalculator.cycleForPurchase(qore.copy(closingDayInclusive = true), date("2026-06-26"))
        assertEquals(date("2026-05-27"), cycle.consumptionStartDate)
        assertEquals(date("2026-06-26"), cycle.consumptionEndDate)
        assertEquals(date("2026-07-19"), cycle.dueDate)
    }
    @Test fun `preferred closing days survive short and leap months`() {
        for (year in listOf(2023, 2024)) for (day in 29..31) {
            val config = BillingCycleConfiguration(day, 5)
            val first = BillingCycleCalculator.cycleForPurchase(config, LocalDate.of(year, 2, 1))
            assertEquals(YearMonth.of(year, 2).atEndOfMonth(), first.closingDate)
            assertEquals(LocalDate.of(year, 3, 5), first.dueDate)
            assertEquals(LocalDate.of(year, 3, day), BillingCycleCalculator.nextCycle(config, first).closingDate)
        }
        for (day in 29..31) {
            val cycle = BillingCycleCalculator.cycleForPurchase(BillingCycleConfiguration(day, 5), date("2026-04-01"))
            assertEquals(LocalDate.of(2026, 4, minOf(day, 30)), cycle.closingDate)
        }
    }
    @Test fun `payment before closing moves month and payment after closing stays month`() {
        assertEquals(date("2026-07-19"), BillingCycleCalculator.cycleForPurchase(qore, date("2026-06-01")).dueDate)
        assertEquals(date("2026-06-28"), BillingCycleCalculator.cycleForPurchase(qore.copy(dueDay = 28), date("2026-06-01")).dueDate)
    }
    @Test fun `December rollover keeps preferred calendar days`() {
        val cycle = BillingCycleCalculator.cycleForPurchase(qore, date("2026-12-26"))
        assertEquals(date("2027-01-26"), cycle.closingDate)
        assertEquals(date("2027-02-19"), cycle.dueDate)
    }
    @Test fun `processing basis uses processing date and falls back to operation`() {
        val config = qore.copy(cycleDateBasis = CycleDateBasis.PROCESSING)
        assertEquals(date("2026-08-19"), BillingCycleCalculator.cycleForPurchase(config, date("2026-06-25"), date("2026-06-26")).dueDate)
        assertEquals(date("2026-07-19"), BillingCycleCalculator.cycleForPurchase(config, date("2026-06-25")).dueDate)
        assertEquals(date("2026-07-19"), BillingCycleCalculator.cycleForPurchase(qore, date("2026-06-25"), date("2026-06-26")).dueDate)
    }
    @Test fun `one through thirty six installments advance closing months without drift`() {
        val config = BillingCycleConfiguration(31, 31)
        for (n in 1..36) {
            val cycles = BillingCycleCalculator.installmentCycles(config, date("2024-01-01"), n)
            assertEquals((1..n).toList(), cycles.map { it.installmentNumber })
            cycles.forEachIndexed { index, item ->
                assertEquals(YearMonth.of(2024, 1).plusMonths(index.toLong()).atEndOfMonth(), item.cycle.closingDate)
                assertEquals(YearMonth.of(2024, 2).plusMonths(index.toLong()).atEndOfMonth(), item.dueDate)
            }
        }
    }
    @Test fun `current and next cycle share purchase classification and zero is identity`() {
        val today = date("2026-06-25")
        val first = BillingCycleCalculator.currentCycle(qore, today)
        assertEquals(BillingCycleCalculator.cycleForPurchase(qore, today), first)
        assertEquals(first, BillingCycleCalculator.nextCycle(qore, first, 0))
        assertEquals(date("2026-08-26"), BillingCycleCalculator.nextCycle(qore, first, 2).closingDate)
    }
    @Test fun `invalid days counts and negative offsets are rejected`() {
        for (day in listOf(0, 32)) {
            assertThrows(IllegalArgumentException::class.java) { BillingCycleConfiguration(day, 19) }
            assertThrows(IllegalArgumentException::class.java) { BillingCycleConfiguration(26, day) }
        }
        for (n in listOf(0, 37)) assertThrows(IllegalArgumentException::class.java) {
            BillingCycleCalculator.installmentCycles(qore, date("2026-06-25"), n)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BillingCycleCalculator.nextCycle(qore, BillingCycleCalculator.currentCycle(qore, date("2026-06-25")), -1)
        }
    }
}
