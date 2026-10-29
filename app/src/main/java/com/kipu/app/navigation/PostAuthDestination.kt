package com.kipu.app.navigation

/** Notifications win; only explicitly registered accounts with pending onboarding select a plan. */
internal suspend fun resolvePostAuthDestination(
    pendingReminderRoute: String?,
    onboardingPending: Boolean = false,
    hasPlanPreference: suspend () -> Boolean,
): String = pendingReminderRoute ?: if (onboardingPending && !hasPlanPreference()) PLAN_SELECTION_ROUTE else ACCOUNTS_DASHBOARD_ROUTE
