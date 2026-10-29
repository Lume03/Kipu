package com.kipu.app.feature.auth.data

import com.kipu.app.core.network.SupabaseAuthenticatedSessionProvider
import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.security.StoredAuthSession
import com.kipu.app.core.session.*
import com.kipu.app.feature.auth.data.remote.*
import com.kipu.app.feature.auth.domain.model.AuthCredentials
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.*
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
class SupabaseAuthRepositoryTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val api = mockk<AuthApi>()
    private val storage = mockk<KeystoreEncryptedSessionStorage>(relaxed = true)
    private val coordinator = mockk<SessionCoordinator>(relaxed = true)
    private val auth = mockk<Auth>(relaxed = true)
    private val client = mockk<SupabaseClient>()
    private val imported = slot<UserSession>()
    private val gate = AuthSessionGate()
    private val verifier = mockk<AuthSessionVerifier>(relaxed = true)
    private val onboarding = mockk<PlanOnboardingStore>(relaxed = true)
    private val verifiedUser = io.github.jan.supabase.auth.user.UserInfo(aud = "authenticated", id = owner, email = "test@example.com")
    private fun token(subject: String) = "e30." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"sub\":\"$subject\"}".toByteArray()) + ".signature"
    private fun repository(accessToken: String = token(owner)): SupabaseAuthRepository {
        every { client.pluginManager } returns mockk {
            every { installedPlugins } returns mapOf(Auth.key to auth)
        }
        coEvery { api.login(any()) } returns ApiResponse.Success(
            SessionEnvelopeDto(accessToken, "refresh", 3600, "bearer", owner))
        coEvery { verifier.retrieveUser(any()) } returns verifiedUser
        every { verifier.isOnline() } returns true
        coEvery { auth.importSession(capture(imported), any(), any()) } returns Unit
        every { auth.currentSessionOrNull() } answers { if (imported.isCaptured) imported.captured else null }
        every { coordinator.currentOwner } returns LocalOwner(owner)
        every { coordinator.localAccess } returns MutableStateFlow(LocalAccess.Available(owner, RemoteSession.Absent))
        return SupabaseAuthRepository(api, storage, coordinator, client,
            mockk<PendingChangesRepository>(relaxed = true), gate, verifier, onboarding)
    }
    private val credentials = AuthCredentials("test@example.com", "Password123")

    @Test fun `sign in binds JWT subject and reuses memory without storage reads`() = runTest {
        assertTrue(repository().signIn(credentials).isSuccess)
        val provider = SupabaseAuthenticatedSessionProvider(client, storage, coordinator, gate)
        repeat(4) { assertEquals(owner, provider.currentSession()!!.userId.toString()) }
        coVerify(exactly = 0) { storage.load() }
        coVerify(exactly = 1) { storage.save(any()) }
    }

    @Test fun `different JWT subject rejects envelope before import or owner activation`() = runTest {
        assertTrue(repository(token("22222222-2222-4222-8222-222222222222")).signIn(credentials).isFailure)
        coVerify(exactly = 0) { auth.importSession(any(), any(), any()) }
        coVerify(exactly = 0) { coordinator.setActiveOwner(any()) }
        coVerify { auth.clearSession(); storage.delete(); coordinator.clearActiveOwner(false) }
    }

    @Test fun `malformed token fails closed`() = runTest {
        assertTrue(repository("not-a-jwt").signIn(credentials).isFailure)
        coVerify(exactly = 0) { storage.save(any()); coordinator.setActiveOwner(any()) }
    }

    @Test fun `encrypted persistence failure clears imported session and returns login error`() = runTest {
        val repo = repository()
        coEvery { storage.save(any()) } throws java.io.IOException("disk full")
        assertTrue(repo.signIn(credentials).isFailure)
        coVerify(exactly = 1) { auth.importSession(any(), any(), any()) }
        coVerify(exactly = 0) { coordinator.setActiveOwner(any()) }
        coVerify { auth.clearSession(); storage.delete(); coordinator.clearActiveOwner(false) }
    }

    @Test fun `restoration rejects stored owner inconsistent with token`() = runTest {
        val repo = repository()
        coEvery { storage.load() } returns StoredAuthSession.encode(StoredAuthSession(
            token("other-owner"), "refresh", 3600, "bearer", owner, java.time.Instant.now().epochSecond + 3600))
        assertTrue(repo.restoreSession().isFailure)
        coVerify(exactly = 0) { coordinator.setActiveOwner(any()) }
        coVerify { auth.clearSession(); storage.delete() }
    }
    @Test fun `Auth rejects invalid signature despite matching payload and owner stays inactive`() = runTest {
        val repo = repository()
        coEvery { verifier.retrieveUser(any()) } throws IllegalStateException("Auth 401 invalid JWT signature/header")
        assertTrue(repo.signIn(credentials).isFailure)
        coVerify(exactly = 0) { coordinator.setActiveOwner(any()); auth.importSession(any(), any(), any()); storage.save(any()) }
        coVerify { auth.clearSession(); storage.delete() }
    }

    @Test fun `Auth user id must match subject and envelope`() = runTest {
        val repo = repository()
        coEvery { verifier.retrieveUser(any()) } returns verifiedUser.copy(id = "other")
        assertTrue(repo.signIn(credentials).isFailure)
        coVerify(exactly = 0) { coordinator.setActiveOwner(any()) }
        coVerify { auth.clearSession(); storage.delete(); coordinator.clearActiveOwner(false) }
    }

    @Test fun `returned Auth user is imported and persisted before owner activates`() = runTest {
        val repo = repository()
        assertTrue(repo.signIn(credentials).isSuccess)
        assertEquals(verifiedUser, imported.captured.user)
        coVerifyOrder { verifier.retrieveUser(any()); auth.importSession(any(), any(), any()); storage.save(any()); coordinator.setActiveOwner(owner) }
        coVerify { storage.save(match { StoredAuthSession.parse(it)?.verifiedUser == verifiedUser }) }
    }

    private fun stored(verified: Boolean = true) = StoredAuthSession(token(owner), "refresh", 3600, "bearer", owner,
        java.time.Instant.now().epochSecond + 3600, verifiedUser.takeIf { verified })

    @Test fun `offline startup accepts only previously verified encrypted session without network wait`() = runTest {
        val repo = repository()
        every { verifier.isOnline() } returns false
        coEvery { storage.load() } returns StoredAuthSession.encode(stored())
        assertTrue(repo.restoreSession().isSuccess)
        coVerify(exactly = 0) { verifier.retrieveUser(any()); auth.refreshSession(any()) }
        coVerify { coordinator.setActiveOwner(owner) }
        verify(exactly = 1) { verifier.whenOnline(any()) }
    }

    @Test fun `offline legacy unverified session never activates owner`() = runTest {
        val repo = repository()
        every { verifier.isOnline() } returns false
        coEvery { storage.load() } returns StoredAuthSession.encode(stored(false))
        assertTrue(repo.restoreSession().isFailure)
        coVerify(exactly = 0) { coordinator.setActiveOwner(any()) }
    }

    @Test fun `online restoration rejects Auth identity mismatch even with previous verification`() = runTest {
        val repo = repository()
        coEvery { storage.load() } returns StoredAuthSession.encode(stored())
        coEvery { verifier.retrieveUser(any()) } returns verifiedUser.copy(id = "other")
        assertTrue(repo.restoreSession().isFailure)
        coVerify(exactly = 0) { coordinator.setActiveOwner(any()) }
        coVerify { storage.delete(); auth.clearSession() }
    }

    @Test fun `online restoration transport outage retains verified session and schedules retry`() = runTest {
        val repo = repository()
        coEvery { storage.load() } returns StoredAuthSession.encode(stored())
        coEvery { verifier.retrieveUser(any()) } throws java.io.IOException("network lost")
        assertTrue(repo.restoreSession().isSuccess)
        coVerify { coordinator.setActiveOwner(owner) }
        verify { verifier.whenOnline(any()) }
    }

    @Test fun `owner stays inactive until Auth verification finishes during welcome`() = runTest {
        val repo = repository()
        val verification = kotlinx.coroutines.CompletableDeferred<io.github.jan.supabase.auth.user.UserInfo>()
        coEvery { verifier.retrieveUser(any()) } coAnswers { verification.await() }
        val entry = com.kipu.app.feature.auth.presentation.SessionEntryCurtain()
        entry.start(credentials.email)
        val login = async { repo.signIn(credentials) }
        runCurrent()
        assertNotNull(entry.state.value)
        assertFalse(login.isCompleted)
        coVerify(exactly = 0) { coordinator.setActiveOwner(any()) }
        verification.complete(verifiedUser)
        assertTrue(login.await().isSuccess)
    }

    @Test fun `offline verified session is revalidated after network becomes available`() = runTest {
        val repo = repository()
        every { verifier.isOnline() } returns false
        coEvery { storage.load() } returns StoredAuthSession.encode(stored())
        val action = slot<suspend () -> Unit>()
        every { verifier.whenOnline(capture(action)) } returns kotlinx.coroutines.Job()
        assertTrue(repo.restoreSession().isSuccess)
        coVerify(exactly = 0) { verifier.retrieveUser(any()) }
        action.captured.invoke()
        coVerify(exactly = 1) { verifier.retrieveUser(any()) }
        coVerify { storage.save(match { StoredAuthSession.parse(it)?.verifiedUser == verifiedUser }) }
    }

    @Test fun `Auth rejection on deferred revalidation clears previously verified owner`() = runTest {
        val repo = repository()
        every { verifier.isOnline() } returns false
        coEvery { storage.load() } returns StoredAuthSession.encode(stored())
        val action = slot<suspend () -> Unit>()
        every { verifier.whenOnline(capture(action)) } returns kotlinx.coroutines.Job()
        assertTrue(repo.restoreSession().isSuccess)
        coEvery { verifier.retrieveUser(any()) } throws IllegalStateException("Auth rejected signature")
        action.captured.invoke()
        coVerify { storage.delete(); coordinator.clearActiveOwner(false); auth.clearSession() }
    }

}
