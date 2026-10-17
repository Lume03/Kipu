package com.kipu.app.feature.settings.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun BiometricSettingsAvailableThemePreview() {
    KipuTheme {
        BiometricSettingsContent(
            state = BiometricSettingsUiState(
                isLocalUnlockEnabled = false,
                hasBiometrics = true,
                hasDeviceCredential = true,
                canAuthenticate = true,
            ),
            onToggleLocalUnlock = {},
            onContinue = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@KipuThemePreviews
@Composable
private fun BiometricSettingsUnavailableThemePreview() {
    KipuTheme {
        BiometricSettingsContent(
            state = BiometricSettingsUiState(canAuthenticate = false),
            onToggleLocalUnlock = {},
            onContinue = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
