package com.kipu.app.feature.analytics.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
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
import com.kipu.app.feature.analytics.domain.AnalyticsOverview
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.BalanceSeriesCalculator
import com.kipu.app.feature.analytics.domain.CurrencyComparison
import com.kipu.app.feature.analytics.domain.PeriodResult
import com.kipu.app.feature.categories.presentation.components.MerchantPickerBottomSheet
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import com.kipu.app.feature.movements.presentation.AccountSelectorBottomSheet
import com.kipu.app.feature.movements.presentation.AccountSelectorDefaults
import com.kipu.app.feature.movements.presentation.AccountSelectorPurpose
import com.kipu.app.feature.movements.presentation.MovementFiltersCatalog
import com.kipu.app.feature.movements.presentation.MovementFiltersCatalogViewModel
import com.kipu.app.feature.movements.presentation.MovementReferenceOption
import com.kipu.app.feature.movements.presentation.PastelCategoryPickerBottomSheet
import com.kipu.app.feature.movements.presentation.ReferenceSelector
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
import com.kipu.app.ui.theme.at
import com.kipu.app.ui.theme.rememberPastelTones
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

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
const val ANALYTICS_PERIOD_TAG = "analytics_period_pill"
const val ANALYTICS_RESTRICTED_TAG = "analytics_restricted_notice"

/** Hilt wrapper; [AnalyticsScreen] stays stateless so UI tests need no injection. */
@Composable
fun AnalyticsRoute(
    onNavigateToPlans: () -> Unit,
    onNavigateToBasicHistory: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    navigation: AnalyticsNavigation = AnalyticsNavigation(),
    viewModel: AnalyticsViewModel = hiltViewModel(),
    catalogViewModel: MovementFiltersCatalogViewModel = hiltViewModel(),
) {
    val catalog by catalogViewModel.state.collectAsStateWithLifecycle()
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
        navigation = navigation,
        catalog = catalog,
    )
}

