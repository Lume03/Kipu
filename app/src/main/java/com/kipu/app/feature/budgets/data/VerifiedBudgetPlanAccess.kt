package com.kipu.app.feature.budgets.data

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.domain.BudgetPlanAccess
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import java.util.UUID
import javax.inject.Inject

class VerifiedBudgetPlanAccess @Inject constructor(
    private val featureAccessCacheDao: FeatureAccessCacheDao,
    private val entitlementEvaluator: EffectiveEntitlementEvaluator,
    private val sessionCoordinator: SessionCoordinator,
) : BudgetPlanAccess {
    override suspend fun isPremiumVerified(ownerId: String): Boolean {
        if ((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId != ownerId) return false
        val userId = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return false
        return entitlementEvaluator.evaluate(ownerId, featureAccessCacheDao.get(userId)) is OfflineEntitlementLeaseDecision.Allowed
    }
}
