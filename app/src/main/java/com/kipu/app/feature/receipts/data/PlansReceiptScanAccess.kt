package com.kipu.app.feature.receipts.data

import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import com.kipu.app.feature.receipts.domain.ReceiptScanAccess
import com.kipu.app.feature.receipts.domain.ReceiptScanAccessPolicy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlansReceiptScanAccess @Inject constructor(
    private val cacheDao: FeatureAccessCacheDao,
    private val leaseEvaluator: EffectiveEntitlementEvaluator,
    private val policy: ReceiptScanAccessPolicy,
) : ReceiptScanAccess {
    override suspend fun canScan(ownerId: String): Boolean {
        val owner = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return false
        val cache = cacheDao.get(owner) ?: return false
        if (leaseEvaluator.evaluate(owner.toString(), cache) !is OfflineEntitlementLeaseDecision.Allowed) return false
        return policy.allows(EffectiveEntitlement(verified = true, expiresAtEpochMillis = cache.entitlementExpiresAt?.toEpochMilli()))
    }
}
