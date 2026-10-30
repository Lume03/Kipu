package com.kipu.app.feature.statements.data

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.domain.FeatureAccessPolicy
import com.kipu.app.feature.plans.domain.model.*
import com.kipu.app.feature.statements.domain.StatementImportAccess
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class VerifiedStatementImportAccess @Inject constructor(
    private val session: SessionCoordinator,
    private val cacheDao: FeatureAccessCacheDao,
    private val evaluator: EffectiveEntitlementEvaluator,
    private val policy: FeatureAccessPolicy,
) : StatementImportAccess {
    override suspend fun canImport(ownerId: String): Boolean = coroutineScope {
        val snapshot = session.localAccess.value
        if ((snapshot as? LocalAccess.Available)?.userId != ownerId) return@coroutineScope false
        val owner = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return@coroutineScope false
        val invalidated = AtomicBoolean(false)
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            session.localAccess.collect { if (it !== snapshot) invalidated.set(true) }
        }
        try {
            val cache = cacheDao.get(owner)
            val verified = evaluator.evaluate(ownerId, cache) is OfflineEntitlementLeaseDecision.Allowed
            val decision = policy.evaluate(
                FeatureAccessRequest(Capability.CardStatementImport, 0, FreePlanLimits(),
                    EffectiveEntitlement(verified, cache?.entitlementExpiresAt?.toEpochMilli())),
            )
            return@coroutineScope !invalidated.get() && session.localAccess.value === snapshot && decision is FeatureAccessDecision.Allowed
        } finally {
            observer.cancel()
        }
    }
}
