package com.kipu.app.feature.plans.data.billing

import android.app.Activity
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge

/** Catalog owner and server mode are rechecked before a command can reach either provider. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeBillingPurchaseRepository(
    private val play: BillingPurchaseRepository,
    private val development: BillingPurchaseRepository,
    private val sessions: AuthenticatedSessionProvider,
) : BillingPurchaseRepository {
    @Inject constructor(play: BillingRepository, development: DevPlanBillingRepository, sessions: AuthenticatedSessionProvider) :
        this(play as BillingPurchaseRepository, development as BillingPurchaseRepository, sessions)

    private val selected = MutableStateFlow<BillingPurchaseRepository?>(null)
    private val errors = MutableSharedFlow<BillingVerificationResult>(extraBufferCapacity = 8)
    private var catalogOwner: UUID? = null
    override val purchaseResults = merge(selected.flatMapLatest { it?.purchaseResults ?: emptyFlow() }, errors)

    override suspend fun isDevelopmentMode(): Boolean = development.isDevelopmentMode()

    override suspend fun loadOffers(): List<LocalizedBillingOffer> {
        val owner = sessions.currentSession()?.userId ?: return emptyList()
        val provider = if (isDevelopmentMode()) development else play
        if (sessions.currentSession()?.userId != owner) return emptyList()
        selected.value = provider
        val offers = provider.loadOffers()
        if (sessions.currentSession()?.userId != owner) {
            selected.value = null
            catalogOwner = null
            return emptyList()
        }
        catalogOwner = owner
        return offers
    }

    override suspend fun startPurchase(activity: Activity, offer: LocalizedBillingOffer) {
        if (catalogOwner == null || sessions.currentSession()?.userId != catalogOwner) {
            errors.emit(BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED"))
            return
        }
        val devMode = isDevelopmentMode()
        if (sessions.currentSession()?.userId != catalogOwner) {
            errors.emit(BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED"))
            return
        }
        if (devMode != offer.product.storeProductId.startsWith("dev:")) {
            errors.emit(BillingVerificationResult.Rejected(if (devMode) "CATALOG_CHANGED" else "DEV_MODE_DISABLED"))
            return
        }
        (if (devMode) development else play).startPurchase(activity, offer)
    }

    override suspend fun refreshPurchases() {
        if (catalogOwner == null || sessions.currentSession()?.userId != catalogOwner) {
            errors.emit(BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED"))
            return
        }
        val provider = if (isDevelopmentMode()) development else play
        if (provider !== selected.value) {
            errors.emit(BillingVerificationResult.Rejected(if (provider === play) "DEV_MODE_DISABLED" else "CATALOG_CHANGED"))
            return
        }
        if (sessions.currentSession()?.userId != catalogOwner) {
            errors.emit(BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED"))
            return
        }
        provider.refreshPurchases()
    }

    override suspend fun restoreAndVerifyAccess(): BillingVerificationResult =
        (if (isDevelopmentMode()) development else play).restoreAndVerifyAccess()
}
