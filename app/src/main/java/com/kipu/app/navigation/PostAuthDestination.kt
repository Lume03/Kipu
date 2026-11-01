package com.kipu.app.navigation

/** Pending initial setup must finish before reminders or the dashboard can open. */
internal suspend fun resolvePostAuthDestination(
    pendingReminderRoute: String?,
    onboardingPending: Boolean = false,
    setupPending: Boolean = false,
    hasPlanPreference: suspend () -> Boolean,
): String = if (setupPending) SETUP_GRAPH_ROUTE else pendingReminderRoute ?: if (onboardingPending && !hasPlanPreference()) PLAN_SELECTION_ROUTE else ACCOUNTS_DASHBOARD_ROUTE
