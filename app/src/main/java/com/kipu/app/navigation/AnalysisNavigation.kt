package com.kipu.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.analytics.presentation.AnalyticsRoute

/** Bottom-bar identifier of Análisis, a page of the dashboard destination ([RootTab.ANALYSIS]). */
const val ANALYSIS_ROUTE = "analysis"

/** Direct entry to HU-41 statistics; the ViewModel re-evaluates entitlement on every request. */
const val ANALYTICS_ROUTE = "analytics"

fun NavController.navigateToAnalytics() {
    navigate(ANALYTICS_ROUTE) { launchSingleTop = true }
}

/** Free users are sent to the existing basic movement history, never to a new history screen. */
internal fun NavController.openBasicHistoryFromAnalytics() = selectRootTab(RootTab.MOVEMENTS)

fun NavGraphBuilder.analyticsDestinations(navController: NavController) {
    composable(ANALYTICS_ROUTE) {
        AnalyticsRoute(
            onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
            onNavigateToBasicHistory = navController::openBasicHistoryFromAnalytics,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
