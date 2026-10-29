package com.kipu.app.feature.auth.data

import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.network.requiresReauthentication
import kotlinx.coroutines.CancellationException
import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.security.accessTokenSubject
import com.kipu.app.core.security.StoredAuthSession
import com.kipu.app.core.session.PendingChangesRepository
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.data.remote.ApiResponse
import com.kipu.app.feature.auth.data.remote.AuthApi
import com.kipu.app.feature.auth.data.remote.LoginRequestDto
import com.kipu.app.feature.auth.data.remote.RegisterRequestDto
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.auth.domain.PasswordValidator
import com.kipu.app.feature.auth.domain.model.AuthCredentials
import com.kipu.app.feature.auth.domain.model.AuthError
import com.kipu.app.feature.auth.domain.model.AuthResult
import com.kipu.app.feature.auth.domain.model.CooldownState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionStorage: KeystoreEncryptedSessionStorage,
    private val sessionCoordinator: SessionCoordinator,
    private val supabaseClient: SupabaseClient,
    private val pendingChangesRepository: PendingChangesRepository,
    private val sessionGate: AuthSessionGate,
) : AuthRepository {

    private val _cooldownState = MutableStateFlow(CooldownState())
    override val cooldownState: StateFlow<CooldownState> = _cooldownState.asStateFlow()

    override suspend fun register(credentials: AuthCredentials): Result<AuthResult> {
        val emailValidation = PasswordValidator.validateEmail(credentials.email)
        if (!emailValidation.isValid) {
            return Result.failure(Exception((emailValidation as PasswordValidator.ValidationResult.Invalid).reason))
        }
        val passwordValidation = PasswordValidator.validatePassword(credentials.password)
        if (!passwordValidation.isValid) {
            return Result.failure(Exception((passwordValidation as PasswordValidator.ValidationResult.Invalid).reason))
        }

        val normalizedEmail = PasswordValidator.normalizeEmail(credentials.email)
        val response = authApi.register(
            RegisterRequestDto(
                email = normalizedEmail,
                password = credentials.password,
                captchaToken = credentials.captchaToken,
            )
        )

        return when (response) {
            is ApiResponse.Success -> {
                Result.success(AuthResult.ConfirmationRequired(normalizedEmail))
            }
            is ApiResponse.Error -> {
                when (response.statusCode) {
                    409 -> Result.success(AuthResult.AccountAlreadyExists)
                    429 -> {
                        val retry = response.retryAfter ?: 60
                        _cooldownState.value = CooldownState(retry, Instant.now().plusSeconds(retry.toLong()))
                        Result.failure(Exception("Demasiados intentos. Intenta en $retry segundos."))
                    }
                    else -> Result.failure(Exception("Error al procesar el registro."))
                }
            }
            is ApiResponse.NetworkFailure -> {
                Result.failure(Exception("Se requiere conexión a internet para registrarse."))
            }
        }
    }

    override suspend fun signIn(credentials: AuthCredentials): Result<AuthResult> {
        if (_cooldownState.value.isBlocked) {
            return Result.failure(Exception("Espera antes de intentar nuevamente."))
        }

        val emailValidation = PasswordValidator.validateEmail(credentials.email)
        if (!emailValidation.isValid) {
            return Result.failure(Exception("Correo o contraseña incorrectos."))
        }
        val passwordValidation = PasswordValidator.validatePassword(credentials.password)
        if (!passwordValidation.isValid) {
            return Result.failure(Exception("Correo o contraseña incorrectos."))
        }

        val normalizedEmail = PasswordValidator.normalizeEmail(credentials.email)
        val response = authApi.login(
            LoginRequestDto(
                email = normalizedEmail,
                password = credentials.password,
                captchaToken = credentials.captchaToken,
            )
        )

        return when (response) {
            is ApiResponse.Success -> {
                val envelope = response.data
                try {
                    check(accessTokenSubject(envelope.accessToken) == envelope.userId) { "Auth subject mismatch" }
                    val now = Instant.now()
                    val stored = StoredAuthSession(
                        accessToken = envelope.accessToken,
                        refreshToken = envelope.refreshToken,
                        expiresIn = envelope.expiresIn,
                        tokenType = envelope.tokenType,
                        userId = envelope.userId,
                        expiresAtEpochSeconds = now.plusSeconds(envelope.expiresIn).epochSecond,
                    )
                    // Bind the independently decoded token subject before exposing the SDK fast path.
                    supabaseClient.auth.importSession(stored.toUserSession().copy(
                        user = UserInfo(aud = "authenticated", id = stored.userId),
                    ), autoRefresh = false)
                    sessionStorage.save(StoredAuthSession.encode(stored))
                    sessionCoordinator.setActiveOwner(envelope.userId)
                    sessionCoordinator.updateRemoteSession(
                        RemoteSession.Valid(envelope.userId, now.plusSeconds(envelope.expiresIn))
                    )
                    _cooldownState.value = CooldownState(0, null)
                    Result.success(AuthResult.Success(envelope.userId))
                } catch (e: CancellationException) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { clearFailedSession() }
                    throw e
                } catch (e: Exception) {
                    clearFailedSession()
                    SecureLog.e("SupabaseAuthRepository", "Failed to activate owner session", e)
                    Result.failure(Exception("Error al inicializar la sesión."))
                }
            }
            is ApiResponse.Error -> {
                when (response.statusCode) {
                    429 -> {
                        val retry = response.retryAfter ?: 60
                        _cooldownState.value = CooldownState(retry, Instant.now().plusSeconds(retry.toLong()))
                        Result.failure(Exception("Demasiados intentos. Intenta en $retry segundos."))
                    }
                    else -> {
                        // FR-004: Neutral error response
                        Result.failure(Exception("Correo o contraseña incorrectos."))
                    }
                }
            }
            is ApiResponse.NetworkFailure -> {
                Result.failure(Exception("Se requiere conexión a internet para iniciar sesión."))
            }
        }
    }

    private suspend fun clearFailedSession() {
        runCatching { supabaseClient.auth.clearSession() }
        sessionStorage.delete()
        sessionCoordinator.clearActiveOwner(explicit = false)
        sessionCoordinator.updateRemoteSession(RemoteSession.Absent)
    }

    override suspend fun signOut(explicit: Boolean): Result<Unit> {
        return try {
            val ownerId = sessionCoordinator.currentOwner?.verifiedUserId
            if (ownerId != null) {
                runCatching { UUID.fromString(ownerId) }.getOrNull()?.let { userId ->
                    pendingChangesRepository.markWaitingForAuth(userId)
                }
            }
            sessionCoordinator.clearActiveOwner(explicit)
            sessionStorage.delete()
            sessionCoordinator.updateRemoteSession(RemoteSession.Absent)
            try {
                supabaseClient.auth.signOut()
            } catch (_: Exception) {
                // Ignore remote network sign-out failure on offline logout per FR-016
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreSession(): Result<AuthResult?> = sessionGate.exclusive { restoreSessionExclusive() }

    private suspend fun restoreSessionExclusive(): Result<AuthResult?> {
        return try {
            val sessionJson = sessionStorage.load()
            if (sessionJson.isNullOrEmpty()) {
                sessionCoordinator.updateRemoteSession(RemoteSession.Absent)
                return Result.success(null)
            }
            val stored = StoredAuthSession.parse(sessionJson)
            if (stored == null) {
                sessionCoordinator.updateRemoteSession(RemoteSession.Absent)
                return Result.success(null)
            }
            val userId = stored.userId
            if (accessTokenSubject(stored.accessToken) != userId) {
                clearFailedSession()
                return Result.failure(IllegalStateException("Auth subject mismatch"))
            }
            sessionCoordinator.setActiveOwner(userId)
            if (stored.hasValidAccessToken(Instant.now())) {
                val imported = runCatching {
                    supabaseClient.auth.importSession(stored.toUserSession().copy(
                        user = UserInfo(aud = "authenticated", id = stored.userId),
                    ), autoRefresh = false)
                }.isSuccess
                if (imported) {
                    sessionCoordinator.updateRemoteSession(
                        RemoteSession.Valid(userId, Instant.ofEpochSecond(requireNotNull(stored.expiresAtEpochSeconds)))
                    )
                    return Result.success(AuthResult.Success(userId))
                }
            }

            var refreshError: Exception? = null
            val refreshed = try {
                supabaseClient.auth.refreshSession(stored.refreshToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                refreshError = error
                null
            }
            if (refreshed != null && (refreshed.user?.id != userId || accessTokenSubject(refreshed.accessToken) != userId)) {
                clearFailedSession()
                sessionCoordinator.updateRemoteSession(RemoteSession.ReauthenticationRequired(userId))
                return Result.failure(IllegalStateException("La sesión restaurada no corresponde al usuario local"))
            }
            if (refreshed != null && refreshed.user?.id == userId) {
                val now = Instant.now()
                val updated = StoredAuthSession.fromUserSession(refreshed, userId, now)
                try {
                    sessionStorage.save(StoredAuthSession.encode(updated))
                    supabaseClient.auth.importSession(updated.toUserSession().copy(user = refreshed.user), autoRefresh = false)
                } catch (error: Exception) {
                    clearFailedSession()
                    throw error
                }
                sessionCoordinator.updateRemoteSession(RemoteSession.Valid(userId, now.plusSeconds(updated.expiresIn)))
            } else {
                sessionCoordinator.updateRemoteSession(
                    if (refreshError?.let(::requiresReauthentication) == true) RemoteSession.ReauthenticationRequired(userId)
                    else RemoteSession.RefreshRequired(userId)
                )
            }
            Result.success(AuthResult.Success(userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
