package com.kipu.app.feature.auth.domain

import com.kipu.app.feature.auth.domain.model.AuthCredentials
import com.kipu.app.feature.auth.domain.model.AuthResult
import com.kipu.app.feature.auth.domain.model.CooldownState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface governing authentication operations.
 * Enforces neutral responses, owner isolation, and progressive rate limiting.
 */
interface AuthRepository {
    val cooldownState: StateFlow<CooldownState>

    /**
     * Attempts to register a new account.
     * Complies with FR-001, FR-002, and FR-051 (existing account disclosure).
     */
    suspend fun register(credentials: AuthCredentials): Result<AuthResult>

    /**
     * Attempts to sign in with email and password.
     * Complies with FR-003, FR-004, FR-006, FR-007, and FR-049.
     */
    suspend fun signIn(credentials: AuthCredentials): Result<AuthResult>

    /**
     * Confirms the 6-digit code sent at registration and opens the session. Failures are [OtpException].
     */
    suspend fun verifyOtp(email: String, code: String): Result<AuthResult> =
        Result.failure(UnsupportedOperationException("OTP verification is unavailable"))

    /** Asks the server to mail a new confirmation code. */
    suspend fun resendOtp(email: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("OTP resend is unavailable"))

    /**
     * Signs out the currently authenticated user.
     * Complies with FR-016 and FR-017.
     */
    suspend fun signOut(explicit: Boolean): Result<Unit>

    /**
     * Attempts to restore an existing valid session from encrypted storage.
     * Complies with FR-012 and FR-013.
     */
    suspend fun restoreSession(): Result<AuthResult?>
}
