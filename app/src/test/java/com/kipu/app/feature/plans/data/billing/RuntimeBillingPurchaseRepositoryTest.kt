package com.kipu.app.feature.plans.data.billing

import android.app.Activity
import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.plans.domain.model.BillingProduct
import com.kipu.app.feature.plans.domain.model.BillingProductKind
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.LocalizedBillingOffer
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RuntimeBillingPurchaseRepositoryTest {
    private val play = mockk<BillingPurchaseRepository>()
    private val dev = mockk<BillingPurchaseRepository>()
    private val sessions = mockk<AuthenticatedSessionProvider>()
    private var owner = UUID.randomUUID()
    private var enabled = true
    private val repository = RuntimeBillingPurchaseRepository(play,dev,sessions)
    private val activity = mockk<Activity>()
    private val devOffer = DevPlanBillingRepository.OFFERS.first()
    private val playOffer = LocalizedBillingOffer(BillingProduct("p","play","monthly","Monthly",CommercialOption.MONTHLY,BillingProductKind.SUBSCRIPTION),"PEN 5","PEN","token",null,null)

    init {
        coEvery { sessions.currentSession() } answers { AuthenticatedSession(owner,"jwt") }
        coEvery { dev.isDevelopmentMode() } answers { enabled }
        coEvery { dev.loadOffers() } returns DevPlanBillingRepository.OFFERS
        coEvery { play.loadOffers() } returns listOf(playOffer)
        coEvery { dev.startPurchase(any(),any()) } returns Unit
        coEvery { play.startPurchase(any(),any()) } returns Unit
        every { play.purchaseResults } returns emptyFlow()
        every { dev.purchaseResults } returns emptyFlow()
    }

    @Test fun `enabled mode loads and executes development without invoking Play`() = runTest {
        assertEquals(4, repository.loadOffers().size)
        repository.startPurchase(activity,devOffer)
        coVerify(exactly=1) { dev.startPurchase(activity,devOffer) }
        coVerify(exactly=0) { play.loadOffers() }
        coVerify(exactly=0) { play.startPurchase(any(),any()) }
    }

    @Test fun `disabled mode preserves Play catalog and purchase`() = runTest {
        enabled = false
        assertEquals(listOf(playOffer),repository.loadOffers())
        repository.startPurchase(activity,playOffer)
        coVerify(exactly=1) { play.startPurchase(activity,playOffer) }
        coVerify(exactly=0) { dev.loadOffers() }
    }

    @Test fun `switching off after catalog never turns a free activation into a Play charge`() = runTest {
        repository.loadOffers()
        val result = async { repository.purchaseResults.first() }
        runCurrent()
        enabled = false
        repository.startPurchase(activity,devOffer)
        assertEquals(BillingVerificationResult.Rejected("DEV_MODE_DISABLED"),result.await())
        coVerify(exactly=0) { play.startPurchase(any(),any()) }
        coVerify(exactly=0) { dev.startPurchase(any(),any()) }
    }

    @Test fun `owner change invalidates catalog before either provider executes`() = runTest {
        repository.loadOffers()
        val result = async { repository.purchaseResults.first() }
        runCurrent()
        owner = UUID.randomUUID()
        repository.startPurchase(activity,devOffer)
        assertEquals(BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED"),result.await())
        coVerify(exactly=0) { dev.startPurchase(any(),any()) }
    }
}
