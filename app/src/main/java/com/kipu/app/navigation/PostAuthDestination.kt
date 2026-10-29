package com.kipu.app.navigation

/** Shared by interactive entry and restoration; a reminder keeps its explicit destination. */
internal suspend fun resolvePostAuthDestination(
    pendingReminderRoute: String?,
    hasPlanPreference: suspend () -> Boolean,
): String = pendingReminderRoute ?: if (hasPlanPreference()) ACCOUNTS_DASHBOARD_ROUTE else PLAN_SELECTION_ROUTE
