package com.kipu.app.feature.analytics.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.analytics.domain.AnalyticsComparison
import com.kipu.app.feature.analytics.domain.AnalyticsFilterOption
import com.kipu.app.feature.analytics.domain.AnalyticsFilterOptions
import com.kipu.app.feature.analytics.domain.AnalyticsFilters
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.CurrencyComparison
import com.kipu.app.feature.analytics.domain.PeriodResult
import com.kipu.app.feature.home.domain.NetWorthSummary
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuEmptyState
import com.kipu.app.ui.component.KipuErrorState
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.KipuSkeletonBlock
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.loadingSemantics
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

const val ANALYTICS_SCREEN_TAG = "analytics_screen"
const val ANALYTICS_MODE_TAG_PREFIX = "analytics_mode_"
const val ANALYTICS_FILTERS_TAG = "analytics_filters"
const val ANALYTICS_FILTER_APPLY_TAG = "analytics_filter_apply"
const val ANALYTICS_PARTIAL_TAG = "analytics_partial_warning"
const val ANALYTICS_NO_BASE_TAG = "analytics_no_base"
const val ANALYTICS_PREMIUM_TAG = "analytics_premium_required"
const val ANALYTICS_PREMIUM_PLANS_TAG = "analytics_premium_plans"
const val ANALYTICS_PREMIUM_CURRENT_TAG = "analytics_premium_current"
const val ANALYTICS_PREMIUM_HISTORY_TAG = "analytics_premium_history"
const val ANALYTICS_CATEGORY_CHART_TAG = "analytics_category_chart"
const val ANALYTICS_COMPARISON_TAG_PREFIX = "analytics_comparison_"
const val ANALYTICS_MASK_TOGGLE_TAG = "analytics_mask_toggle"
const val ANALYTICS_MONTH_TAG_PREFIX = "analytics_month_"

