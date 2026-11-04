package com.kipu.app.feature.analytics.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.movements.domain.model.MovementType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

/** Dates are inclusive and use the same device business zone as movement history. */
data class AnalyticsPeriod(val start: LocalDate, val endInclusive: LocalDate, val zone: ZoneId) {
    init { require(!endInclusive.isBefore(start)) }
    val fromMillis: Long get() = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val toExclusiveMillis: Long get() = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun isPartial(today: LocalDate): Boolean = !endInclusive.isBefore(today)

    /**
     * The period to compare against. A month-aligned period is compared with the same days of the month before
     * (so a month in progress never looks like savings); any other range with the equally long range before it.
     */
    fun previous(): AnalyticsPeriod {
        if (start.dayOfMonth == 1) {
            val previousStart = start.minusMonths(1)
            val span = java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive)
            val previousEnd = minOf(previousStart.plusDays(span), previousStart.withDayOfMonth(previousStart.lengthOfMonth()))
            return AnalyticsPeriod(previousStart, previousEnd, zone)
        }
        val length = java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive) + 1
        return AnalyticsPeriod(start.minusDays(length), start.minusDays(1), zone)
    }
    companion object {
        fun currentMonth(today: LocalDate, zone: ZoneId) = AnalyticsPeriod(today.withDayOfMonth(1), today, zone)
        fun month(month: YearMonth, today: LocalDate, zone: ZoneId): AnalyticsPeriod {
            require(!month.atDay(1).isAfter(today))
            return AnalyticsPeriod(month.atDay(1), minOf(month.atEndOfMonth(), today), zone)
        }
    }
}

data class AnalyticsFilters(
    val categoryIds: Set<String> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val cardIds: Set<String> = emptySet(),
    val merchantIds: Set<String> = emptySet(),
    val types: Set<MovementType> = emptySet(),
    val currency: Currency? = null,
)

data class ExpenseRow(
    val amountMinor: Long, val refundedMinor: Long, val currency: String,
    val categoryId: String?, val accountId: String?, val cardId: String?, val merchantId: String?,
    val occurredAtMillis: Long = 0L,
)

/** Operating income only: transfers, refunds, openings and adjustments never reach this list. */
data class IncomeRow(val amountMinor: Long, val currency: String, val accountId: String?, val occurredAtMillis: Long)

/** One block of seven days (the last one may be shorter) inside the analysed period. */
data class WeekFlow(val index: Int, val start: LocalDate, val endInclusive: LocalDate, val income: Money, val expense: Money) {
    val net: Money get() = income - expense
}

/** What one liquid account moved in the period; used by the "where is your money" detail. */
data class AccountFlow(val income: Money, val expense: Money, val movements: Int)

data class PeriodResult(
    val period: AnalyticsPeriod, val partial: Boolean, val totals: Map<Currency, Money>, val hasRecords: Boolean,
    /** Operating expense per category id (null = sin categoría), per currency; never converted. */
    val byCategory: Map<Currency, Map<String?, Money>> = emptyMap(),
    val incomeTotals: Map<Currency, Money> = emptyMap(),
    val weeks: Map<Currency, List<WeekFlow>> = emptyMap(),
    val byAccount: Map<Currency, Map<String, AccountFlow>> = emptyMap(),
    /** Expense per currency, category id and merchant id (null = sin comercio). */
    val byMerchant: Map<Currency, Map<String?, Map<String?, Money>>> = emptyMap(),
) {
    val hasIncome: Boolean get() = incomeTotals.values.any { it.minorUnits > 0L }
    val hasMovements: Boolean get() = hasRecords || hasIncome
}
sealed interface PercentageChange {
    data object SinBaseDeComparacion : PercentageChange
    data class Value(val percent: BigDecimal) : PercentageChange
}
data class CurrencyComparison(val previous: Money, val current: Money, val difference: Money, val percentage: PercentageChange)
data class AnalyticsComparison(val previous: PeriodResult, val current: PeriodResult, val byCurrency: Map<Currency, CurrencyComparison>)

