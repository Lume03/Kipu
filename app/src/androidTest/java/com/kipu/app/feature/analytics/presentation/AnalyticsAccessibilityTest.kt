package com.kipu.app.feature.analytics.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.kipu.app.feature.analytics.domain.*
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic state only: does not touch the signed-in user's preferences or entitlement. */
class AnalyticsAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 3)
    private val zone = ZoneId.of("America/Lima")

    @Test fun largeTextKeepsPeriodAndMaskActionAccessibleWithoutLeakingChartAmounts() {
        val period = AnalyticsPeriod.currentMonth(today, zone)
        val result = AnalyticsCalculator.calculate(period, today, AnalyticsFilters(),
            listOf(ExpenseRow(12345, 0, "PEN", "food", null, null, null)))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                KipuTheme {
                    AnalyticsScreen(AnalyticsUiState.Content(result),
                        AnalyticsRequest.CurrentMonth, AnalyticsFilterOptions(categories = listOf(AnalyticsFilterOption("food", "Comida"))),
                        {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, today = today, zone = zone)
                }
            }
        }
        compose.onNodeWithContentDescription("Ocultar importes").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Mostrar importes").assertIsDisplayed()
        compose.onNodeWithTag(ANALYTICS_PARTIAL_TAG, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        // The categories card sits below the ring, flows and weeks, so the lazy list has to bring it in first.
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("${ANALYTICS_CATEGORY_CHART_TAG}_PEN"))
        compose.onNodeWithTag("${ANALYTICS_CATEGORY_CHART_TAG}_PEN").assertIsDisplayed()
        compose.onAllNodes(hasText("123.45", substring = true) or hasContentDescription("123.45", substring = true),
            useUnmergedTree = true).assertCountEquals(0)
    }
}
