package com.kipu.app.feature.analytics.presentation

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.analytics.domain.AnalyticsFilters
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.PercentageChange
import com.kipu.app.feature.analytics.domain.PeriodResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Which control group the screen shows; derived from the ViewModel request, never stored twice. */
enum class AnalyticsMode { CURRENT, REPORT, COMPARE }

/** Selector state derived from [AnalyticsRequest]; months are the only periods the UI offers. */
data class AnalyticsSelectionUi(
    val mode: AnalyticsMode,
    val reportMonth: YearMonth,
    val previousMonth: YearMonth,
    val currentMonth: YearMonth,
    val filters: AnalyticsFilters,
)

fun AnalyticsRequest.toSelectionUi(today: LocalDate): AnalyticsSelectionUi {
    val thisMonth = YearMonth.from(today)
    val lastMonth = thisMonth.minusMonths(1)
    return when (this) {
        AnalyticsRequest.CurrentMonth -> AnalyticsSelectionUi(AnalyticsMode.CURRENT, lastMonth, lastMonth, thisMonth, AnalyticsFilters())
        is AnalyticsRequest.Report -> AnalyticsSelectionUi(
            AnalyticsMode.REPORT, YearMonth.from(period.start), lastMonth, thisMonth, filters,
        )
        is AnalyticsRequest.Compare -> AnalyticsSelectionUi(
            AnalyticsMode.COMPARE, lastMonth, YearMonth.from(previous.start), YearMonth.from(current.start), filters,
        )
    }
}

/** Last [count] months, newest first, never in the future. */
fun selectableMonths(today: LocalDate, count: Int = 12): List<YearMonth> =
    (0 until count).map { YearMonth.from(today).minusMonths(it.toLong()) }

fun AnalyticsFilters.activeCount(): Int =
    categoryIds.size + accountIds.size + cardIds.size + merchantIds.size + types.size + if (currency != null) 1 else 0

/** Percentage label; a zero base is explicit and never rendered as infinity or NaN. */
sealed interface PercentLabel {
    data object NoBase : PercentLabel
    data class Text(val value: String) : PercentLabel
}

private val percentFormat get() = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

fun PercentageChange.toLabel(): PercentLabel = when (this) {
    PercentageChange.SinBaseDeComparacion -> PercentLabel.NoBase
    is PercentageChange.Value -> {
        val rounded = percent.setScale(2, RoundingMode.HALF_UP)
        val sign = when (rounded.signum()) { 1 -> "+"; -1 -> "−"; else -> "" }
        PercentLabel.Text("$sign${percentFormat.format(rounded.abs())} %")
    }
}

/** Signed difference amount text without currency, e.g. "+12.50" or "-3.00", for MoneyText. */
fun Money.signedAmount(): String = when {
    minorUnits > 0L -> "+" + com.kipu.app.ui.component.formatMinorUnits(minorUnits)
    else -> com.kipu.app.ui.component.formatMinorUnits(minorUnits)
}

/** Exact date with deterministic Spanish month abbreviations taken from resources. */
fun LocalDate.shortLabel(monthNames: List<String>): String = "$dayOfMonth ${monthNames[monthValue - 1]} $year"

fun YearMonth.shortLabel(monthNames: List<String>): String = "${monthNames[monthValue - 1].replaceFirstChar { it.titlecase(Locale.ROOT) }} $year"

data class PeriodDatesUi(val start: String, val end: String, val singleDay: Boolean, val partial: Boolean)

fun AnalyticsPeriod.datesUi(monthNames: List<String>, partial: Boolean) = PeriodDatesUi(
    start = start.shortLabel(monthNames),
    end = endInclusive.shortLabel(monthNames),
    singleDay = start == endInclusive,
    partial = partial,
)

/** One bar of the category breakdown; [share] is 0..1 of the currency total. */
data class CategoryShareUi(val categoryId: String?, val name: String?, val amount: Money, val share: Float, val percentText: String)

fun PeriodResult.categoryShares(currency: Currency, names: Map<String, String>): List<CategoryShareUi> {
    val entries = byCategory[currency].orEmpty()
    val total = entries.values.sumOf { it.minorUnits }
    if (total <= 0L) return emptyList()
    return entries.entries.sortedByDescending { it.value.minorUnits }.map { (id, money) ->
        val percent = BigDecimal.valueOf(money.minorUnits).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
        CategoryShareUi(
            categoryId = id,
            name = id?.let(names::get),
            amount = money,
            share = (money.minorUnits.toDouble() / total).toFloat().coerceIn(0f, 1f),
            percentText = DecimalFormat("0.#", DecimalFormatSymbols(Locale.US)).format(percent),
        )
    }
}

/** Bar fractions for a two-period comparison; both zero means no bars, never a division by zero. */
fun comparisonFractions(previous: Money, current: Money): Pair<Float, Float> {
    val max = maxOf(previous.minorUnits, current.minorUnits)
    if (max <= 0L) return 0f to 0f
    return (previous.minorUnits.coerceAtLeast(0).toFloat() / max) to (current.minorUnits.coerceAtLeast(0).toFloat() / max)
}

/** Currencies shown in a stable order (PEN, then USD); a single result is never converted. */
fun Collection<Currency>.ordered(): List<Currency> = sortedBy { it.ordinal }
