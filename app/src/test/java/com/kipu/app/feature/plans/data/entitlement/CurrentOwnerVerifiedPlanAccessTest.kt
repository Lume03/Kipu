package com.kipu.app.feature.plans.data.entitlement

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentOwnerVerifiedPlanAccessTest {
    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val sessionState = MutableStateFlow<LocalAccess>(LocalAccess.Available(owner.toString(), RemoteSession.Absent))
    private val session = mockk<SessionCoordinator> { every { localAccess } returns sessionState }
    private val dao = mockk<FeatureAccessCacheDao>()

    @Test
    fun `only Allowed grants lift quota for server and developer sources`() = runTest {
        for (source in listOf("SIGNED_SERVER_GRANT", "SIGNED_DEVELOPER_GRANT")) {
            val cache = FeatureAccessCacheEntity(owner, 1, "PREMIUM", source = source)
            coEvery { dao.get(owner) } returns cache
            var decision: OfflineEntitlementLeaseDecision = OfflineEntitlementLeaseDecision.Allowed(1)
            val access = CurrentOwnerVerifiedPlanAccess(dao, EffectiveEntitlementEvaluator { _, _ -> decision }, session)
            assertTrue(access.isPremiumVerified())
            assertTrue(access.isPremiumVerified(owner.toString()))
            decision = OfflineEntitlementLeaseDecision.RevalidationRequired(OfflineEntitlementLeaseDecision.Reason.LEASE_EXPIRED)
            assertFalse(access.isPremiumVerified())
            decision = OfflineEntitlementLeaseDecision.PremiumRequired
            assertFalse(access.isPremiumVerified())
            assertFalse(CurrentOwnerVerifiedPlanAccess(dao, DenyUnverifiedEntitlementEvaluator, session).isPremiumVerified())
        }
    }

    @Test
    fun `missing or wrong session never queries another owners cache`() = runTest {
        val access = CurrentOwnerVerifiedPlanAccess(dao, EffectiveEntitlementEvaluator { _, _ -> OfflineEntitlementLeaseDecision.Allowed(1) }, session)
        assertFalse(access.isPremiumVerified(other.toString()))
        sessionState.value = LocalAccess.Protected(owner.toString(), "LOCKED")
        assertFalse(access.isPremiumVerified(owner.toString()))
        sessionState.value = LocalAccess.NoOwner
        assertFalse(access.isPremiumVerified())
        coVerify(exactly = 0) { dao.get(any()) }
    }

    @Test
    fun `owner switch while cache loads denies the old owners entitlement`() = runTest {
        coEvery { dao.get(owner) } coAnswers {
            sessionState.value = LocalAccess.Available(other.toString(), RemoteSession.Absent)
            FeatureAccessCacheEntity(owner, 1, "PREMIUM")
        }
        val access = CurrentOwnerVerifiedPlanAccess(dao, EffectiveEntitlementEvaluator { _, _ -> OfflineEntitlementLeaseDecision.Allowed(1) }, session)
        assertFalse(access.isPremiumVerified())
    }

    @Test
    fun `observable entitlement follows grants revocation session switch and sign out`() = runTest {
        val cache = MutableStateFlow<FeatureAccessCacheEntity?>(FeatureAccessCacheEntity.verifiedFree(owner))
        val otherCache = MutableStateFlow<FeatureAccessCacheEntity?>(null)
        every { dao.observe(owner) } returns cache
        every { dao.observe(other) } returns otherCache
        val access = CurrentOwnerVerifiedPlanAccess(dao, EffectiveEntitlementEvaluator { _, entry ->
            if (entry?.effectiveTier == "PREMIUM") OfflineEntitlementLeaseDecision.Allowed(1)
            else OfflineEntitlementLeaseDecision.PremiumRequired
        }, session)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { access.observePremiumVerified().collect { values += it } }
        testScheduler.runCurrent()
        assertEquals(false, values.last())
        cache.value = FeatureAccessCacheEntity(owner, 1, "PREMIUM", source = "SIGNED_DEVELOPER_GRANT")
        testScheduler.runCurrent()
        assertEquals(true, values.last())
        cache.value = FeatureAccessCacheEntity.verifiedFree(owner)
        testScheduler.runCurrent()
        assertEquals(false, values.last())
        cache.value = FeatureAccessCacheEntity(owner, 1, "PREMIUM", source = "SIGNED_SERVER_GRANT")
        testScheduler.runCurrent()
        assertEquals(true, values.last())
        sessionState.value = LocalAccess.Available(other.toString(), RemoteSession.Absent)
        testScheduler.runCurrent()
        assertEquals(false, values.last())
        sessionState.value = LocalAccess.NoOwner
        testScheduler.runCurrent()
        assertEquals(false, values.last())
    }
}
