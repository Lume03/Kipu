package com.kipu.app.feature.plans.presentation

import android.app.Activity
import com.kipu.app.feature.plans.data.billing.DevPlanBillingRepository
import com.kipu.app.feature.plans.domain.ConfirmPlanSelection
import com.kipu.app.feature.plans.domain.DevPlanOperations
import com.kipu.app.feature.plans.domain.OperationIdProvider
import com.kipu.app.feature.plans.domain.PlanPreferencesRepository
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.PlanSelection
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import com.kipu.app.feature.plans.purchase.BillingPurchaseRepository
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DevPlanViewModelsTest {
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `selection in development activates before confirming the inert preference`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = preferences()
        val dev = mockk<DevPlanOperations>()
        coEvery { dev.isDevelopmentMode() } returns true
        coEvery { dev.activate(CommercialOption.LIFETIME) } returns BillingVerificationResult.DevActivated("dev:PRO_LIFETIME",true)
        val model = PlanSelectionViewModel(preferences,ConfirmPlanSelection(preferences,OperationIdProvider(UUID::randomUUID)),dev)
        advanceUntilIdle()
        assertTrue(model.uiState.value.developmentMode)
        model.onOptionSelected(CommercialOption.LIFETIME)
        model.confirmSelection()
        advanceUntilIdle()
        coVerifyOrder {
            dev.activate(CommercialOption.LIFETIME)
            preferences.confirmSelection(PlanSelection.PREMIUM_INTENT,any())
        }
    }

    @Test fun `selection with gate off preserves intent without activating`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = preferences()
        val dev = mockk<DevPlanOperations>()
        coEvery { dev.isDevelopmentMode() } returns false
        val model = PlanSelectionViewModel(preferences,ConfirmPlanSelection(preferences,OperationIdProvider(UUID::randomUUID)),dev)
        advanceUntilIdle()
        assertFalse(model.uiState.value.developmentMode)
        model.continueWithFree()
        advanceUntilIdle()
        coVerify { preferences.confirmSelection(PlanSelection.FREE,any()) }
        coVerify(exactly=0) { dev.activate(any()) }
    }

    @Test fun `failed development activation does not report onboarding success`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = preferences()
        val dev = mockk<DevPlanOperations>()
        coEvery { dev.isDevelopmentMode() } returns true
        coEvery { dev.activate(any()) } returns BillingVerificationResult.Rejected("DEV_MODE_DISABLED")
        val model = PlanSelectionViewModel(preferences,ConfirmPlanSelection(preferences,OperationIdProvider(UUID::randomUUID)),dev)
        advanceUntilIdle()
        model.continueWithFree()
        advanceUntilIdle()
        assertEquals(PlanSelectionError.CONFIRMATION_FAILED,model.uiState.value.error)
        coVerify(exactly=0) { preferences.confirmSelection(any(),any()) }
    }

    @Test fun `mode lookup failure blocks confirmation until retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val preferences = preferences()
        val dev = mockk<DevPlanOperations>()
        coEvery { dev.isDevelopmentMode() } throws IllegalStateException("network")
        val model = PlanSelectionViewModel(preferences,ConfirmPlanSelection(preferences,OperationIdProvider(UUID::randomUUID)),dev)
        advanceUntilIdle()
        assertFalse(model.uiState.value.canConfirm)
        assertTrue(model.uiState.value.modeError)
        coEvery { dev.isDevelopmentMode() } returns false
        model.loadMode()
        advanceUntilIdle()
        assertTrue(model.uiState.value.canConfirm)
    }

    @Test fun `purchase model exposes all development plans and can downgrade after Premium`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<BillingPurchaseRepository>()
        val results = MutableSharedFlow<BillingVerificationResult>()
        every { repository.purchaseResults } returns results
        coEvery { repository.loadOffers() } returns DevPlanBillingRepository.OFFERS
        coEvery { repository.startPurchase(any(),any()) } coAnswers {
            results.emit(BillingVerificationResult.DevActivated("dev:FREE",false))
        }
        val model = PlanPurchaseViewModel(repository)
        advanceUntilIdle()
        assertTrue(model.uiState.value.developmentMode)
        assertEquals(4,model.uiState.value.offers.size)
        results.emit(BillingVerificationResult.DevActivated("dev:PRO_LIFETIME",true))
        advanceUntilIdle()
        assertTrue(model.uiState.value.effectivePremium)
        model.selectOffer("dev:FREE")
        model.startPurchase(mockk<Activity>())
        advanceUntilIdle()
        assertFalse(model.uiState.value.effectivePremium)
        assertNull(model.uiState.value.providerPurchaseState)
        assertEquals(PlanPurchaseStatus.VERIFIED,model.uiState.value.status)
    }

    private fun preferences(): PlanPreferencesRepository = mockk<PlanPreferencesRepository>().also {
        every { it.currentPreference } returns flowOf(null)
        coEvery { it.getTrialEligibility() } returns TrialEligibilitySnapshot.UNKNOWN
        coEvery { it.confirmSelection(any(),any()) } returns Unit
    }
}
