package com.kipu.app.feature.analytics

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.analytics.domain.AnalyticsCalculator
import com.kipu.app.feature.analytics.domain.AnalyticsFilters
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.BalanceSeriesCalculator
import com.kipu.app.feature.analytics.domain.DestinationKind
import com.kipu.app.feature.analytics.domain.ExpenseRow
import com.kipu.app.feature.analytics.domain.IncomeRow
import com.kipu.app.feature.analytics.domain.LedgerDelta
import com.kipu.app.feature.analytics.domain.MoneyDestinationCalculator
import com.kipu.app.feature.analytics.presentation.percentChange
import com.kipu.app.feature.analytics.presentation.wholePercentages
import com.kipu.app.feature.movements.domain.model.MovementType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsOverviewTest {
    private val zone = ZoneId.of("America/Lima")
    private val today = LocalDate.of(2026, 11, 24)
    private val november = AnalyticsPeriod(LocalDate.of(2026, 11, 1), today, zone)
    private fun millis(day: Int) = LocalDate.of(2026, 11, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun pen(minor: Long) = Money(minor, Currency.PEN)

    @Test fun weekBlocksAreSevenDaysAndTheLastOneEndsWithThePeriod() {
        val blocks = AnalyticsCalculator.weekBlocks(november)
        assertEquals(4, blocks.size)
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 7), blocks.first())
        assertEquals(LocalDate.of(2026, 11, 22) to today, blocks.last())
    }

    @Test fun flowsSplitIncomeAndExpenseByWeekAndAccount() {
        val expenses = listOf(
            ExpenseRow(10_000, 0, "PEN", "food", "acc-1", null, "wong", millis(3)),
            ExpenseRow(5_000, 2_000, "PEN", "food", "acc-1", null, "wong", millis(10)),
            ExpenseRow(7_000, 0, "PEN", "fun", "acc-2", null, null, millis(23)),
        )
        val incomes = listOf(IncomeRow(100_000, "PEN", "acc-1", millis(9)), IncomeRow(20_000, "PEN", "acc-2", millis(23)))
        val result = AnalyticsCalculator.calculate(november, today, AnalyticsFilters(), expenses, incomes)

        assertEquals(pen(20_000), result.totals.getValue(Currency.PEN))
        assertEquals(pen(120_000), result.incomeTotals.getValue(Currency.PEN))
        val weeks = result.weeks.getValue(Currency.PEN)
        assertEquals(listOf(10_000L, 3_000L, 0L, 7_000L), weeks.map { it.expense.minorUnits })
        assertEquals(listOf(0L, 100_000L, 0L, 20_000L), weeks.map { it.income.minorUnits })
        assertEquals(pen(97_000), weeks[1].net)
        val first = result.byAccount.getValue(Currency.PEN).getValue("acc-1")
        assertEquals(pen(100_000), first.income)
        assertEquals(pen(13_000), first.expense)
        assertEquals(3, first.movements)
        assertEquals(pen(13_000), result.byMerchant.getValue(Currency.PEN).getValue("food").getValue("wong"))
        assertTrue(result.byMerchant.getValue(Currency.PEN).getValue("fun").containsKey(null))
        assertTrue(result.hasMovements)
    }

    @Test fun incomeOnlyPeriodStillHasMovementsButNoExpenseRecords() {
        val result = AnalyticsCalculator.calculate(november, today, AnalyticsFilters(), emptyList(), listOf(IncomeRow(5_000, "PEN", "acc-1", millis(2))))
        assertTrue(!result.hasRecords)
        assertTrue(result.hasMovements)
    }

    @Test fun filtersHideIncomeThatCannotMatchThem() {
        val incomes = listOf(IncomeRow(5_000, "PEN", "acc-1", millis(2)))
        val byCategory = AnalyticsCalculator.calculate(november, today, AnalyticsFilters(categoryIds = setOf("food")), emptyList(), incomes)
        assertTrue(byCategory.incomeTotals.isEmpty())
        val byAccount = AnalyticsCalculator.calculate(november, today, AnalyticsFilters(accountIds = setOf("acc-2")), emptyList(), incomes)
        assertTrue(byAccount.incomeTotals.isEmpty())
        val expenseOnly = AnalyticsCalculator.calculate(november, today, AnalyticsFilters(types = setOf(MovementType.EXPENSE)), emptyList(), incomes)
        assertTrue(expenseOnly.incomeTotals.isEmpty())
        val matching = AnalyticsCalculator.calculate(november, today, AnalyticsFilters(accountIds = setOf("acc-1")), emptyList(), incomes)
        assertEquals(pen(5_000), matching.incomeTotals.getValue(Currency.PEN))
    }

    @Test fun previousPeriodComparesTheSameDaysOfTheMonthBefore() {
        val previous = november.previous()
        assertEquals(LocalDate.of(2026, 10, 1), previous.start)
        assertEquals(LocalDate.of(2026, 10, 24), previous.endInclusive)
        val closed = AnalyticsPeriod(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), zone).previous()
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28), closed.start to closed.endInclusive)
        val range = AnalyticsPeriod(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 16), zone).previous()
        assertEquals(LocalDate.of(2026, 11, 3) to LocalDate.of(2026, 11, 9), range.start to range.endInclusive)
    }

    @Test fun dailyBalanceUndoesLaterEntriesAndOnlyCountsTheShownAccounts() {
        val period = AnalyticsPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4), zone)
        val deltas = listOf(
            LedgerDelta("acc-1", "PEN", -2_000, millis(2)),
            LedgerDelta("acc-1", "PEN", 10_000, millis(3)),
            LedgerDelta("acc-1", "PEN", -500, millis(4)),
            LedgerDelta("other", "PEN", 99_999, millis(3)),
            LedgerDelta("acc-1", "USD", 77_777, millis(3)),
        )
        val series = BalanceSeriesCalculator.series(period, pen(50_000), setOf("acc-1"), deltas)
        assertEquals(listOf(42_500L, 40_500L, 50_500L, 50_000L), series.map { it.balance.minorUnits })
        assertEquals(period.endInclusive, series.last().date)
    }

    @Test fun destinationSlicesAlwaysAddUpToTheTotal() {
        val normal = MoneyDestinationCalculator.slices(pen(100_000), pen(30_000), pen(20_000))
        assertEquals(listOf(DestinationKind.FREE, DestinationKind.GOALS, DestinationKind.UPCOMING), normal.map { it.kind })
        assertEquals(listOf(50_000L, 30_000L, 20_000L), normal.map { it.amount.minorUnits })
        val short = MoneyDestinationCalculator.slices(pen(10_000), pen(8_000), pen(9_000))
        assertEquals(listOf(0L, 8_000L, 2_000L), short.map { it.amount.minorUnits })
        assertEquals(10_000L, short.sumOf { it.amount.minorUnits })
        val negative = MoneyDestinationCalculator.slices(pen(-500), pen(100), pen(100))
        assertEquals(listOf(0L, 0L, 0L), negative.map { it.amount.minorUnits })
    }

    @Test fun percentagesRoundToAHundredAndPercentChangeNeverDividesByZero() {
        assertEquals(listOf(34, 33, 33), wholePercentages(listOf(1, 1, 1)))
        assertEquals(100, wholePercentages(listOf(1_230, 640, 470, 180)).sum())
        assertEquals(listOf(0, 0), wholePercentages(listOf(0, 0)))
        assertEquals(-14, percentChange(pen(86_000), pen(100_000)))
        assertEquals(25, percentChange(pen(125), pen(100)))
        assertNull(percentChange(pen(100), pen(0)))
    }
}
