package com.kipu.app.navigation

import androidx.navigation.NavController

internal fun NavController.navigateRootTab(route: String) {
    // Restoring a root also restores the child that was above it (e.g. filtered
    // history opened from a receipt). Root tabs must always land on the root.
    if (route == ACCOUNTS_DASHBOARD_ROUTE && popBackStack(route, inclusive = false)) return
    navigate(route) {
        popUpTo(ACCOUNTS_DASHBOARD_ROUTE)
        launchSingleTop = true
    }
}