/** What the money half of the screen draws, whichever state produced it. */
private class ShownData(
    val result: PeriodResult,
    val previous: PeriodResult?,
    val overview: AnalyticsOverview,
    val premium: Boolean,
)

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
    navigation: AnalyticsNavigation = AnalyticsNavigation(),
    catalog: MovementFiltersCatalog? = null,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = rememberCalmEmeraldColors()
    val monthNames = stringArrayResource(R.array.analytics_month_short).toList()
    val inheritedMask = LocalBalanceMasked.current
    var masked by rememberSaveable(inheritedMask) { mutableStateOf(inheritedMask) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showPeriod by rememberSaveable { mutableStateOf(false) }
    var showRange by rememberSaveable { mutableStateOf(false) }
    var restricted by rememberSaveable { mutableStateOf(false) }
    var premiumKnown by rememberSaveable { mutableStateOf(false) }
    var currencyName by rememberSaveable { mutableStateOf<String?>(null) }
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
    LaunchedEffect(state) {
        when (state) {
            is AnalyticsUiState.Content -> premiumKnown = state.premium
            is AnalyticsUiState.Comparison -> premiumKnown = true
            is AnalyticsUiState.PremiumRequired -> premiumKnown = false
            else -> Unit
        }
    }
    val shown: ShownData? = when (state) {
        is AnalyticsUiState.Content -> ShownData(state.result, state.previous, state.overview, state.premium)
        is AnalyticsUiState.PremiumRequired -> ShownData(state.current, null, state.overview, false)
        else -> null
    }
    val currencies = remember(shown?.overview, shown?.result) {
        shown?.let { (it.overview.currencies + it.result.totals.keys + it.result.incomeTotals.keys).distinct().sortedBy(Currency::ordinal) }.orEmpty()
    }
    val currency = currencies.firstOrNull { it.name == currencyName } ?: currencies.firstOrNull() ?: Currency.PEN
    val periodLabel = periodPillLabel(selection, ui, today, monthNames)

    CompositionLocalProvider(LocalBalanceMasked provides masked) {
        Scaffold(
            modifier = modifier.testTag(ANALYTICS_SCREEN_TAG),
            containerColor = colors.background,
            topBar = {
                AnalyticsHeader(
                    periodLabel = periodLabel,
                    periodEnabled = state !is AnalyticsUiState.Locked && state !is AnalyticsUiState.NoOwner,
                    masked = masked,
                    onToggleMask = { masked = !masked },
                    onPeriod = { showPeriod = true },
                    onNavigateBack = onNavigateBack,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                    is AnalyticsUiState.Content, is AnalyticsUiState.PremiumRequired -> {
                        val data = requireNotNull(shown)
                        if (state is AnalyticsUiState.PremiumRequired) {
                            item(key = "premium") {
                                PremiumRequiredCard(
                                    basicHistoryAvailable = state.basicHistoryAvailable,
                                    onPlans = onNavigateToPlans,
                                    onCurrent = onShowCurrentMonth,
                                    onHistory = onNavigateToBasicHistory,
                                )
                            }
                            item(key = "preview_title") { SectionTitle(stringResource(R.string.analytics_premium_preview)) }
                        }
                        item(key = "subline") {
                            PeriodSubline(
                                result = data.result, monthNames = monthNames, filterCount = ui.filters.activeCount(),
                                onOpenFilters = { showFilters = true },
                            )
                        }
                        if (restricted && state is AnalyticsUiState.Content) item(key = "restricted") {
                            RestrictedNotice(onPlans = onNavigateToPlans, onClose = { restricted = false })
                        }
                        analyticsContentItems(
                            data = data, options = options, currencies = currencies, currency = currency,
                            onCurrency = { currencyName = it.name }, today = today, monthNames = monthNames, navigation = navigation,
                            onCompare = { selectMode(AnalyticsMode.COMPARE) }, onPlans = onNavigateToPlans,
                        )
                    }
                    is AnalyticsUiState.Comparison -> {
                        item(key = "compare_back") {
                            TextButton(onClick = onShowCurrentMonth, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.analytics_compare_back), color = colors.primaryDeep, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        item(key = "compare_selectors") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MonthSelector(stringResource(R.string.analytics_previous_period_label), "previous", months, ui.previousMonth, monthNames) { onCompare(month(it), month(ui.currentMonth), ui.filters) }
                                MonthSelector(stringResource(R.string.analytics_current_period_label), "current", months, ui.currentMonth, monthNames) { onCompare(month(ui.previousMonth), month(it), ui.filters) }
                            }
                        }
                        comparisonItems(state.result, monthNames)
                    }
                }
            }
        }
        if (showPeriod) {
            PeriodSheet(
                today = today, ui = ui, months = months, monthNames = monthNames, premium = premiumKnown,
                onDismiss = { showPeriod = false },
                onCurrent = { showPeriod = false; restricted = false; onShowCurrentMonth() },
                onMonth = { ym -> showPeriod = false; if (premiumKnown) onShowReport(month(ym), ui.filters) else restricted = true },
                onRange = { showPeriod = false; if (premiumKnown) showRange = true else restricted = true },
                onCompare = { showPeriod = false; if (premiumKnown) selectMode(AnalyticsMode.COMPARE) else restricted = true },
            )
        }
        if (showRange) {
            RangeDialog(
                today = today,
                onDismiss = { showRange = false },
                onApply = { start, end -> showRange = false; onShowReport(AnalyticsPeriod(start, end, zone), ui.filters) },
            )
        }
        if (showFilters) {
            FilterSheet(
                initial = ui.filters,
                options = options,
                catalog = catalog,
                onDismiss = { showFilters = false },
                onApply = { showFilters = false; applyFilters(it) },
            )
        }
    }
}

@Composable
private fun periodPillLabel(selection: AnalyticsRequest, ui: AnalyticsSelectionUi, today: LocalDate, monthNames: List<String>): String = when (selection) {
    AnalyticsRequest.CurrentMonth -> YearMonth.from(today).shortLabel(monthNames)
    is AnalyticsRequest.Compare -> stringResource(R.string.analytics_mode_compare)
    is AnalyticsRequest.Report -> {
        val period = selection.period
        val wholeMonth = period.start.dayOfMonth == 1 && period.endInclusive == minOf(YearMonth.from(period.start).atEndOfMonth(), today)
        if (wholeMonth) YearMonth.from(period.start).shortLabel(monthNames)
        else stringResource(R.string.analytics_range_label, period.start.dayMonth(monthNames), period.endInclusive.dayMonth(monthNames))
    }
}