/** Hilt wrapper; [AnalyticsScreen] stays stateless so UI tests need no injection. */
@Composable
fun AnalyticsRoute(
    onNavigateToPlans: () -> Unit,
    onNavigateToBasicHistory: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val options by viewModel.filterOptions.collectAsStateWithLifecycle()
    AnalyticsScreen(
        state = state,
        selection = selection,
        options = options,
        onShowCurrentMonth = viewModel::showCurrentMonth,
        onShowReport = viewModel::showReport,
        onCompare = viewModel::compare,
        onRetry = viewModel::retry,
        onNavigateToPlans = onNavigateToPlans,
        onNavigateToBasicHistory = onNavigateToBasicHistory,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    state: AnalyticsUiState,
    selection: AnalyticsRequest,
    options: AnalyticsFilterOptions,
    onShowCurrentMonth: () -> Unit,
    onShowReport: (AnalyticsPeriod, AnalyticsFilters) -> Unit,
    onCompare: (AnalyticsPeriod, AnalyticsPeriod, AnalyticsFilters) -> Unit,
    onRetry: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToBasicHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = rememberCalmEmeraldColors()
    val monthNames = stringArrayResource(R.array.analytics_month_short).toList()
    val inheritedMask = LocalBalanceMasked.current
    var masked by rememberSaveable(inheritedMask) { mutableStateOf(inheritedMask) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val ui = remember(selection, today) { selection.toSelectionUi(today) }
    val months = remember(today) { selectableMonths(today) }
    fun month(ym: YearMonth) = AnalyticsPeriod.month(ym, today, zone)
    val selectMode: (AnalyticsMode) -> Unit = { mode ->
        when (mode) {
            AnalyticsMode.CURRENT -> onShowCurrentMonth()
            AnalyticsMode.REPORT -> onShowReport(month(ui.reportMonth), ui.filters)
            AnalyticsMode.COMPARE -> onCompare(month(ui.previousMonth), month(ui.currentMonth), ui.filters)
        }
    }
    val applyFilters: (AnalyticsFilters) -> Unit = { filters ->
        when (ui.mode) {
            // Filters are a Premium depth: on the basic month they become a report of that month.
            AnalyticsMode.CURRENT -> if (filters == AnalyticsFilters()) onShowCurrentMonth()
                else onShowReport(AnalyticsPeriod.currentMonth(today, zone), filters)
            AnalyticsMode.REPORT -> onShowReport(month(ui.reportMonth), filters)
            AnalyticsMode.COMPARE -> onCompare(month(ui.previousMonth), month(ui.currentMonth), filters)
        }
    }

    CompositionLocalProvider(LocalBalanceMasked provides masked) {
        Scaffold(
            modifier = modifier.testTag(ANALYTICS_SCREEN_TAG),
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                    title = {
                        Text(stringResource(R.string.analytics_title), fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    },
                    navigationIcon = {
                        if (onNavigateBack != null) IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.analytics_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { masked = !masked }, modifier = Modifier.testTag(ANALYTICS_MASK_TOGGLE_TAG)) {
                            Icon(
                                if (masked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(if (masked) R.string.analytics_show_amounts else R.string.analytics_hide_amounts),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val controlsEnabled = state !is AnalyticsUiState.Locked && state !is AnalyticsUiState.NoOwner
                if (controlsEnabled) {
                    item(key = "controls") {
                        AnalyticsControls(
                            ui = ui, months = months, monthNames = monthNames,
                            onMode = selectMode,
                            onReportMonth = { onShowReport(month(it), ui.filters) },
                            onPreviousMonth = { onCompare(month(it), month(ui.currentMonth), ui.filters) },
                            onCurrentMonth = { onCompare(month(ui.previousMonth), month(it), ui.filters) },
                            onOpenFilters = { showFilters = true },
                        )
                    }
                }
                when (state) {
                    AnalyticsUiState.Loading -> item(key = "loading") { LoadingContent() }
                    AnalyticsUiState.Locked -> item(key = "locked") {
                        AccessNotice(Icons.Outlined.Lock, stringResource(R.string.analytics_locked_title), stringResource(R.string.analytics_locked_body))
                    }
                    AnalyticsUiState.NoOwner -> item(key = "no_owner") {
                        AccessNotice(Icons.Outlined.Person, stringResource(R.string.analytics_no_owner_title), stringResource(R.string.analytics_no_owner_body))
                    }
                    is AnalyticsUiState.Error -> item(key = "error") {
                        KipuErrorState(
                            title = stringResource(R.string.analytics_error_title),
                            message = state.message,
                            onRetry = onRetry,
                            retryLabel = stringResource(R.string.analytics_retry),
                        )
                    }
                    is AnalyticsUiState.Content -> {
                        item(key = "period") { PeriodCard(stringResource(R.string.analytics_period_label), state.result, monthNames) }
                        periodResultItems(state.result, options, monthNames)
                        item(key = "net_worth") { NetWorthSection(state.netWorth) }
                    }
                    is AnalyticsUiState.Comparison -> {
                        comparisonItems(state.result, monthNames)
                        item(key = "net_worth") { NetWorthSection(state.netWorth) }
                    }
                    is AnalyticsUiState.PremiumRequired -> {
                        item(key = "premium") {
                            PremiumRequiredCard(
                                basicHistoryAvailable = state.basicHistoryAvailable,
                                onPlans = onNavigateToPlans,
                                onCurrent = onShowCurrentMonth,
                                onHistory = onNavigateToBasicHistory,
                            )
                        }
                        item(key = "preview_title") { SectionTitle(stringResource(R.string.analytics_premium_preview)) }
                        item(key = "period") { PeriodCard(stringResource(R.string.analytics_mode_current), state.current, monthNames) }
                        periodResultItems(state.current, options, monthNames)
                        item(key = "net_worth") { NetWorthSection(state.netWorth) }
                    }
                }
            }
        }
        if (showFilters) {
            FilterSheet(
                initial = ui.filters,
                options = options,
                onDismiss = { showFilters = false },
                onApply = { showFilters = false; applyFilters(it) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalyticsControls(
    ui: AnalyticsSelectionUi,
    months: List<YearMonth>,
    monthNames: List<String>,
    onMode: (AnalyticsMode) -> Unit,
    onReportMonth: (YearMonth) -> Unit,
    onPreviousMonth: (YearMonth) -> Unit,
    onCurrentMonth: (YearMonth) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val modeLabel = stringResource(R.string.analytics_mode_label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { contentDescription = modeLabel },
        ) {
            AnalyticsMode.entries.forEach { mode ->
                KipuFilterChip(
                    selected = ui.mode == mode,
                    onClick = { if (ui.mode != mode) onMode(mode) },
                    label = {
                        Text(stringResource(when (mode) {
                            AnalyticsMode.CURRENT -> R.string.analytics_mode_current
                            AnalyticsMode.REPORT -> R.string.analytics_mode_report
                            AnalyticsMode.COMPARE -> R.string.analytics_mode_compare
                        }))
                    },
                    trailingIcon = if (mode != AnalyticsMode.CURRENT) {
                        { Icon(Icons.Outlined.WorkspacePremium, contentDescription = stringResource(R.string.analytics_premium_badge), modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.testTag(ANALYTICS_MODE_TAG_PREFIX + mode.name.lowercase()),
                )
            }
        }
        when (ui.mode) {
            AnalyticsMode.CURRENT -> Unit
            AnalyticsMode.REPORT -> MonthSelector(stringResource(R.string.analytics_period_label), "report", months, ui.reportMonth, monthNames, onReportMonth)
            AnalyticsMode.COMPARE -> {
                MonthSelector(stringResource(R.string.analytics_previous_period_label), "previous", months, ui.previousMonth, monthNames, onPreviousMonth)
                MonthSelector(stringResource(R.string.analytics_current_period_label), "current", months, ui.currentMonth, monthNames, onCurrentMonth)
            }
        }
        val count = ui.filters.activeCount()
        OutlinedButton(
            onClick = onOpenFilters,
            modifier = Modifier.heightIn(min = 48.dp).testTag(ANALYTICS_FILTERS_TAG),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (count > 0) colors.primaryDeep else colors.borderSubtle),
        ) {
            Icon(Icons.Outlined.FilterList, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                if (count > 0) stringResource(R.string.analytics_filters_count, count) else stringResource(R.string.analytics_filters),
                color = colors.primaryDeep, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MonthSelector(label: String, key: String, months: List<YearMonth>, selected: YearMonth, monthNames: List<String>, onSelect: (YearMonth) -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.secondaryMuted, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(months, key = { it.toString() }) { ym ->
                KipuFilterChip(
                    selected = ym == selected,
                    onClick = { if (ym != selected) onSelect(ym) },
                    label = { Text(ym.shortLabel(monthNames)) },
                    modifier = Modifier.testTag("$ANALYTICS_MONTH_TAG_PREFIX${key}_$ym"),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = rememberCalmEmeraldColors().primaryText,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}

@Composable
private fun AnalyticsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceCard,
        shadowElevation = 1.dp,
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

/** Exact dates plus a textual open/closed tag, so the state never depends on color. */
@Composable
private fun PeriodCard(label: String, result: PeriodResult, monthNames: List<String>) {
    val colors = rememberCalmEmeraldColors()
    val dates = result.period.datesUi(monthNames, result.partial)
    AnalyticsCard(Modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.secondaryMuted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            PeriodTag(dates.partial)
        }
        Text(
            if (dates.singleDay) stringResource(R.string.analytics_period_single_day, dates.start) else stringResource(R.string.analytics_period_dates, dates.start, dates.end),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
        )
        if (dates.partial) PartialWarning(dates.end)
    }
}

@Composable
private fun PeriodTag(partial: Boolean) {
    val colors = rememberCalmEmeraldColors()
    Text(
        stringResource(if (partial) R.string.analytics_partial_chip else R.string.analytics_closed_chip),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (partial) colors.warningText else colors.primaryDeep,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (partial) colors.warningBg else colors.incomeBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun PartialWarning(endDate: String) {
    val colors = rememberCalmEmeraldColors()
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.warningBg)
            .padding(horizontal = 12.dp, vertical = 8.dp).testTag(ANALYTICS_PARTIAL_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.warningText, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.analytics_partial_warning, endDate), style = MaterialTheme.typography.bodySmall, color = colors.warningText)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.periodResultItems(
    result: PeriodResult,
    options: AnalyticsFilterOptions,
    monthNames: List<String>,
) {
    if (!result.hasRecords) {
        item(key = "empty") {
            KipuEmptyState(
                title = stringResource(R.string.analytics_empty_title),
                message = stringResource(R.string.analytics_empty_body),
            )
        }
        return
    }
    val names = options.categories.associate { it.id to it.name }
    result.totals.keys.ordered().forEach { currency ->
        item(key = "total_$currency") { TotalCard(currency, result.totals.getValue(currency), multiCurrency = result.totals.size > 1) }
        val shares = result.categoryShares(currency, names)
        if (shares.isNotEmpty()) item(key = "categories_$currency") { CategoryBreakdown(currency, shares) }
    }
}

@Composable
private fun TotalCard(currency: Currency, total: Money, multiCurrency: Boolean) {
    val colors = rememberCalmEmeraldColors()
    AnalyticsCard(Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            if (multiCurrency) "${stringResource(R.string.analytics_operating_total)} · ${currency.label()}" else stringResource(R.string.analytics_operating_total),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Bold,
            color = colors.secondaryMuted,
        )
        MoneyText(
            money = total,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            color = colors.primaryText,
        )
        Text(stringResource(R.string.analytics_operating_note), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
    }
}

@Composable
private fun CategoryBreakdown(currency: Currency, shares: List<CategoryShareUi>) {
    val colors = rememberCalmEmeraldColors()
    val masked = LocalBalanceMasked.current
    val chartDescription = stringResource(R.string.analytics_category_chart_description, currency.label(), shares.size)
    val uncategorized = stringResource(R.string.analytics_uncategorized)
    val unknown = stringResource(R.string.analytics_unknown_category)
    val hidden = stringResource(R.string.analytics_masked_amount)
    AnalyticsCard(Modifier.testTag("${ANALYTICS_CATEGORY_CHART_TAG}_$currency").semantics { contentDescription = chartDescription }) {
        SectionTitle(stringResource(R.string.analytics_by_category))
        shares.forEach { share ->
            val name = share.name ?: if (share.categoryId == null) uncategorized else unknown
            val amountText = if (masked) hidden else "${currency.symbol()} ${formatMinorUnits(share.amount.minorUnits)}"
            val rowDescription = stringResource(R.string.analytics_category_row_description, name, amountText, share.percentText)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = rowDescription },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.primaryText, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.analytics_category_share, share.percentText), style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                    MoneyText(money = share.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
                }
                HorizontalBar(fraction = share.share, color = colors.primaryDeep, track = colors.pillTrack)
            }
        }
    }
}

@Composable
private fun HorizontalBar(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) drawRoundRect(color = color, topLeft = Offset.Zero, size = Size(maxOf(width, size.height), size.height), cornerRadius = radius)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.comparisonItems(result: AnalyticsComparison, monthNames: List<String>) {
    item(key = "previous_period") { PeriodCard(labelOf(R.string.analytics_comparison_previous), result.previous, monthNames) }
    item(key = "current_period") { PeriodCard(labelOf(R.string.analytics_comparison_current), result.current, monthNames) }
    if (!result.previous.hasRecords && !result.current.hasRecords) {
        item(key = "comparison_empty") {
            KipuEmptyState(title = stringResource(R.string.analytics_empty_title), message = stringResource(R.string.analytics_comparison_empty))
        }
        return
    }
    result.byCurrency.keys.ordered().forEach { currency ->
        item(key = "comparison_$currency") { ComparisonCard(currency, result.byCurrency.getValue(currency), multiCurrency = result.byCurrency.size > 1) }
    }
}

/** Resource label resolved inside a composable scope; keeps LazyList builders free of string literals. */
@Composable
private fun labelOf(id: Int): String = stringResource(id)

@Composable
private fun ComparisonCard(currency: Currency, comparison: CurrencyComparison, multiCurrency: Boolean) {
    val colors = rememberCalmEmeraldColors()
    val masked = LocalBalanceMasked.current
    val hidden = stringResource(R.string.analytics_masked_amount)
    fun text(money: Money) = if (masked) hidden else "${currency.symbol()} ${formatMinorUnits(money.minorUnits)}"
    val chartDescription = stringResource(R.string.analytics_comparison_chart_description, currency.label(), text(comparison.previous), text(comparison.current))
    val (previousFraction, currentFraction) = comparisonFractions(comparison.previous, comparison.current)
    AnalyticsCard(Modifier.testTag(ANALYTICS_COMPARISON_TAG_PREFIX + currency.name)) {
        SectionTitle(if (multiCurrency) "${stringResource(R.string.analytics_comparison_title)} · ${currency.label()}" else stringResource(R.string.analytics_comparison_title))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = chartDescription },
        ) {
            LegendBar(stringResource(R.string.analytics_comparison_previous), comparison.previous, previousFraction, colors.transferBlue)
            LegendBar(stringResource(R.string.analytics_comparison_current), comparison.current, currentFraction, colors.primaryDeep)
        }
        HorizontalDivider(color = colors.borderSubtle)
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.analytics_comparison_difference), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, modifier = Modifier.weight(1f))
            MoneyText(
                amount = comparison.difference.signedAmount(),
                currencySymbol = currency.symbol(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (comparison.difference.minorUnits > 0L) colors.expenseText else colors.primaryText,
            )
        }
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.analytics_comparison_percentage), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, modifier = Modifier.weight(1f))
            when (val label = comparison.percentage.toLabel()) {
                PercentLabel.NoBase -> Text(
                    stringResource(R.string.analytics_no_base),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.warningText,
                    modifier = Modifier.testTag(ANALYTICS_NO_BASE_TAG + "_" + currency.name),
                )
                is PercentLabel.Text -> Text(label.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colors.primaryText)
            }
        }
        if (comparison.percentage.toLabel() == PercentLabel.NoBase) {
            Text(stringResource(R.string.analytics_no_base_note), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        }
    }
}

@Composable
private fun LegendBar(label: String, money: Money, fraction: Float, color: Color) {
    val colors = rememberCalmEmeraldColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, modifier = Modifier.weight(1f))
            MoneyText(money = money, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
        }
        HorizontalBar(fraction = fraction, color = color, track = colors.pillTrack)
    }
}

@Composable
private fun PremiumRequiredCard(basicHistoryAvailable: Boolean, onPlans: () -> Unit, onCurrent: () -> Unit, onHistory: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(ANALYTICS_PREMIUM_TAG).semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = colors.incomeBg,
        border = BorderStroke(1.dp, colors.incomeBorder),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.analytics_premium_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.primaryText, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.analytics_premium_body), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
                }
            }
            Button(
                onClick = onPlans,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(ANALYTICS_PREMIUM_PLANS_TAG),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
            ) { Text(stringResource(R.string.analytics_premium_cta), fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onCurrent,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(ANALYTICS_PREMIUM_CURRENT_TAG),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(R.string.analytics_premium_current), color = colors.primaryDeep, fontWeight = FontWeight.SemiBold) }
            if (basicHistoryAvailable) TextButton(
                onClick = onHistory,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(ANALYTICS_PREMIUM_HISTORY_TAG),
            ) { Text(stringResource(R.string.analytics_premium_history), color = colors.primaryDeep, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Reservations and available credit are never added: only the canonical net-worth components are shown. */
@Composable
private fun NetWorthSection(summary: NetWorthSummary) {
    val colors = rememberCalmEmeraldColors()
    AnalyticsCard {
        SectionTitle(stringResource(R.string.analytics_net_worth_title))
        when (summary) {
            NetWorthSummary.NoData -> Text(stringResource(R.string.analytics_net_worth_empty), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
            is NetWorthSummary.Available -> summary.byCurrency.forEach { item ->
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (summary.byCurrency.size > 1) Text(item.currency.label(), style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                    NetWorthLine(stringResource(R.string.analytics_net_worth_assets), item.realAssets)
                    NetWorthLine(stringResource(R.string.analytics_net_worth_receivables), item.receivables)
                    NetWorthLine(stringResource(R.string.analytics_net_worth_liabilities), item.liabilities)
                    NetWorthLine(stringResource(R.string.analytics_net_worth_total), item.netWorth, emphasized = true)
                }
            }
        }
        Text(stringResource(R.string.analytics_net_worth_note), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
    }
}

@Composable
private fun NetWorthLine(label: String, money: Money, emphasized: Boolean = false) {
    val colors = rememberCalmEmeraldColors()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (emphasized) colors.primaryText else colors.secondaryMuted, fontWeight = if (emphasized) FontWeight.Bold else null, modifier = Modifier.weight(1f))
        MoneyText(
            money = money,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasized && money.minorUnits < 0L) colors.expenseText else colors.primaryText,
        )
    }
}

@Composable
private fun LoadingContent() {
    val label = stringResource(R.string.analytics_loading)
    Column(Modifier.fillMaxWidth().loadingSemantics(label), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 72.dp)
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 96.dp)
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 160.dp)
    }
}

@Composable
private fun AccessNotice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    val colors = rememberCalmEmeraldColors()
    KipuEmptyState(
        title = title,
        message = message,
        modifier = Modifier.padding(top = 32.dp).semantics(mergeDescendants = true) {},
        icon = {
            Surface(shape = CircleShape, color = colors.incomeBg, modifier = Modifier.size(72.dp)) {
                Icon(icon, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.padding(18.dp))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    initial: AnalyticsFilters,
    options: AnalyticsFilterOptions,
    onDismiss: () -> Unit,
    onApply: (AnalyticsFilters) -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    var draft by remember(initial) { mutableStateOf(initial) }
    KipuBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            Text(
                stringResource(R.string.analytics_filters_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
            )
        },
        footer = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { draft = AnalyticsFilters() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.analytics_filter_clear), color = colors.primaryDeep)
                }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(ANALYTICS_FILTER_APPLY_TAG),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
                ) { Text(stringResource(R.string.analytics_filter_apply), fontWeight = FontWeight.SemiBold) }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.analytics_filters_note), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            OptionGroup(stringResource(R.string.analytics_filter_category), options.categories, draft.categoryIds) { draft = draft.copy(categoryIds = it) }
            OptionGroup(stringResource(R.string.analytics_filter_account), options.accounts, draft.accountIds) { draft = draft.copy(accountIds = it) }
            OptionGroup(stringResource(R.string.analytics_filter_card), options.cards, draft.cardIds) { draft = draft.copy(cardIds = it) }
            OptionGroup(stringResource(R.string.analytics_filter_merchant), options.merchants, draft.merchantIds) { draft = draft.copy(merchantIds = it) }
            ChipGroup(stringResource(R.string.analytics_filter_type)) {
                listOf(MovementType.EXPENSE).forEach { type ->
                    KipuFilterChip(
                        selected = type in draft.types,
                        onClick = { draft = draft.copy(types = draft.types.toggle(type)) },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
            ChipGroup(stringResource(R.string.analytics_filter_currency)) {
                KipuFilterChip(selected = draft.currency == null, onClick = { draft = draft.copy(currency = null) }, label = { Text(stringResource(R.string.analytics_filter_all_currencies)) })
                Currency.entries.forEach { currency ->
                    KipuFilterChip(selected = draft.currency == currency, onClick = { draft = draft.copy(currency = currency) }, label = { Text(currency.label()) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = rememberCalmEmeraldColors().primaryText, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun OptionGroup(title: String, options: List<AnalyticsFilterOption>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    ChipGroup(title) {
        if (options.isEmpty()) {
            Text(stringResource(R.string.analytics_filter_empty), style = MaterialTheme.typography.bodySmall, color = rememberCalmEmeraldColors().secondaryMuted)
        }
        options.forEach { option ->
            KipuFilterChip(selected = option.id in selected, onClick = { onChange(selected.toggle(option.id)) }, label = { Text(option.name) })
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private fun MovementType.labelRes(): Int = when (this) {
    MovementType.EXPENSE -> R.string.analytics_type_expense
    MovementType.INCOME -> R.string.analytics_type_income
    MovementType.TRANSFER -> R.string.analytics_type_transfer
}

@Composable
private fun Currency.label(): String = stringResource(
    when (this) {
        Currency.PEN -> R.string.analytics_currency_pen
        Currency.USD -> R.string.analytics_currency_usd
    },
)
