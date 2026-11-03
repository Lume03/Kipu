package com.kipu.app.feature.analytics

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.analytics.domain.*
import com.kipu.app.feature.home.domain.*
import com.kipu.app.feature.movements.domain.model.MovementType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class AnalyticsCalculatorTest {
    private val zone = ZoneId.of("America/Lima")
    private val today = LocalDate.of(2026, 11, 3)
    private val previous = AnalyticsPeriod.month(YearMonth.of(2026, 10), today, zone)
    private val current = AnalyticsPeriod.month(YearMonth.of(2026, 11), today, zone)
    private fun expense(amount: Long, refund: Long = 0, currency: String = "PEN", category: String = "food", account: String = "cash", card: String? = null, merchant: String? = null) = ExpenseRow(amount, refund, currency, category, account, card, merchant)
    private fun result(period: AnalyticsPeriod, vararg rows: ExpenseRow) = AnalyticsCalculator.calculate(period, today, AnalyticsFilters(), rows.toList())

    @Test fun expenseNetOfRefundAndCurrencyIsolated() {
        val result = result(current, expense(10000, 2500), expense(300, currency = "USD"))
        assertEquals(Money(7500, Currency.PEN), result.totals[Currency.PEN])
        assertEquals(Money(300, Currency.USD), result.totals[Currency.USD])
        assertTrue(result.hasRecords)
    }
    @Test fun combinedFiltersAndExpenseType() {
        val rows = listOf(expense(10000), expense(9000, category = "rent"), expense(8000, account = "other"), expense(7000, currency = "USD"))
        val filters = AnalyticsFilters(categoryIds = setOf("food"), accountIds = setOf("cash"), types = setOf(MovementType.EXPENSE), currency = Currency.PEN)
        assertEquals(Money(10000, Currency.PEN), AnalyticsCalculator.calculate(current, today, filters, rows).totals[Currency.PEN])
        assertFalse(AnalyticsCalculator.calculate(current, today, filters.copy(types = setOf(MovementType.TRANSFER)), rows).hasRecords)
    }
    @Test fun comparisonWithValidAndZeroBase() {
        val comparison = AnalyticsCalculator.compare(result(previous, expense(10000)), result(current, expense(15000), expense(200, currency = "USD")))
        assertEquals(Money(5000, Currency.PEN), comparison.byCurrency.getValue(Currency.PEN).difference)
        assertEquals(PercentageChange.Value(BigDecimal("50.00")), comparison.byCurrency.getValue(Currency.PEN).percentage)
        assertEquals(PercentageChange.SinBaseDeComparacion, comparison.byCurrency.getValue(Currency.USD).percentage)
    }
    @Test fun inclusiveExactDatesAndPartialMonth() {
        assertEquals(LocalDate.of(2026,10,1), previous.start)
        assertEquals(LocalDate.of(2026,10,31), previous.endInclusive)
        assertFalse(result(previous).partial)
        assertEquals(today, current.endInclusive)
        assertTrue(result(current).partial)
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), current.toExclusiveMillis)
        assertEquals(current.start.atStartOfDay(zone).toInstant().toEpochMilli(), current.fromMillis)
    }
    @Test fun netWorthIncludesReceivablesAndDeductsCardDebtOnce() {
        val data = NetWorthInputs(listOf(Money(10000, Currency.PEN)), listOf(Money(2000, Currency.PEN)), emptyList(), listOf(Money(3000, Currency.PEN)))
        val summary = NetWorthCalculator.calculate(data) as NetWorthSummary.Available
        assertEquals(Money(9000, Currency.PEN), summary.byCurrency.single().netWorth)
    }
    @Test fun fullRefundIsConfirmedZeroNotEmpty() {
        val result = result(current, expense(10000, 10000))
        assertTrue(result.hasRecords)
        assertEquals(Money.zero(Currency.PEN), result.totals[Currency.PEN])
    }
}
