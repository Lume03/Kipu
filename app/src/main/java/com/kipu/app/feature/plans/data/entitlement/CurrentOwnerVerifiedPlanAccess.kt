package com.kipu.app.feature.plans.data.entitlement

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentOwnerVerifiedPlanAccess @Inject constructor(
    private val cacheDao: FeatureAccessCacheDao,
    private val evaluator: EffectiveEntitlementEvaluator,
    private val session: SessionCoordinator,
) : VerifiedPlanAccess {
    private fun currentOwner(): String? = (session.localAccess.value as? LocalAccess.Available)?.userId

    override suspend fun isPremiumVerified(ownerId: String?): Boolean {
        val owner = ownerId ?: currentOwner() ?: return false
        if (currentOwner() != owner) return false
        val uuid = runCatching { UUID.fromString(owner) }.getOrNull() ?: return false
        val cache = cacheDao.get(uuid)
        return currentOwner() == owner && evaluator.evaluate(owner, cache) is OfflineEntitlementLeaseDecision.Allowed
    }

    override fun observePremiumVerified(): Flow<Boolean> = session.localAccess.flatMapLatest { access ->
        val owner = (access as? LocalAccess.Available)?.userId
        val uuid = owner?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (owner == null || uuid == null) flowOf(false)
        else combine(cacheDao.observe(uuid), offlineEntitlementRefreshTicker()) { cache, _ ->
            currentOwner() == owner && evaluator.evaluate(owner, cache) is OfflineEntitlementLeaseDecision.Allowed
        }
    }.distinctUntilChanged()
}
