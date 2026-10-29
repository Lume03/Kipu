package com.kipu.app.feature.capture.data

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.capture.domain.CaptureAccessDecision
import com.kipu.app.feature.capture.domain.CaptureAccessGate
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.CapabilityState
import com.kipu.app.feature.settings.domain.model.ConsentState
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verified gate: session owner, live listener permission, GRANTED account consent and a verified
 * Premium entitlement (same pattern as VerifiedBudgetPlanAccess). Never reads a UI flag.
 */
@Singleton
class VerifiedCaptureAccessGate @Inject constructor(
    private val sessionCoordinator: SessionCoordinator,
    private val permissionGateway: PermissionSourceGateway,
    private val consentDao: PermissionConsentDao,
    private val featureAccessCacheDao: FeatureAccessCacheDao,
    private val entitlementEvaluator: EffectiveEntitlementEvaluator,
) : CaptureAccessGate {
    override fun currentOwnerId(): String? =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId

    override fun liveDenial(ownerId: String): CaptureAccessDecision? = when {
        currentOwnerId() != ownerId -> CaptureAccessDecision.NoSession
        permissionGateway.checkDeviceAuthorization(PermissionSource.OTHER_APP_NOTIFICATION_CONTENT) != DeviceAuthorization.GRANTED -> CaptureAccessDecision.PermissionMissing
        else -> null
    }

    override suspend fun evaluateCurrent(): CaptureAccessDecision {
        val owner = currentOwnerId() ?: return CaptureAccessDecision.NoSession
        return evaluate(owner)
    }

    override suspend fun evaluate(ownerId: String): CaptureAccessDecision = coroutineScope {
        val snapshot = sessionCoordinator.localAccess.value
        val invalidated = AtomicBoolean(false)
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            sessionCoordinator.localAccess.collect { if (it !== snapshot) invalidated.set(true) }
        }
        try { evaluateStable(ownerId) { invalidated.get() || sessionCoordinator.localAccess.value !== snapshot } }
        finally { observer.cancel() }
    }

    private suspend fun evaluateStable(ownerId: String, invalidated: () -> Boolean): CaptureAccessDecision {
        if (currentOwnerId() != ownerId) return CaptureAccessDecision.NoSession
        val userId = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return CaptureAccessDecision.NoSession

        if (permissionGateway.checkDeviceAuthorization(PermissionSource.OTHER_APP_NOTIFICATION_CONTENT) !=
            DeviceAuthorization.GRANTED
        ) {
            return CaptureAccessDecision.PermissionMissing
        }

        val consent = consentDao.findAccountConsent(userId, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name)
        if (consent == null ||
            consent.consentState != ConsentState.GRANTED.name ||
            consent.capabilityState == CapabilityState.DENIED.name
        ) {
            return CaptureAccessDecision.ConsentMissing
        }

        val cache = featureAccessCacheDao.get(userId)
        // Consent may have been revoked while the lease query was suspended.
        if (consentDao.findAccountConsent(userId, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name) != consent) {
            return CaptureAccessDecision.ConsentMissing
        }
        if (invalidated()) return CaptureAccessDecision.NoSession
        liveDenial(ownerId)?.let { return it }
        if (entitlementEvaluator.evaluate(ownerId, cache) !is OfflineEntitlementLeaseDecision.Allowed) {
            return CaptureAccessDecision.PremiumRequired
        }
        if (invalidated()) return CaptureAccessDecision.NoSession
        liveDenial(ownerId)?.let { return it }
        return CaptureAccessDecision.Allowed(
            EffectiveEntitlement(verified = true, expiresAtEpochMillis = cache?.entitlementExpiresAt?.toEpochMilli()),
        )
    }
}
