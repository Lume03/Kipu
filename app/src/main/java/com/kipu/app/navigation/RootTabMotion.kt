package com.kipu.app.navigation

/**
 * Resolves the visible root tab for NavHost transitions. Every bottom-bar page is hosted by the
 * dashboard; only the filtered movement history is a separate destination with a root position.
 */
internal object RootTabMotion {
    fun indexOf(route: String?, selectedDashboardTab: Int): Int? = when {
        route == ACCOUNTS_DASHBOARD_ROUTE -> selectedDashboardTab.takeIf { it in 0 until RootTab.COUNT }
        route == MOVEMENTS_HISTORY_PATTERN || route == MOVEMENTS_HISTORY_ROUTE ||
            route?.startsWith("movements/history") == true -> RootTab.MOVEMENTS
        else -> null
    }

    fun isRootTransition(
        initialRoute: String?,
        targetRoute: String?,
        selectedDashboardTab: Int,
    ): Boolean {
        val initialIndex = indexOf(initialRoute, selectedDashboardTab) ?: return false
        val targetIndex = indexOf(targetRoute, selectedDashboardTab) ?: return false
        return initialIndex != targetIndex
    }

    fun isForward(
        initialRoute: String?,
        targetRoute: String?,
        selectedDashboardTab: Int,
    ): Boolean {
        val initialIndex = indexOf(initialRoute, selectedDashboardTab) ?: return true
        val targetIndex = indexOf(targetRoute, selectedDashboardTab) ?: return true
        return targetIndex > initialIndex
    }
}
