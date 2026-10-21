package com.kipu.app.feature.plans.data.entitlement

import com.kipu.app.BuildConfig
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.data.remote.RefreshEntitlementApi
import com.kipu.app.feature.plans.data.remote.RefreshEntitlementApiResult
import com.kipu.app.feature.plans.data.remote.RefreshEntitlementResponseDto
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class TestPlanRefreshRepositoryTest {
    private val userId = UUID.fromString("7d100000-0000-4000-8000-000000000001")
    private val session = AuthenticatedSession(userId, "jwt")

    @Test
    fun `free override replaces signed cache without billing or other data changes`() = runTest {
        assumeTrue(BuildConfig.TEST_PLAN_OVERRIDES_ENABLED)
        val api = mockk<RefreshEntitlementApi>()
        coEvery { api.refresh(session, "spki") } returns RefreshEntitlementApiResult.Success(
            RefreshEntitlementResponseDto("FREE", "TEST_OVERRIDE", null, null),
        )
        val dao = mockk<FeatureAccessCacheDao>()
        coEvery { dao.putVerified(any()) } returns Unit
        val repository = repository(api, dao)

        val result = repository.refresh()

        assertEquals(TestPlanRefreshResult(true), result)
        coVerify(exactly = 1) { dao.putVerified(match { it.userId == userId && it.effectiveTier == "FREE" && it.source == "TEST_OVERRIDE" && it.offlineGrantPayload == null }) }
    }

    @Test
    fun `cancellation from network propagates and does not write cache`() = runTest {
        assumeTrue(BuildConfig.TEST_PLAN_OVERRIDES_ENABLED)
        val api = mockk<RefreshEntitlementApi>()
        coEvery { api.refresh(session, "spki") } throws CancellationException("cancel")
        val dao = mockk<FeatureAccessCacheDao>()
        val repository = repository(api, dao)

        try {
            repository.refresh()
            throw AssertionError("expected cancellation")
        } catch (_: CancellationException) {
            coVerify(exactly = 0) { dao.putVerified(any()) }
        }
    }

    private fun repository(api: RefreshEntitlementApi, dao: FeatureAccessCacheDao): TestPlanRefreshRepository {
        val sessions = mockk<AuthenticatedSessionProvider>()
        coEvery { sessions.currentSession() } returns session
        val coordinator = mockk<SessionCoordinator>()
        every { coordinator.currentOwner } returns LocalOwner(userId.toString(), false)
        val keys = object : InstallationSigningKeyProvider {
            override fun getOrCreatePublicIdentity() = InstallationPublicIdentity("spki", "thumbprint")
        }
        val clock = object : OfflineEntitlementClock {
            override fun read() = OfflineEntitlementClockReading(10_000L, 3)
        }
        return TestPlanRefreshRepository(
            api = api,
            sessions = sessions,
            sessionCoordinator = coordinator,
            cache = dao,
            installationKeys = keys,
            clock = clock,
            verifier = mockk(relaxed = true),
            evaluator = mockk(relaxed = true),
        )
    }
}
