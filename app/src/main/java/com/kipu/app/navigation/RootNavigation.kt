package com.kipu.app.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Bottom-bar pages. All of them live inside the dashboard destination, like Dinero and
 * Movimientos always did: switching pages only animates, it never navigates or recomposes a screen.
 */
internal object RootTab {
    const val MONEY = 0
    const val MOVEMENTS = 1
    const val PLANNING = 2
    const val ANALYSIS = 3
    const val COUNT = 4
}

/** Selected [RootTab], kept in the dashboard entry so it survives rotation and process death. */
const val ROOT_TAB_KEY = "root_tab"

internal fun NavController.dashboardEntryOrNull(): NavBackStackEntry? =
    runCatching { getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }.getOrNull()

/** Shows a bottom-bar page, first returning to the dashboard when a child screen is on top. */
internal fun NavController.selectRootTab(tab: Int) {
    // Set it before navigating so the NavHost transition already sees the destination page.
    dashboardEntryOrNull()?.savedStateHandle?.set(ROOT_TAB_KEY, tab)
    if (currentDestination?.route != ACCOUNTS_DASHBOARD_ROUTE) navigateRootTab(ACCOUNTS_DASHBOARD_ROUTE)
    dashboardEntryOrNull()?.savedStateHandle?.set(ROOT_TAB_KEY, tab)
}

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