@Composable
private fun AnalyticsHeader(
    periodLabel: String,
    periodEnabled: Boolean,
    masked: Boolean,
    onToggleMask: () -> Unit,
    onPeriod: () -> Unit,
    onNavigateBack: (() -> Unit)?,
) {
    val colors = rememberCalmEmeraldColors()
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceCard).statusBarsPadding().padding(start = if (onNavigateBack != null) 4.dp else 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onNavigateBack != null) IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.analytics_back))
        }
        Text(
            stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
            color = colors.primaryText, modifier = Modifier.weight(1f).semantics { heading() },
        )
        val description = stringResource(R.string.analytics_period_pill_desc, periodLabel)
        Row(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(BorderStroke(1.dp, colors.borderSubtle), RoundedCornerShape(22.dp))
                .clickable(enabled = periodEnabled, role = Role.Button, onClick = onPeriod)
                .semantics { contentDescription = description }
                .testTag(ANALYTICS_PERIOD_TAG)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(periodLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.primaryText, modifier = Modifier.clearAndSetSemantics {})
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onToggleMask, modifier = Modifier.testTag(ANALYTICS_MASK_TOGGLE_TAG)) {
            Icon(
                if (masked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = stringResource(if (masked) R.string.analytics_show_amounts else R.string.analytics_hide_amounts),
            )
        }
    }
    HorizontalDivider(color = colors.borderSubtle)
}

@Composable
private fun PeriodSubline(result: PeriodResult, monthNames: List<String>, filterCount: Int, onOpenFilters: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val dates = result.period.datesUi(monthNames, result.partial)
    // Inside one year the year is noise and makes the line wrap next to the tag and the filters.
    val sameYear = result.period.start.year == result.period.endInclusive.year
    val from = if (sameYear) result.period.start.dayMonth(monthNames) else dates.start
    val to = if (sameYear) result.period.endInclusive.dayMonth(monthNames) else dates.end
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (dates.singleDay) stringResource(R.string.analytics_period_single_day, from) else stringResource(R.string.analytics_period_dates, from, to),
            style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, modifier = Modifier.weight(1f, fill = false),
        )
        if (dates.partial) {
            Text(
                stringResource(R.string.analytics_partial_short), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.warningText,
                modifier = Modifier.testTag(ANALYTICS_PARTIAL_TAG).clip(RoundedCornerShape(8.dp)).background(colors.warningBg).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onOpenFilters,
            modifier = Modifier.heightIn(min = 44.dp).testTag(ANALYTICS_FILTERS_TAG),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, if (filterCount > 0) colors.primaryDeep else colors.borderSubtle),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(Icons.Outlined.FilterList, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                if (filterCount > 0) stringResource(R.string.analytics_filters_count, filterCount) else stringResource(R.string.analytics_filters),
                color = colors.primaryDeep, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun RestrictedNotice(onPlans: () -> Unit, onClose: () -> Unit) {
    val tone = rememberPastelTones().at(5)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tone.tile).padding(start = 14.dp, top = 8.dp, bottom = 8.dp).testTag(ANALYTICS_RESTRICTED_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = tone.ink, modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.analytics_restricted_notice), style = MaterialTheme.typography.bodySmall, color = tone.ink)
            Text(
                stringResource(R.string.analytics_restricted_cta), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = tone.ink,
                modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClick = onPlans),
            )
        }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.analytics_close_notice), tint = tone.ink) }
    }
}

