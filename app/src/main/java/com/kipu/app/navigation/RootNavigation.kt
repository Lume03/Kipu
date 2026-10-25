package com.kipu.app.navigation

import androidx.navigation.NavController

internal fun NavController.navigateRootTab(route: String) {
    // Save the outgoing root's ViewModels and saveable UI (tabs, filters, scroll).
    if (route == ACCOUNTS_DASHBOARD_ROUTE && popBackStack(route, inclusive = false, saveState = true)) return
    navigate(route) {
        popUpTo(ACCOUNTS_DASHBOARD_ROUTE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    // A saved stack can include a child, e.g. filtered history opened from a receipt.
    // Selecting a bottom tab always lands on its root, while retaining the root state.
    if (currentDestination?.route != route) popBackStack(route, inclusive = false)
}
