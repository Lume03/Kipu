package com.kipu.app.feature.auth.presentation

import androidx.compose.runtime.Composable
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun AuthFormThemePreview() {
    KipuTheme {
        AuthFormScreen(
            uiState = AuthUiState(email = "persona@ejemplo.com"),
            onEmailChanged = {},
            onPasswordChanged = {},
            onLoginClick = {},
            onRegisterClick = {},
            onNavigateToRecovery = {},
            onDismissExistingAccountDialog = {},
            onDismissConfirmationDialog = {},
            initialRegisterMode = false,
        )
    }
}
