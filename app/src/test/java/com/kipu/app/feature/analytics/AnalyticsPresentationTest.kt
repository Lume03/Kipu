package com.kipu.app.feature.analytics

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.analytics.domain.AnalyticsCalculator
import com.kipu.app.feature.analytics.domain.AnalyticsFilters
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.ExpenseRow
import com.kipu.app.feature.analytics.domain.PercentageChange
import com.kipu.app.feature.analytics.presentation.AnalyticsMode
import com.kipu.app.feature.analytics.presentation.AnalyticsRequest
import com.kipu.app.feature.analytics.presentation.PercentLabel
import com.kipu.app.feature.analytics.presentation.activeCount
import com.kipu.app.feature.analytics.presentation.categoryShares
import com.kipu.app.feature.analytics.presentation.comparisonFractions
import com.kipu.app.feature.analytics.presentation.datesUi
import com.kipu.app.feature.analytics.presentation.selectableMonths
import com.kipu.app.feature.analytics.presentation.signedAmount
import com.kipu.app.feature.analytics.presentation.toLabel
import com.kipu.app.feature.analytics.presentation.toSelectionUi
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsPresentationTest {
    private val zone = ZoneId.of("America/Lima")
    private val today = LocalDate.of(2026, 11, 3)
    private val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    private fun row(amount: Long, category: String?, currency: String = "PEN", refunded: Long = 0) =
        ExpenseRow(amount, refunded, currency, category, null, null, null)

    @Test fun zeroBaseIsSinBaseNeverInfinityOrNaN() {
        val previous = AnalyticsCalculator.calculate(AnalyticsPeriod.month(YearMonth.of(2026, 10), today, zone), today, AnalyticsFilters(), emptyList())
        val current = AnalyticsCalculator.calculate(AnalyticsPeriod.currentMonth(today, zone), today, AnalyticsFilters(), listOf(row(10_000, "food")))
        val comparison = AnalyticsCalculator.compare(previous, current).byCurrency.getValue(Currency.PEN)
        assertEquals(PercentLabel.NoBase, comparison.percentage.toLabel())
        assertEquals("+100.00", comparison.difference.signedAmount())
    }

    @Test fun percentageIsSignedWithTwoDecimals() {
        assertEquals(PercentLabel.Text("+12.50 %"), PercentageChange.Value(BigDecimal("12.5")).toLabel())
        assertEquals(PercentLabel.Text("−5.00 %"), PercentageChange.Value(BigDecimal("-5")).toLabel())
        assertEquals(PercentLabel.Text("0.00 %"), PercentageChange.Value(BigDecimal.ZERO).toLabel())
        assertEquals(PercentLabel.Text("+1,250.00 %"), PercentageChange.Value(BigDecimal("1250")).toLabel())
        listOf(BigDecimal("12.5"), BigDecimal("-5")).forEach {
            val text = (PercentageChange.Value(it).toLabel() as PercentLabel.Text).value
            assertFalse(text.contains("Infinity") || text.contains("NaN") || text.contains("∞"))
        }
    }

    @Test fun partialPeriodShowsExactDates() {
        val period = AnalyticsPeriod.currentMonth(today, zone)
        val dates = period.datesUi(months, period.isPartial(today))
        assertTrue(dates.partial)
        assertEquals("1 nov 2026", dates.start)
        assertEquals("3 nov 2026", dates.end)
        val closed = AnalyticsPeriod.month(YearMonth.of(2026, 10), today, zone)
        val closedDates = closed.datesUi(months, closed.isPartial(today))
        assertFalse(closedDates.partial)
        assertEquals("31 oct 2026", closedDates.end)
    }

    @Test fun categorySharesUseNetAmountsAndSortDescending() {
        val result = AnalyticsCalculator.calculate(
            AnalyticsPeriod.currentMonth(today, zone), today, AnalyticsFilters(),
            listOf(row(6_000, "food"), row(5_000, "home", refunded = 1_000), row(2_000, null), row(9_900, "usd", currency = "USD")),
        )
        val shares = result.categoryShares(Currency.PEN, mapOf("food" to "Comida"))
        assertEquals(listOf("food", "home", null), shares.map { it.categoryId })
        assertEquals("Comida", shares.first().name)
        assertEquals(Money(4_000, Currency.PEN), shares[1].amount)
        assertEquals(listOf("50", "33.3", "16.7"), shares.map { it.percentText })
        assertEquals(Money(12_000, Currency.PEN), result.totals[Currency.PEN])
        assertEquals(1, result.categoryShares(Currency.USD, emptyMap()).size)
    }

    @Test fun comparisonFractionsHandleZero() {
        assertEquals(0f to 0f, comparisonFractions(Money.zero(Currency.PEN), Money.zero(Currency.PEN)))
        assertEquals(0.5f to 1f, comparisonFractions(Money(50, Currency.PEN), Money(100, Currency.PEN)))
    }

    @Test fun selectionReflectsRequestAndMonthsNeverInFuture() {
        val previous = AnalyticsPeriod.month(YearMonth.of(2026, 9), today, zone)
        val current = AnalyticsPeriod.currentMonth(today, zone)
        val filters = AnalyticsFilters(categoryIds = setOf("a"), currency = Currency.USD)
        val ui = AnalyticsRequest.Compare(previous, current, filters).toSelectionUi(today)
        assertEquals(AnalyticsMode.COMPARE, ui.mode)
        assertEquals(YearMonth.of(2026, 9), ui.previousMonth)
        assertEquals(YearMonth.of(2026, 11), ui.currentMonth)
        assertEquals(2, ui.filters.activeCount())
        assertEquals(AnalyticsMode.CURRENT, AnalyticsRequest.CurrentMonth.toSelectionUi(today).mode)
        val list = selectableMonths(today)
        assertEquals(YearMonth.of(2026, 11), list.first())
        assertTrue(list.none { it.isAfter(YearMonth.from(today)) })
    }
}