/** The money half of Análisis: ring, flows, weeks, categories, daily balance, credit and the call to compare. */
private fun LazyListScope.analyticsContentItems(
    data: ShownData,
    options: AnalyticsFilterOptions,
    currencies: List<Currency>,
    currency: Currency,
    onCurrency: (Currency) -> Unit,
    today: LocalDate,
    monthNames: List<String>,
    navigation: AnalyticsNavigation,
    onCompare: () -> Unit,
    onPlans: () -> Unit,
) {
    val overview = data.overview
    val result = data.result
    val hasAccounts = overview.accounts.isNotEmpty()
    if (!hasAccounts) {
        item(key = "no_accounts") { NoAccountsSection(navigation.onAddAccount) }
    } else {
        item(key = "ring") {
            MoneyRingSection(
                overview = overview, result = result, currencies = currencies, currency = currency, onCurrency = onCurrency,
                asOfLabel = today.dayMonth(monthNames), untilLabel = overview.upcomingUntil?.dayMonth(monthNames), navigation = navigation,
            )
        }
        if (overview.accountsIn(currency).size == 1) item(key = "one_account") { OneAccountBanner(navigation.onAddAccount) }
    }
    if (!hasAccounts) {
        if (!result.hasMovements) item(key = "no_accounts_note") {
            Text(
                stringResource(R.string.analytics_no_accounts_note), style = MaterialTheme.typography.bodySmall,
                color = rememberCalmEmeraldColors().secondaryMuted, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
    }
    if (!result.hasMovements) {
        if (hasAccounts) item(key = "empty_movements") { EmptyMovementsSection() }
    } else {
        val expense = result.totals[currency] ?: Money.zero(currency)
        val income = result.incomeTotals[currency] ?: Money.zero(currency)
        val previousExpense = data.previous?.totals?.get(currency) ?: data.previous?.let { Money.zero(currency) }
        item(key = "kpis") { FlowKpis(income, expense, previousExpense, data.premium, hasPrevious = data.previous != null) }
        item(key = "weeks") { WeeklySection(result.weeks[currency].orEmpty(), monthNames) }
        item(key = "categories") {
            CategoriesSection(
                result = result, previous = data.previous, premium = data.premium, currency = currency, options = options,
                previousLabel = stringResource(R.string.analytics_cats_prev_label), onOpenMovements = navigation.onOpenMovements,
                modifier = Modifier.testTag("${ANALYTICS_CATEGORY_CHART_TAG}_$currency"),
            )
        }
    }
    if (hasAccounts) {
        item(key = "balance") {
            val series = remember(overview, result.period, currency) {
                val ids = overview.accountsIn(currency).map { it.account.id.value }.toSet()
                BalanceSeriesCalculator.series(result.period, overview.total(currency), ids, overview.deltas)
            }
            BalanceLineSection(series, monthNames)
        }
        item(key = "credit") { CreditSection(overview, currency, navigation) }
    }
    if (result.hasMovements) {
        val expense = result.totals[currency] ?: Money.zero(currency)
        val previousExpense = data.previous?.totals?.get(currency)
        item(key = "compare_banner") {
            when {
                !data.premium -> PremiumBanner(onPlans)
                data.previous != null && previousExpense != null && previousExpense.minorUnits > 0L -> CompareBanner(expense, previousExpense, onCompare)
                data.previous != null -> NoBaseBanner()
            }
        }
    }
}

// ---------------------------------------------------------------- periodo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSheet(
    today: LocalDate,
    ui: AnalyticsSelectionUi,
    months: List<YearMonth>,
    monthNames: List<String>,
    premium: Boolean,
    onDismiss: () -> Unit,
    onCurrent: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onRange: () -> Unit,
    onCompare: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    KipuBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            Text(
                stringResource(R.string.analytics_period_sheet_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val thisMonth = months.first()
            PeriodRow(
                title = stringResource(R.string.analytics_period_this_month),
                subtitle = stringResource(R.string.analytics_period_this_month_sub, thisMonth.shortLabel(monthNames), today.dayOfMonth),
                selected = ui.mode == AnalyticsMode.CURRENT, locked = false, onClick = onCurrent,
            )
            months.getOrNull(1)?.let { ym ->
                PeriodRow(
                    title = stringResource(R.string.analytics_period_previous),
                    subtitle = stringResource(R.string.analytics_period_month_sub, ym.shortLabel(monthNames)),
                    selected = ui.mode == AnalyticsMode.REPORT && ui.reportMonth == ym, locked = !premium, onClick = { onMonth(ym) },
                    modifier = Modifier.testTag("${ANALYTICS_MONTH_TAG_PREFIX}report_$ym"),
                )
            }
            PeriodRow(
                title = stringResource(R.string.analytics_period_range), subtitle = stringResource(R.string.analytics_period_range_sub),
                selected = false, locked = !premium, onClick = onRange,
            )
            PeriodRow(
                title = stringResource(R.string.analytics_period_compare), subtitle = stringResource(R.string.analytics_period_compare_sub),
                selected = ui.mode == AnalyticsMode.COMPARE, locked = !premium, onClick = onCompare,
                modifier = Modifier.testTag(ANALYTICS_MODE_TAG_PREFIX + "compare"),
            )
            val older = months.drop(2)
            if (older.isNotEmpty()) {
                Text(
                    stringResource(R.string.analytics_period_older).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp), fontWeight = FontWeight.Bold,
                    color = colors.primaryDeep, modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
                )
                older.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { ym ->
                            MonthTile(
                                label = ym.shortLabel(monthNames), selected = ui.mode == AnalyticsMode.REPORT && ui.reportMonth == ym,
                                locked = !premium, onClick = { onMonth(ym) },
                                modifier = Modifier.weight(1f).testTag("${ANALYTICS_MONTH_TAG_PREFIX}report_$ym"),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MonthTile(label: String, selected: Boolean, locked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val tone = rememberPastelTones().at(0)
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) tone.tile else colors.background)
            .selectable(selected = selected, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (selected) tone.ink else colors.primaryText, maxLines = 1)
        if (locked) {
            Spacer(Modifier.size(4.dp))
            Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.analytics_premium_badge), tint = colors.warningText, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun PeriodRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val colors = rememberCalmEmeraldColors()
    val tone = rememberPastelTones().at(0)
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) tone.tile else tint ?: colors.background)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colors.primaryText)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        }
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = tone.ink)
        if (locked) {
            val badge = rememberPastelTones().at(5)
            Row(
                Modifier.clip(RoundedCornerShape(9.dp)).background(badge.tile).padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = badge.ink, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.analytics_premium_badge), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = badge.ink)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(today: LocalDate, onDismiss: () -> Unit, onApply: (LocalDate, LocalDate) -> Unit) {
    val todayUtc = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    fun date(millis: Long?): LocalDate? = millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val start = date(state.selectedStartDateMillis)
    val end = date(state.selectedEndDateMillis) ?: start
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (start != null && end != null) onApply(start, end) }, enabled = start != null && end != null) {
                Text(stringResource(R.string.analytics_range_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.analytics_range_cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text(stringResource(R.string.analytics_range_title), modifier = Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium) },
            modifier = Modifier.heightIn(max = 560.dp),
        )
    }
}

