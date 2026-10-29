package com.kipu.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kipu.app.feature.plans.presentation.PlanSelectionRoute
import com.kipu.app.feature.plans.presentation.PlanPurchaseRoute

const val PLAN_SELECTION_ROUTE = "onboarding/plan-selection"
const val PLAN_PURCHASE_ROUTE = "billing/purchase"
const val PLAN_PREFERENCE_ROUTE = "settings/plan-preference"

fun NavGraphBuilder.planSelectionDestination(navController: NavController, onConfirmed: () -> Unit) {
    composable(PLAN_SELECTION_ROUTE) { PlanSelectionRoute(onConfirmed) }
    composable(PLAN_PREFERENCE_ROUTE) {
        PlanSelectionRoute(
            onConfirmed = { navController.popBackStack() },
            onNavigateBack = { navController.popBackStack() },
        )
    }
    composable(PLAN_PURCHASE_ROUTE) { PlanPurchaseRoute(onNavigateBack = { navController.popBackStack() }) }
}

/**
 * Single source of the authenticated app graph. MainActivity's NavHost must use this builder:
 * a hand-copied list once omitted [calendarDestinations] and crashed "Calendario financiero".
 */
fun NavGraphBuilder.appDestinations(
    navController: NavController,
    onAuthenticated: (userId: String) -> Unit,
    onSignOut: () -> Unit,
) {
    authDestinations(navController = navController, onAuthenticated = onAuthenticated)
    planSelectionDestination(navController = navController, onConfirmed = { navController.navigate(BIOMETRIC_ROUTE) })
    settingsDestinations(navController = navController, onSignOut = onSignOut)
    accountsDestinations(navController = navController)
    movementDestinations(navController = navController)
    receiptDestinations(navController)
    captureDestinations(navController = navController)
    movementsDestinations(navController = navController)
    debtDestinations(navController = navController)
    notificationDestinations(navController = navController)
    goalDestinations(navController = navController)
    calendarDestinations(navController = navController)
    budgetDestinations(navController = navController)
}
