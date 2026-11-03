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
)

data class PeriodResult(
    val period: AnalyticsPeriod, val partial: Boolean, val totals: Map<Currency, Money>, val hasRecords: Boolean,
    /** Operating expense per category id (null = sin categoría), per currency; never converted. */
    val byCategory: Map<Currency, Map<String?, Money>> = emptyMap(),
)
sealed interface PercentageChange {
    data object SinBaseDeComparacion : PercentageChange
    data class Value(val percent: BigDecimal) : PercentageChange
}
data class CurrencyComparison(val previous: Money, val current: Money, val difference: Money, val percentage: PercentageChange)
data class AnalyticsComparison(val previous: PeriodResult, val current: PeriodResult, val byCurrency: Map<Currency, CurrencyComparison>)

object AnalyticsCalculator {
    fun calculate(period: AnalyticsPeriod, today: LocalDate, filters: AnalyticsFilters, rows: List<ExpenseRow>): PeriodResult {
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
        return PeriodResult(period, period.isPartial(today), withSelectedCurrency, matched.isNotEmpty(), byCategory)
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

data class AnalyticsFilterOption(val id: String, val name: String)

/** Selectable values for [AnalyticsFilters]; names are presentation only and never affect totals. */
data class AnalyticsFilterOptions(
    val categories: List<AnalyticsFilterOption> = emptyList(),
    val accounts: List<AnalyticsFilterOption> = emptyList(),
    val cards: List<AnalyticsFilterOption> = emptyList(),
    val merchants: List<AnalyticsFilterOption> = emptyList(),
)