@Composable
private fun LoadingContent() {
    val label = stringResource(R.string.analytics_loading)
    Column(Modifier.fillMaxWidth().loadingSemantics(label), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 24.dp)
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 380.dp)
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 92.dp)
        KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 190.dp)
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
    val tones = rememberPastelTones()
    AnalyticsCard(Modifier.testTag(ANALYTICS_COMPARISON_TAG_PREFIX + currency.name)) {
        SectionTitle(if (multiCurrency) "${stringResource(R.string.analytics_comparison_title)} · ${currency.label()}" else stringResource(R.string.analytics_comparison_title))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = chartDescription },
        ) {
            LegendBar(stringResource(R.string.analytics_comparison_previous), comparison.previous, previousFraction, tones.at(6).fill)
            LegendBar(stringResource(R.string.analytics_comparison_current), comparison.current, currentFraction, tones.at(0).fill)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    initial: AnalyticsFilters,
    options: AnalyticsFilterOptions,
    catalog: MovementFiltersCatalog?,
    onDismiss: () -> Unit,
    onApply: (AnalyticsFilters) -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    var draft by remember(initial) { mutableStateOf(initial) }
    val active = draft.activeCount()
    val accountOptions = remember(options) { (options.accounts + options.cards).map { MovementReferenceOption(it.id, it.name) } }
    val cardIds = remember(options) { options.cards.map { it.id }.toSet() }
    val categoryOptions = remember(options) { options.categories.map { MovementReferenceOption(it.id, it.name) } }
    val merchantOptions = remember(options) { options.merchants.map { MovementReferenceOption(it.id, it.name) } }
    KipuBottomSheet(
        onDismissRequest = onDismiss,
        header = {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.analytics_filters_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
                if (active > 0) Text(
                    stringResource(R.string.analytics_filters_active, active), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = colors.incomeText,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(colors.incomeBg).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.analytics_range_cancel)) }
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { draft = AnalyticsFilters() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = active > 0) {
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
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.analytics_filters_note), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            FilterGroupTitle(stringResource(R.string.analytics_filter_group_scope))
            val typeLabels = listOf(R.string.analytics_filter_type_all, R.string.analytics_kpi_expenses, R.string.analytics_kpi_income).map { stringResource(it) }
            Segmented(
                labels = typeLabels,
                selectedIndex = when (draft.types) { setOf(MovementType.EXPENSE) -> 1; setOf(MovementType.INCOME) -> 2; else -> 0 },
                description = stringResource(R.string.analytics_filter_type),
                onSelect = { draft = draft.copy(types = when (it) { 1 -> setOf(MovementType.EXPENSE); 2 -> setOf(MovementType.INCOME); else -> emptySet() }) },
            )
            val currencies = listOf<Currency?>(null) + Currency.entries
            val currencyLabels = currencies.map { currency ->
                if (currency == null) stringResource(R.string.analytics_filter_all_currencies)
                else stringResource(if (currency == Currency.PEN) R.string.analytics_currency_pen else R.string.analytics_currency_usd)
            }
            Segmented(
                labels = currencyLabels, selectedIndex = currencies.indexOf(draft.currency).coerceAtLeast(0),
                description = stringResource(R.string.analytics_filter_currency), onSelect = { draft = draft.copy(currency = currencies[it]) },
            )
            Spacer(Modifier.height(2.dp))
            FilterGroupTitle(stringResource(R.string.analytics_filter_group_by))
            ReferenceSelector(
                title = stringResource(R.string.analytics_filter_account_card), icon = Icons.Rounded.AccountBalanceWallet,
                emptySummary = stringResource(R.string.analytics_filter_all_f), options = accountOptions,
                selected = draft.accountIds + draft.cardIds, enabled = true,
                onChange = { ids -> draft = draft.copy(accountIds = ids.filter { it !in cardIds }.toSet(), cardIds = ids.filter { it in cardIds }.toSet()) },
                selectorContent = catalog?.let { data -> { dismiss ->
                    AccountSelectorBottomSheet(
                        accounts = data.accounts, creditCards = data.cards, purpose = AccountSelectorPurpose.FILTER,
                        title = AccountSelectorDefaults.title(AccountSelectorPurpose.FILTER),
                        subtitle = AccountSelectorDefaults.subtitle(AccountSelectorPurpose.FILTER),
                        selectedAccountIds = draft.accountIds, selectedCardIds = draft.cardIds,
                        onSelectionConfirmed = { accountIds, selectedCards -> draft = draft.copy(accountIds = accountIds, cardIds = selectedCards) },
                        onDismissRequest = dismiss,
                    )
                } },
            )
            ReferenceSelector(
                title = stringResource(R.string.analytics_filter_category), icon = Icons.Rounded.Category,
                emptySummary = stringResource(R.string.analytics_filter_all_f), options = categoryOptions,
                selected = draft.categoryIds, enabled = true, onChange = { draft = draft.copy(categoryIds = it) },
                selectorContent = catalog?.let { data -> { dismiss ->
                    PastelCategoryPickerBottomSheet(
                        categories = data.categories, selectedCategoryId = null, onSelect = {},
                        onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                        onDismiss = dismiss, title = stringResource(R.string.analytics_filter_pick_category),
                        subtitle = stringResource(R.string.analytics_filter_pick_many),
                        allowCreate = false, multiSelect = true, selectedCategoryIds = draft.categoryIds,
                        onConfirmMultiple = { draft = draft.copy(categoryIds = it) },
                    )
                } },
            )
            ReferenceSelector(
                title = stringResource(R.string.analytics_filter_merchant), icon = Icons.Rounded.Storefront,
                emptySummary = stringResource(R.string.analytics_filter_all_m), options = merchantOptions,
                selected = draft.merchantIds, enabled = true, onChange = { draft = draft.copy(merchantIds = it) },
                selectorContent = catalog?.let { data -> { dismiss ->
                    var query by remember { mutableStateOf("") }
                    MerchantPickerBottomSheet(
                        state = MerchantPickerState(
                            query = query, catalogEntries = data.merchants,
                            searchResults = data.merchants.filter { it.name.contains(query.trim(), ignoreCase = true) },
                        ),
                        onQueryChange = { query = it },
                        onSelectMerchant = { entry -> draft = draft.copy(merchantIds = draft.merchantIds.toggle(entry.id.value)) },
                        onSetProvisionalText = {}, onClearSelection = { draft = draft.copy(merchantIds = emptySet()) },
                        onDismiss = dismiss, title = stringResource(R.string.analytics_filter_pick_merchant),
                        subtitle = stringResource(R.string.analytics_filter_pick_many),
                        allowProvisionalText = false, selectedMerchantIds = draft.merchantIds, dismissOnSelect = false,
                    )
                } },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FilterGroupTitle(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp), fontWeight = FontWeight.Bold,
        color = rememberCalmEmeraldColors().primaryDeep, modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun Currency.label(): String = stringResource(
    when (this) {
        Currency.PEN -> R.string.analytics_currency_pen
        Currency.USD -> R.string.analytics_currency_usd
    },
)
