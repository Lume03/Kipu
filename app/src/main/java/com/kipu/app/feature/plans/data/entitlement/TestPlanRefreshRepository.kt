package com.kipu.app.feature.plans.data.entitlement

import com.kipu.app.BuildConfig
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.data.remote.RefreshEntitlementApi
import com.kipu.app.feature.plans.data.remote.RefreshEntitlementApiResult
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

data class TestPlanRefreshResult(val success: Boolean, val code: String? = null)

interface TestPlanRefresh {
    suspend fun refresh(): TestPlanRefreshResult
}

@Singleton
class TestPlanRefreshRepository @Inject constructor(
    private val api: RefreshEntitlementApi,
    private val sessions: AuthenticatedSessionProvider,
    private val sessionCoordinator: SessionCoordinator,
    private val cache: FeatureAccessCacheDao,
    private val installationKeys: InstallationSigningKeyProvider,
    private val clock: OfflineEntitlementClock,
    private val verifier: OfflineEntitlementGrantVerifier,
    private val evaluator: EffectiveEntitlementEvaluator,
) : TestPlanRefresh {
    override suspend fun refresh(): TestPlanRefreshResult {
        if (!BuildConfig.TEST_PLAN_OVERRIDES_ENABLED) return TestPlanRefreshResult(false, "TEST_PLANS_DISABLED")
        val session = sessions.currentSession() ?: return TestPlanRefreshResult(false, "UNAUTHENTICATED")
        if (!isCurrentSessionOwner(session.userId.toString())) return TestPlanRefreshResult(false, "SESSION_CHANGED")
        try {
            val installation = installationKeys.getOrCreatePublicIdentity()
            val anchor = clock.read()
            val response = when (val result = api.refresh(session, installation.publicKeyBase64)) {
                is RefreshEntitlementApiResult.Failure -> return TestPlanRefreshResult(false, result.code)
                is RefreshEntitlementApiResult.Success -> result.value
            }
            if (!isCurrentSessionOwner(session.userId.toString())) return TestPlanRefreshResult(false, "SESSION_CHANGED")
            if (response.source !in setOf("TEST_OVERRIDE", "COMMERCIAL")) return invalid()
            val entry = when (response.effectiveTier) {
                "FREE" -> {
                    if (response.offlineEntitlementGrant != null || response.expiresAt != null) return invalid()
                    FeatureAccessCacheEntity.verifiedFree(session.userId).copy(
                        source = if (response.source == "TEST_OVERRIDE") "TEST_OVERRIDE" else "VERIFIED_SERVER",
                    )
                }
                "PREMIUM" -> {
                    val signed = response.offlineEntitlementGrant?.asSignedGrant() ?: return invalid()
                    val claims = verifier.verify(signed, session.userId.toString()) ?: return invalid()
                    if ((response.source == "TEST_OVERRIDE") != (claims.source == "TEST_OVERRIDE")) return invalid()
                    val responseExpiry = response.expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    if (response.expiresAt != null && responseExpiry == null) return invalid()
                    if (responseExpiry != claims.entitlementEndsAtMillis) return invalid()
                    val currentAnchor = clock.read()
                    if (currentAnchor.bootCount == null || anchor.bootCount == null ||
                        currentAnchor.bootCount != anchor.bootCount || currentAnchor.elapsedRealtimeMillis < anchor.elapsedRealtimeMillis
                    ) return TestPlanRefreshResult(false, "OFFLINE_CLOCK_UNAVAILABLE")
                    val verified = FeatureAccessCacheEntity.fromVerifiedGrant(
                        userId = session.userId,
                        claims = claims,
                        grant = signed,
                        anchorElapsedRealtimeMillis = anchor.elapsedRealtimeMillis,
                        anchorBootCount = anchor.bootCount,
                    ).copy(source = if (response.source == "TEST_OVERRIDE") "SIGNED_TEST_GRANT" else "SIGNED_SERVER_GRANT")
                    if (evaluator.evaluate(session.userId.toString(), verified) !is OfflineEntitlementLeaseDecision.Allowed) return invalid()
                    verified
                }
                else -> return invalid()
            }
            if (!isCurrentSessionOwner(session.userId.toString())) return TestPlanRefreshResult(false, "SESSION_CHANGED")
            cache.putVerified(entry)
            return TestPlanRefreshResult(true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return TestPlanRefreshResult(false, "REFRESH_UNAVAILABLE")
        }
    }

    private suspend fun isCurrentSessionOwner(userId: String): Boolean =
        sessions.currentSession()?.userId?.toString() == userId &&
            sessionCoordinator.currentOwner?.verifiedUserId == userId

    private fun invalid() = TestPlanRefreshResult(false, "INVALID_VERIFICATION_RESPONSE")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TestPlanRefreshModule {
    @Binds abstract fun testPlanRefresh(value: TestPlanRefreshRepository): TestPlanRefresh
}
