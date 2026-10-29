package com.kipu.app.feature.capture.data

import android.content.Intent
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.capture.domain.CaptureAccessDecision
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.CapabilityState
import com.kipu.app.feature.settings.domain.model.ConsentState
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerifiedCaptureAccessGateTest {
    private val owner = UUID.randomUUID()
    private val ownerId = owner.toString()

    private val access = MutableStateFlow<LocalAccess>(LocalAccess.Available(ownerId, RemoteSession.Valid(ownerId, Instant.MAX)))
    private val session = object : SessionCoordinator {
        override val remoteSession: StateFlow<RemoteSession> = MutableStateFlow(RemoteSession.Absent)
        override val localAccess: StateFlow<LocalAccess> = access
        override val currentOwner: LocalOwner? = null
        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }

    private var devicePermission = DeviceAuthorization.GRANTED
    private val permissions = object : PermissionSourceGateway {
        override fun checkDeviceAuthorization(source: PermissionSource) = devicePermission
        override fun createSettingsIntent(source: PermissionSource): Intent = error("unused")
        override suspend fun refreshAndSaveDeviceAuthorizations() = emptyMap<PermissionSource, DeviceAuthorization>()
    }

    private var consent: AccountSourceConsentEntity? = consentEntity(ConsentState.GRANTED, CapabilityState.ALLOWED)
    private val consentDao = mockk<PermissionConsentDao>().also {
        coEvery { it.findAccountConsent(any(), any()) } answers { consent }
    }

    private var cache: FeatureAccessCacheEntity? = FeatureAccessCacheEntity(
        userId = owner,
        policyVersion = 1,
        effectiveTier = "PREMIUM",
        entitlementExpiresAt = Instant.ofEpochMilli(5_000),
    )
    private val cacheDao = mockk<FeatureAccessCacheDao>().also { coEvery { it.get(any()) } answers { cache } }

    private var lease: OfflineEntitlementLeaseDecision = OfflineEntitlementLeaseDecision.Allowed(1_000)
    private val evaluator = EffectiveEntitlementEvaluator { _, _ -> lease }

    private val gate = VerifiedCaptureAccessGate(session, permissions, consentDao, cacheDao, evaluator)

    private fun consentEntity(state: ConsentState, capability: CapabilityState) = AccountSourceConsentEntity(
        userId = owner,
        source = PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name,
        consentState = state.name,
        capabilityState = capability.name,
    )

    @Test fun `allows with verified entitlement carrying its known expiry`() = runBlocking {
        val decision = gate.evaluateCurrent()
        assertEquals(CaptureAccessDecision.Allowed(EffectiveEntitlement(true, 5_000)), decision)
    }

    @Test fun `no session denies`() = runBlocking {
        access.value = LocalAccess.NoOwner
        assertNull(gate.currentOwnerId())
        assertEquals(CaptureAccessDecision.NoSession, gate.evaluateCurrent())
    }

    @Test fun `locked session denies`() = runBlocking {
        access.value = LocalAccess.Protected(ownerId, "LOCKED")
        assertEquals(CaptureAccessDecision.NoSession, gate.evaluateCurrent())
    }

    @Test fun `another owner than the session owner is denied`() = runBlocking {
        assertEquals(CaptureAccessDecision.NoSession, gate.evaluate(UUID.randomUUID().toString()))
    }

    @Test fun `missing listener permission denies`() = runBlocking {
        devicePermission = DeviceAuthorization.DENIED
        assertEquals(CaptureAccessDecision.PermissionMissing, gate.evaluateCurrent())
    }

    @Test fun `consent not granted denies`() = runBlocking {
        for (state in listOf(ConsentState.NOT_EXPLAINED, ConsentState.EXPLAINED, ConsentState.DENIED, ConsentState.REVOKED)) {
            consent = consentEntity(state, CapabilityState.ALLOWED)
            assertEquals(CaptureAccessDecision.ConsentMissing, gate.evaluateCurrent())
        }
        consent = null
        assertEquals(CaptureAccessDecision.ConsentMissing, gate.evaluateCurrent())
    }

    @Test fun `denied capability denies even with granted consent`() = runBlocking {
        consent = consentEntity(ConsentState.GRANTED, CapabilityState.DENIED)
        assertEquals(CaptureAccessDecision.ConsentMissing, gate.evaluateCurrent())
    }

    @Test fun `unverified or expired lease requires premium`() = runBlocking {
        lease = OfflineEntitlementLeaseDecision.PremiumRequired
        assertEquals(CaptureAccessDecision.PremiumRequired, gate.evaluateCurrent())
        lease = OfflineEntitlementLeaseDecision.RevalidationRequired(OfflineEntitlementLeaseDecision.Reason.LEASE_EXPIRED)
        assertEquals(CaptureAccessDecision.PremiumRequired, gate.evaluateCurrent())
    }

    @Test fun `revoking consent after allowing stops processing on the next evaluation`() = runBlocking {
        assertEquals(true, gate.evaluateCurrent() is CaptureAccessDecision.Allowed)
        consent = consentEntity(ConsentState.REVOKED, CapabilityState.ALLOWED)
        assertEquals(CaptureAccessDecision.ConsentMissing, gate.evaluateCurrent())
    }

    @Test fun `permission revoked while cache query is suspended invalidates the result`() = runBlocking {
        coEvery { cacheDao.get(any()) } coAnswers {
            kotlinx.coroutines.yield()
            devicePermission = DeviceAuthorization.DENIED
            cache
        }
        assertEquals(CaptureAccessDecision.PermissionMissing, gate.evaluate(ownerId))
    }

    @Test fun `locked session while consent query is suspended invalidates the result`() = runBlocking {
        coEvery { consentDao.findAccountConsent(any(), any()) } coAnswers {
            kotlinx.coroutines.yield()
            access.value = LocalAccess.Protected(ownerId, "LOCKED")
            consent
        }
        assertEquals(CaptureAccessDecision.NoSession, gate.evaluate(ownerId))
    }

    @Test fun `consent revoked during cache query is checked again before allow`() = runBlocking {
        coEvery { cacheDao.get(any()) } coAnswers {
            consent = consentEntity(ConsentState.REVOKED, CapabilityState.DENIED)
            cache
        }
        assertEquals(CaptureAccessDecision.ConsentMissing, gate.evaluate(ownerId))
    }
}
