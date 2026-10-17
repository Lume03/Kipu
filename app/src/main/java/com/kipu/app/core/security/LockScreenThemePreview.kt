package com.kipu.app.core.security

import androidx.compose.runtime.Composable
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun LockScreenThemePreview() {
    KipuTheme {
        LockScreenContent(
            errorMessage = "No se pudo desbloquear. Intenta nuevamente.",
            onUnlockClick = {},
            onSignOutRequested = {},
        )
    }
}
