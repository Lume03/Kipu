package com.kipu.app.feature.auth.presentation

import androidx.compose.runtime.Composable
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun SignOutDialogPendingChangesThemePreview() {
    KipuTheme {
        SignOutDialog(
            pendingCount = 2,
            onConfirmSignOut = {},
            onDismiss = {},
        )
    }
}
