package com.kipu.app.feature.auth.presentation

data class AuthUiState(
    val email: String = "",
    val emailError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val generalError: String? = null,
    val cooldownSeconds: Int = 0,
    val showExistingAccountDialog: Boolean = false,
    val confirmationRequiredEmail: String? = null,
) {
    val isFormValid: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && emailError == null && passwordError == null
}

sealed interface AuthNavigationEvent {
    /** [accountCreated] is true when a new account was just registered: it gets a welcome screen before going on. */
    data class NavigateToHome(val userId: String, val accountCreated: Boolean = false) : AuthNavigationEvent
    data object NavigateToLogin : AuthNavigationEvent
    data object NavigateToRegister : AuthNavigationEvent
    data object NavigateToRecovery : AuthNavigationEvent
}
