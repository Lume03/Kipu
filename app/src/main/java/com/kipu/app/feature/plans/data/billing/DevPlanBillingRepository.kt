package com.kipu.app.feature.plans.data.billing

import android.app.Activity
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.entitlement.TestPlanRefresh
import com.kipu.app.feature.plans.data.remote.DevPlanApi
import com.kipu.app.feature.plans.domain.DevPlanOperations
import com.kipu.app.feature.plans.domain.OperationIdProvider
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import com.kipu.app.feature.plans.domain.model.BillingProduct
import com.kipu.app.feature.plans.domain.model.BillingProductKind
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No financial/Play dependencies: only an authenticated command and verified server refresh. */
@Singleton
class DevPlanBillingRepository @Inject constructor(
    private val api: DevPlanApi,
    private val sessions: AuthenticatedSessionProvider,
    private val coordinator: SessionCoordinator,
    private val refresh: TestPlanRefresh,
    private val access: VerifiedPlanAccess,
    private val operationIds: OperationIdProvider,
) : BillingPurchaseRepository, DevPlanOperations {
    private val results = MutableSharedFlow<BillingVerificationResult>(extraBufferCapacity = 8)
    override val purchaseResults = results.asSharedFlow()
    private val mutex = Mutex()
    private var attempt: Attempt? = null

    override suspend fun isDevelopmentMode(): Boolean {
        val session = sessions.currentSession() ?: return false
        check(isCurrentOwner(session.userId)) { "AUTH_SESSION_CHANGED" }
        val enabled = api.mode(session)
        check(isCurrentOwner(session.userId)) { "AUTH_SESSION_CHANGED" }
        return enabled
    }

    override suspend fun loadOffers(): List<LocalizedBillingOffer> = OFFERS

    override suspend fun startPurchase(activity: Activity, offer: LocalizedBillingOffer) {
        results.emit(activate(offer.product.option))
    }

    override suspend fun activate(option: CommercialOption): BillingVerificationResult = mutex.withLock {
        try {
            val session = sessions.currentSession()
                ?: return@withLock BillingVerificationResult.Rejected("UNAUTHENTICATED")
            if (!isCurrentOwner(session.userId)) return@withLock ownerChanged()
            val current = attempt?.takeIf { it.owner == session.userId && it.option == option }
                ?: Attempt(session.userId, option, operationIds.create()).also { attempt = it }
            val response = api.activate(session, current.operationId, planType(option))
            if (!isCurrentOwner(session.userId)) return@withLock ownerChanged()
            if (response.outcome != "APPLIED") {
                if (response.outcome in setOf("CONFLICT", "REJECTED")) {
                    attempt = null
                    return@withLock BillingVerificationResult.Rejected(response.code ?: response.outcome)
                }
                return@withLock BillingVerificationResult.Retryable(response.code ?: "DEV_PLAN_UNAVAILABLE")
            }
            if (response.planType != planType(option) || response.operationId != current.operationId.toString()) {
                return@withLock BillingVerificationResult.Retryable("INVALID_DEV_RESPONSE")
            }
            // Bypass automatic refresh throttling after every explicit plan change.
            val refreshed = refresh.refresh()
            if (!isCurrentOwner(session.userId)) return@withLock ownerChanged()
            if (!refreshed.success) return@withLock BillingVerificationResult.Retryable(refreshed.code ?: "REFRESH_UNAVAILABLE")
            val premium = access.isPremiumVerified(session.userId.toString())
            if (!isCurrentOwner(session.userId)) return@withLock ownerChanged()
            attempt = null
            BillingVerificationResult.DevActivated(productId(option), premium)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            BillingVerificationResult.Retryable("DEV_PLAN_UNAVAILABLE")
        }
    }

    override suspend fun refreshPurchases() {
        val pending = attempt
        if (pending != null && !isCurrentOwner(pending.owner)) results.emit(ownerChanged())
        else if (pending != null) results.emit(activate(pending.option))
        else results.emit(restoreAndVerifyAccess())
    }

    override suspend fun restoreAndVerifyAccess(): BillingVerificationResult {
        val session = sessions.currentSession() ?: return BillingVerificationResult.Rejected("UNAUTHENTICATED")
        if (!isCurrentOwner(session.userId)) return ownerChanged()
        val result = refresh.refresh()
        if (!isCurrentOwner(session.userId)) return ownerChanged()
        if (!result.success) return BillingVerificationResult.Retryable(result.code ?: "REFRESH_UNAVAILABLE")
        val premium = access.isPremiumVerified(session.userId.toString())
        if (!isCurrentOwner(session.userId)) return ownerChanged()
        return BillingVerificationResult.DevActivated("", premium)
    }

    private suspend fun isCurrentOwner(owner: UUID): Boolean =
        sessions.currentSession()?.userId == owner && coordinator.currentOwner?.verifiedUserId == owner.toString()

    private fun ownerChanged(): BillingVerificationResult {
        attempt = null
        return BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED")
    }

    private data class Attempt(val owner: UUID, val option: CommercialOption, val operationId: UUID)

    companion object {
        fun planType(option: CommercialOption): String = when (option) {
            CommercialOption.FREE -> "FREE"
            CommercialOption.MONTHLY -> "PRO_MONTHLY"
            CommercialOption.ANNUAL -> "PRO_ANNUAL"
            CommercialOption.LIFETIME -> "PRO_LIFETIME"
        }

        private fun productId(option: CommercialOption) = "dev:${planType(option)}"

        val OFFERS: List<LocalizedBillingOffer> = CommercialOption.entries.map { option ->
            LocalizedBillingOffer(
                product = BillingProduct(productId(option), productId(option), null,
                    when (option) {
                        CommercialOption.FREE -> "Free"
                        CommercialOption.MONTHLY -> "Mensual"
                        CommercialOption.ANNUAL -> "Anual"
                        CommercialOption.LIFETIME -> "Lifetime"
                    }, option, BillingProductKind.ONE_TIME),
                formattedPrice = "Sin cobro", currencyCode = "PEN", offerToken = null,
                trialPeriodDays = null, renewalPeriod = null,
            )
        }
    }
}
