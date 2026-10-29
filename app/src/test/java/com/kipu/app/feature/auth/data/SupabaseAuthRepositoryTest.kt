package com.kipu.app.feature.auth.data

import com.kipu.app.core.network.SupabaseAuthenticatedSessionProvider
import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.PendingChangesRepository
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.data.remote.ApiResponse
import com.kipu.app.feature.auth.data.remote.AuthApi
import com.kipu.app.feature.auth.data.remote.SessionEnvelopeDto
import com.kipu.app.feature.auth.domain.model.AuthCredentials
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthRepositoryTest {
    @Test
    fun `successful sign in reuses verified in memory owner without decrypting storage per request`() = runTest {
        val owner = "11111111-1111-4111-8111-111111111111"
        val api = mockk<AuthApi>()
        val storage = mockk<KeystoreEncryptedSessionStorage>(relaxed = true)
        val coordinator = mockk<SessionCoordinator>(relaxed = true)
        val auth = mockk<Auth>(relaxed = true)
        val client = mockk<SupabaseClient>()
        val imported = slot<UserSession>()
        val gate = AuthSessionGate()
        every { client.pluginManager } returns mockk {
            every { installedPlugins } returns mapOf(Auth.key to auth)
        }
        coEvery { api.login(any()) } returns ApiResponse.Success(
            SessionEnvelopeDto("access", "refresh", 3600, "bearer", owner),
        )
        coEvery { auth.importSession(capture(imported), any(), any()) } returns Unit
        every { auth.currentSessionOrNull() } answers { imported.captured }
        every { coordinator.currentOwner } returns LocalOwner(owner)
        every { coordinator.localAccess } returns MutableStateFlow(
            LocalAccess.Available(owner, RemoteSession.Absent),
        )
        val repository = SupabaseAuthRepository(api, storage, coordinator, client,
            mockk<PendingChangesRepository>(relaxed = true), gate)

        assertTrue(repository.signIn(AuthCredentials("test@example.com", "Password123")).isSuccess)
        val provider = SupabaseAuthenticatedSessionProvider(client, storage, coordinator, gate)
        repeat(4) { assertEquals(owner, provider.currentSession()!!.userId.toString()) }
        coVerify(exactly = 0) { storage.load() }
        coVerify(exactly = 1) { storage.save(any()) }
    }
}
