package com.kipu.app.feature.statements

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.domain.FeatureAccessPolicy
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.statements.data.VerifiedStatementImportAccess
import io.mockk.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VerifiedStatementImportAccessTest {
    private val owner = UUID.randomUUID().toString()
    private val state = MutableStateFlow<LocalAccess>(LocalAccess.Available(owner, RemoteSession.Valid(owner, Instant.MAX)))
    private val session = mockk<SessionCoordinator> { every { localAccess } returns state }
    private val cache = mockk<FeatureAccessCacheDao>().also { dao -> coEvery { dao.get(any()) } returns null }
    private var decision: OfflineEntitlementLeaseDecision = OfflineEntitlementLeaseDecision.Allowed(1000)
    private val gate = VerifiedStatementImportAccess(session, cache, EffectiveEntitlementEvaluator { _, _ -> decision }, FeatureAccessPolicy())

    @Test fun `verified lease allows and unverified or expired lease denies`() = runTest {
        assertTrue(gate.canImport(owner))
        decision = OfflineEntitlementLeaseDecision.PremiumRequired
        assertFalse(gate.canImport(owner))
        decision = OfflineEntitlementLeaseDecision.RevalidationRequired(OfflineEntitlementLeaseDecision.Reason.LEASE_EXPIRED)
        assertFalse(gate.canImport(owner))
    }

    @Test fun `no owner protected session wrong owner and malformed owner deny`() = runTest {
        assertFalse(gate.canImport(UUID.randomUUID().toString()))
        state.value = LocalAccess.NoOwner
        assertFalse(gate.canImport(owner))
        state.value = LocalAccess.Protected(owner, "LOCKED")
        assertFalse(gate.canImport(owner))
        state.value = LocalAccess.Available("invalid", RemoteSession.Absent)
        assertFalse(gate.canImport("invalid"))
        coVerify(exactly = 0) { cache.get(any()) }
    }

    @Test fun `session change during suspended entitlement lookup denies`() = runTest {
        coEvery { cache.get(any()) } coAnswers {
            kotlinx.coroutines.yield()
            state.value = LocalAccess.NoOwner
            null
        }
        assertFalse(gate.canImport(owner))
    }
}
