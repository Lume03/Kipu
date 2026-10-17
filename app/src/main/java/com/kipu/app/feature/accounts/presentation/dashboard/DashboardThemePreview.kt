package com.kipu.app.feature.accounts.presentation.dashboard

import androidx.compose.runtime.Composable
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun DashboardEmptyStateThemePreview() {
    KipuTheme {
        EmptyDashboardContent(
            quotaCount = 0,
            maxQuota = 4,
            onNavigateToNewAccount = {},
            onNavigateToNewCard = {},
            onNavigateToPlans = {},
        )
    }
}
