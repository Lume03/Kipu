package com.kipu.app.core.network

import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.security.StoredAuthSession
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.data.AuthSessionGate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SupabaseAuthenticatedSessionProviderTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val auth = mockk<Auth>(relaxed = true)
    private val client = mockk<SupabaseClient>()
    private val storage = mockk<KeystoreEncryptedSessionStorage>()
    private val coordinator = mockk<SessionCoordinator>(relaxed = true)
    private val remote = MutableStateFlow<RemoteSession>(RemoteSession.RefreshRequired(owner))
    private var stored = StoredAuthSession("expired", "refresh", 3600, "bearer", owner, 1L)

    private fun provider(): SupabaseAuthenticatedSessionProvider {
        every { client.pluginManager } returns mockk {
            every { installedPlugins } returns mapOf(Auth.key to auth)
        }
        every { auth.currentSessionOrNull() } returns null
        every { coordinator.currentOwner } returns LocalOwner(owner)
        every { coordinator.remoteSession } returns remote
        coEvery { coordinator.updateRemoteSession(any()) } coAnswers { remote.value = firstArg() }
        every { coordinator.localAccess } returns MutableStateFlow(LocalAccess.Available(owner, RemoteSession.RefreshRequired(owner)))
        coEvery { storage.load() } answers { StoredAuthSession.encode(stored) }
        coEvery { storage.save(any()) } coAnswers { stored = StoredAuthSession.parse(firstArg())!! }
        coEvery { auth.awaitInitialization() } coAnswers { kotlinx.coroutines.awaitCancellation() }
        return SupabaseAuthenticatedSessionProvider(client, storage, coordinator, AuthSessionGate())
    }

    @Test
    fun `valid encrypted session is available while SDK initialization is incomplete`() = runTest {
        val provider = provider()
        stored = stored.copy(accessToken = "valid", expiresAtEpochSeconds = Instant.now().epochSecond + 3600)
        assertEquals("valid", provider.currentSession()?.accessToken)
        coVerify(exactly = 0) { auth.awaitInitialization() }
    }

    @Test
    fun `parallel workers refresh once and persist renewed credentials without waiting for initialization`() = runTest {
        val provider = provider()
        coEvery { auth.refreshSession("refresh") } coAnswers {
            delay(10)
            UserSession(accessToken = "renewed", refreshToken = "rotated", expiresIn = 3600,
                tokenType = "bearer", user = UserInfo(aud = "authenticated", id = owner))
        }
        val first = async { provider.refreshSession() }
        val second = async { provider.refreshSession() }
        assertEquals("renewed", first.await()?.accessToken)
        assertEquals("renewed", second.await()?.accessToken)
        assertEquals("rotated", stored.refreshToken)
        coVerify(exactly = 1) { auth.refreshSession("refresh") }
        coVerify(exactly = 0) { auth.awaitInitialization() }
        coVerify(exactly = 1) { coordinator.updateRemoteSession(any<RemoteSession.Valid>()) }
    }

    @Test
    fun `refresh cancellation reaches worker and keeps pending credentials`() = runTest {
        val provider = provider()
        coEvery { auth.refreshSession(any()) } throws CancellationException("worker stopped")
        try {
            provider.refreshSession()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals("expired", stored.accessToken)
        coVerify(exactly = 0) { storage.save(any()) }
    }

    @Test
    fun `session for a different owner is never refreshed`() = runTest {
        val provider = provider()
        stored = stored.copy(userId = "22222222-2222-4222-8222-222222222222")
        assertNull(provider.refreshSession())
        coVerify(exactly = 0) { auth.refreshSession(any()) }
    }
    @Test
    fun `revoked refresh credential requests login once and preserves local session`() = runTest {
        val provider = provider()
        val error = mockk<io.github.jan.supabase.auth.exception.AuthRestException>()
        every { error.errorCode } returns io.github.jan.supabase.auth.exception.AuthErrorCode.RefreshTokenAlreadyUsed
        coEvery { auth.refreshSession(any()) } throws error
        assertNull(provider.refreshSession())
        assertTrue(remote.value is RemoteSession.ReauthenticationRequired)
        assertNull(provider.refreshSession())
        assertEquals("expired", stored.accessToken)
        coVerify(exactly = 1) { auth.refreshSession(any()) }
        coVerify(exactly = 0) { storage.save(any()) }
    }

    @Test
    fun `network failure remains retryable and does not require login`() = runTest {
        val provider = provider()
        coEvery { auth.refreshSession(any()) } throws java.io.IOException("offline")
        assertNull(provider.refreshSession())
        assertFalse(remote.value is RemoteSession.ReauthenticationRequired)
    }

}