object AnalyticsCalculator {
    fun calculate(
        period: AnalyticsPeriod, today: LocalDate, filters: AnalyticsFilters, rows: List<ExpenseRow>,
        incomes: List<IncomeRow> = emptyList(),
    ): PeriodResult {
        val matched = rows.filter { row ->
            (filters.types.isEmpty() || MovementType.EXPENSE in filters.types) &&
                (filters.categoryIds.isEmpty() || row.categoryId in filters.categoryIds) &&
                (filters.accountIds.isEmpty() || row.accountId in filters.accountIds) &&
                (filters.cardIds.isEmpty() || row.cardId in filters.cardIds) &&
                (filters.merchantIds.isEmpty() || row.merchantId in filters.merchantIds) &&
                (filters.currency == null || Currency.fromCode(row.currency) == filters.currency)
        }
        fun net(row: ExpenseRow, currency: Currency) = Money((row.amountMinor - row.refundedMinor.coerceAtMost(row.amountMinor)).coerceAtLeast(0), currency)
        val byCurrency = matched.groupBy { Currency.fromCode(it.currency) }
        val totals = byCurrency.mapValues { (currency, entries) ->
            entries.fold(Money.zero(currency)) { total, row -> total + net(row, currency) }
        }
        val byCategory = byCurrency.mapValues { (currency, entries) ->
            entries.groupBy { it.categoryId }.mapValues { (_, rows) -> rows.fold(Money.zero(currency)) { total, row -> total + net(row, currency) } }
                .filterValues { it.minorUnits > 0L }
        }
        val withSelectedCurrency = if (filters.currency != null && filters.currency !in totals) totals + (filters.currency to Money.zero(filters.currency)) else totals

        // Income has no category/card/merchant filter to match, so any of those hides it instead of misreporting it.
        val incomeAllowed = (filters.types.isEmpty() || MovementType.INCOME in filters.types) &&
            filters.categoryIds.isEmpty() && filters.cardIds.isEmpty() && filters.merchantIds.isEmpty()
        val matchedIncome = if (!incomeAllowed) emptyList() else incomes.filter { row ->
            (filters.accountIds.isEmpty() || row.accountId in filters.accountIds) &&
                (filters.currency == null || Currency.fromCode(row.currency) == filters.currency)
        }
        val incomeByCurrency = matchedIncome.groupBy { Currency.fromCode(it.currency) }
        val incomeTotals = incomeByCurrency.mapValues { (currency, entries) -> Money(entries.sumOf { it.amountMinor }, currency) }

        val currencies = (byCurrency.keys + incomeByCurrency.keys).toSortedSet()
        val blocks = weekBlocks(period)
        val zone = period.zone
        fun dayOf(millis: Long): LocalDate = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        fun blockIndex(day: LocalDate): Int = blocks.indexOfFirst { day in it.first..it.second }
        val weeks = currencies.associateWith { currency ->
            val expenseRows = byCurrency[currency].orEmpty()
            val incomeRows = incomeByCurrency[currency].orEmpty()
            blocks.mapIndexed { index, (start, end) ->
                val expense = expenseRows.filter { blockIndex(dayOf(it.occurredAtMillis)) == index }
                    .sumOf { net(it, currency).minorUnits }
                val income = incomeRows.filter { blockIndex(dayOf(it.occurredAtMillis)) == index }.sumOf { it.amountMinor }
                WeekFlow(index, start, end, Money(income, currency), Money(expense, currency))
            }
        }
        val byAccount = currencies.associateWith { currency ->
            val expenseByAccount = byCurrency[currency].orEmpty().filter { it.accountId != null }.groupBy { it.accountId!! }
            val incomeByAccount = incomeByCurrency[currency].orEmpty().filter { it.accountId != null }.groupBy { it.accountId!! }
            (expenseByAccount.keys + incomeByAccount.keys).associateWith { id ->
                val spent = expenseByAccount[id].orEmpty()
                val earned = incomeByAccount[id].orEmpty()
                AccountFlow(
                    income = Money(earned.sumOf { it.amountMinor }, currency),
                    expense = spent.fold(Money.zero(currency)) { total, row -> total + net(row, currency) },
                    movements = spent.size + earned.size,
                )
            }
        }
        val byMerchant = byCurrency.mapValues { (currency, entries) ->
            entries.groupBy { it.categoryId }.mapValues { (_, inCategory) ->
                inCategory.groupBy { it.merchantId }.mapValues { (_, rows) -> rows.fold(Money.zero(currency)) { total, row -> total + net(row, currency) } }
                    .filterValues { it.minorUnits > 0L }
            }
        }
        return PeriodResult(period, period.isPartial(today), withSelectedCurrency, matched.isNotEmpty(), byCategory, incomeTotals, weeks, byAccount, byMerchant)
    }

    /** Consecutive seven-day blocks counted from the first day; the last block ends with the period. */
    fun weekBlocks(period: AnalyticsPeriod): List<Pair<LocalDate, LocalDate>> {
        val blocks = ArrayList<Pair<LocalDate, LocalDate>>()
        var start = period.start
        while (!start.isAfter(period.endInclusive)) {
            blocks += start to minOf(start.plusDays(6), period.endInclusive)
            start = start.plusDays(7)
        }
        return blocks
    }
    fun compare(previous: PeriodResult, current: PeriodResult): AnalyticsComparison {
        val currencies = previous.totals.keys + current.totals.keys
        return AnalyticsComparison(previous, current, currencies.associateWith { currency ->
            val before = previous.totals[currency] ?: Money.zero(currency)
            val after = current.totals[currency] ?: Money.zero(currency)
            val delta = after - before
            val percentage = if (before.minorUnits == 0L) PercentageChange.SinBaseDeComparacion else
                PercentageChange.Value(BigDecimal.valueOf(delta.minorUnits).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(before.minorUnits), 2, RoundingMode.HALF_UP))
            CurrencyComparison(before, after, delta, percentage)
        })
    }
}

/** [icon] and [color] only come from categories, and [merchant] only from merchants; both stay presentation data. */
data class AnalyticsFilterOption(
    val id: String, val name: String,
    val icon: String? = null, val color: String? = null,
    val merchant: MerchantVisual? = null,
)

/** Selectable values for [AnalyticsFilters]; names are presentation only and never affect totals. */
data class AnalyticsFilterOptions(
    val categories: List<AnalyticsFilterOption> = emptyList(),
    val accounts: List<AnalyticsFilterOption> = emptyList(),
    val cards: List<AnalyticsFilterOption> = emptyList(),
    val merchants: List<AnalyticsFilterOption> = emptyList(),
)
