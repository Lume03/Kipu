package com.kipu.app.core.network

import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.security.StoredAuthSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.feature.auth.data.AuthSessionGate
import kotlinx.coroutines.CancellationException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthenticatedSessionProvider @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val sessionStorage: KeystoreEncryptedSessionStorage,
    private val sessionCoordinator: SessionCoordinator,
    private val sessionGate: AuthSessionGate,
) : AuthenticatedSessionProvider {
    override suspend fun currentSession(): AuthenticatedSession? {
        if (sessionCoordinator.localAccess.value !is LocalAccess.Available) return null
        val supabaseSession = runCatching {
            supabaseClient.auth.currentSessionOrNull()?.toAuthenticatedSession()
        }.getOrNull()

        if (supabaseSession != null) return supabaseSession

        val stored = sessionStorage.load()?.let(StoredAuthSession::parse) ?: return null
        if (!stored.hasValidAccessToken(Instant.now())) return null
        val ownerId = sessionCoordinator.currentOwner?.verifiedUserId ?: return null
        if (ownerId != stored.userId) return null
        val uuid = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return null
        return AuthenticatedSession(userId = uuid, accessToken = stored.accessToken)
    }

    override suspend fun refreshSession(): AuthenticatedSession? = sessionGate.exclusive {
        if (sessionCoordinator.localAccess.value !is LocalAccess.Available) return@exclusive null
        if (sessionCoordinator.remoteSession.value is RemoteSession.ReauthenticationRequired) return@exclusive null
        // Another worker or session restoration may already have renewed the credentials.
        currentSession()?.let { return@exclusive it }
        val stored = sessionStorage.load()?.let(StoredAuthSession::parse) ?: return@exclusive null
        val ownerId = sessionCoordinator.currentOwner?.verifiedUserId ?: return@exclusive null
        if (stored.userId != ownerId) return@exclusive null
        // Automatic SDK storage loading is disabled: waiting for its Initializing status
        // here can deadlock before Kipu has imported its own encrypted session.
        val refreshed = try {
            supabaseClient.auth.refreshSession(stored.refreshToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (requiresReauthentication(error)) {
                sessionCoordinator.updateRemoteSession(RemoteSession.ReauthenticationRequired(ownerId))
            }
            return@exclusive null
        }
        if (refreshed.user?.id != ownerId) return@exclusive null
        val now = Instant.now()
        val updated = StoredAuthSession.fromUserSession(refreshed, ownerId, now)
        supabaseClient.auth.importSession(updated.toUserSession(), autoRefresh = false)
        sessionStorage.save(StoredAuthSession.encode(updated))
        sessionCoordinator.updateRemoteSession(RemoteSession.Valid(ownerId, now.plusSeconds(updated.expiresIn)))
        AuthenticatedSession(UUID.fromString(ownerId), updated.accessToken)
    }

    private fun io.github.jan.supabase.auth.user.UserSession.toAuthenticatedSession(): AuthenticatedSession? {
        if (expiresAt <= kotlin.time.Clock.System.now()) return null
        val id = user?.id ?: return null
        if (id != sessionCoordinator.currentOwner?.verifiedUserId) return null
        val userId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return AuthenticatedSession(userId = userId, accessToken = accessToken)
    }
}

/** A revoked refresh credential cannot be recovered by retrying the same request. */
internal fun requiresReauthentication(error: Throwable): Boolean =
    error is AuthRestException && error.errorCode in setOf(
        AuthErrorCode.RefreshTokenAlreadyUsed,
        AuthErrorCode.RefreshTokenNotFound,
        AuthErrorCode.SessionNotFound,
        AuthErrorCode.SessionExpired,
    )
