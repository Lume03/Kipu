package com.kipu.app.feature.analytics.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.analytics.domain.AnalyticsCalculator
import com.kipu.app.feature.analytics.domain.AnalyticsFilterOption
import com.kipu.app.feature.analytics.domain.AnalyticsFilterOptions
import com.kipu.app.feature.analytics.domain.AnalyticsFilters
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.ExpenseRow
import com.kipu.app.feature.home.domain.NetWorthSummary
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AnalyticsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("America/Lima")
    private val today = LocalDate.of(2026, 11, 3)
    private val currentPeriod = AnalyticsPeriod.currentMonth(today, zone)
    private val previousPeriod = AnalyticsPeriod.month(YearMonth.of(2026, 10), today, zone)
    private val options = AnalyticsFilterOptions(categories = listOf(AnalyticsFilterOption("food", "Comida")))

    private fun result(period: AnalyticsPeriod, vararg amounts: Long) = AnalyticsCalculator.calculate(
        period, today, AnalyticsFilters(), amounts.map { ExpenseRow(it, 0, "PEN", "food", null, null, null) },
    )

    private fun show(
        state: AnalyticsUiState,
        selection: AnalyticsRequest = AnalyticsRequest.CurrentMonth,
        onCurrent: () -> Unit = {}, onPlans: () -> Unit = {}, onHistory: () -> Unit = {}, onRetry: () -> Unit = {},
        onReport: (AnalyticsPeriod, AnalyticsFilters) -> Unit = { _, _ -> },
    ) = compose.setContent {
        KipuTheme {
            AnalyticsScreen(
                state = state, selection = selection, options = options,
                onShowCurrentMonth = onCurrent, onShowReport = onReport, onCompare = { _, _, _ -> },
                onRetry = onRetry, onNavigateToPlans = onPlans, onNavigateToBasicHistory = onHistory,
                today = today, zone = zone,
            )
        }
    }

    @Test fun currentMonthShowsTotalCategoryAndPartialDates() {
        show(AnalyticsUiState.Content(result(currentPeriod, 10_000), NetWorthSummary.NoData))
        compose.onNodeWithTag(ANALYTICS_PARTIAL_TAG).assertIsDisplayed()
        compose.onNodeWithText("Del 1 nov 2026 al 3 nov 2026").assertIsDisplayed()
        compose.onNodeWithTag("${ANALYTICS_CATEGORY_CHART_TAG}_PEN").assertExists()
    }

    @Test fun zeroBaseComparisonShowsSinBase() {
        val comparison = AnalyticsCalculator.compare(result(previousPeriod), result(currentPeriod, 5_000))
        show(AnalyticsUiState.Comparison(comparison, NetWorthSummary.NoData), AnalyticsRequest.Compare(previousPeriod, currentPeriod))
        compose.onNode(hasTestTag(ANALYTICS_SCREEN_TAG)).assertExists()
        compose.onAllNodesWithText("Del 1 oct 2026 al 31 oct 2026")[0].assertExists()
        compose.onNodeWithTag("${ANALYTICS_NO_BASE_TAG}_${Currency.PEN.name}", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sin base de comparación", useUnmergedTree = true).assertExists()
    }

    @Test fun freeHistoricalRequestOffersPlansCurrentAndHistory() {
        var plans = 0; var current = 0; var history = 0
        show(
            AnalyticsUiState.PremiumRequired(result(currentPeriod, 1_000), NetWorthSummary.NoData),
            AnalyticsRequest.Report(previousPeriod),
            onCurrent = { current++ }, onPlans = { plans++ }, onHistory = { history++ },
        )
        compose.onNodeWithTag(ANALYTICS_PREMIUM_PLANS_TAG).performClick()
        compose.onNodeWithTag(ANALYTICS_PREMIUM_CURRENT_TAG).performClick()
        compose.onNodeWithTag(ANALYTICS_PREMIUM_HISTORY_TAG).performClick()
        assertEquals(listOf(1, 1, 1), listOf(plans, current, history))
    }

    @Test fun errorRetriesAndLockedHidesControls() {
        var retries = 0
        show(AnalyticsUiState.Error("No se pudieron cargar las estadísticas."), onRetry = { retries++ })
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test fun lockedStateHasNoControls() {
        show(AnalyticsUiState.Locked)
        compose.onNodeWithText("Kipu está bloqueado").assertExists()
        compose.onNodeWithTag(ANALYTICS_FILTERS_TAG).assertDoesNotExist()
    }

    @Test fun reportModeRequestsSelectedMonth() {
        val requested = mutableListOf<AnalyticsPeriod>()
        show(AnalyticsUiState.Content(result(currentPeriod, 1_000), NetWorthSummary.NoData), onReport = { period, _ -> requested += period })
        compose.onNodeWithTag(ANALYTICS_MODE_TAG_PREFIX + "report").performClick()
        assertEquals(previousPeriod, requested.single())
    }
}
