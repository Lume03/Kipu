package com.kipu.app.feature.plans.data.billing

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.entitlement.TestPlanRefresh
import com.kipu.app.feature.plans.data.entitlement.TestPlanRefreshResult
import com.kipu.app.feature.plans.data.remote.DevPlanActivationDto
import com.kipu.app.feature.plans.data.remote.DevPlanApi
import com.kipu.app.feature.plans.domain.OperationIdProvider
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.CommercialOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DevPlanBillingRepositoryTest {
    private val ownerId = UUID.randomUUID()
    private var owner = ownerId
    private val operation = UUID.randomUUID()
    private val api = mockk<DevPlanApi>()
    private val sessions = mockk<AuthenticatedSessionProvider>()
    private val coordinator = mockk<SessionCoordinator>()
    private val refresh = mockk<TestPlanRefresh>()
    private val access = mockk<VerifiedPlanAccess>()
    private val repository = DevPlanBillingRepository(api, sessions, coordinator, refresh, access, OperationIdProvider { operation })

    init {
        coEvery { sessions.currentSession() } answers { AuthenticatedSession(owner, "jwt") }
        every { coordinator.currentOwner } answers { LocalOwner(owner.toString(), false) }
        coEvery { refresh.refresh() } returns TestPlanRefreshResult(true)
        coEvery { access.isPremiumVerified(any()) } returns true
        coEvery { api.activate(any(), any(), any()) } coAnswers {
            DevPlanActivationDto("APPLIED", planType = thirdArg(), operationId = secondArg<UUID>().toString())
        }
    }

    @Test fun `activation refreshes verified access without inventing a Play purchase`() = runTest {
        val result = repository.activate(CommercialOption.LIFETIME)
        assertEquals(BillingVerificationResult.DevActivated("dev:PRO_LIFETIME", true), result)
        coVerify(exactly = 1) { api.activate(AuthenticatedSession(ownerId,"jwt"),operation,"PRO_LIFETIME") }
        coVerify(exactly = 1) { refresh.refresh() }
        assertEquals(4, repository.loadOffers().size)
    }

    @Test fun `Free downgrade obtains verified Free and has no financial dependency`() = runTest {
        coEvery { access.isPremiumVerified(ownerId.toString()) } returns false
        assertEquals(BillingVerificationResult.DevActivated("dev:FREE",false),repository.activate(CommercialOption.FREE))
        coVerify { api.activate(any(),operation,"FREE") }
        coVerify(exactly = 1) { refresh.refresh() }
    }

    @Test fun `disabled mode and conflict are terminal and never refresh entitlement`() = runTest {
        for ((outcome,code) in listOf("REJECTED" to "DEV_MODE_DISABLED", "CONFLICT" to "OPERATION_CONFLICT")) {
            coEvery { api.activate(any(),any(),any()) } returns DevPlanActivationDto(outcome,code)
            assertEquals(BillingVerificationResult.Rejected(code),repository.activate(CommercialOption.MONTHLY))
        }
        coVerify(exactly = 0) { refresh.refresh() }
    }

    @Test fun `transient activation and refresh failures reuse the operation identity`() = runTest {
        coEvery { api.activate(any(),any(),any()) } returnsMany listOf(
            DevPlanActivationDto("RETRYABLE","DEV_PLAN_UNAVAILABLE"),
            DevPlanActivationDto("APPLIED",planType="PRO_ANNUAL",operationId=operation.toString()),
            DevPlanActivationDto("APPLIED",planType="PRO_ANNUAL",operationId=operation.toString()),
        )
        coEvery { refresh.refresh() } returnsMany listOf(TestPlanRefreshResult(false,"NETWORK"),TestPlanRefreshResult(true))
        assertTrue(repository.activate(CommercialOption.ANNUAL) is BillingVerificationResult.Retryable)
        assertTrue(repository.activate(CommercialOption.ANNUAL) is BillingVerificationResult.Retryable)
        assertTrue(repository.activate(CommercialOption.ANNUAL) is BillingVerificationResult.DevActivated)
        coVerify(exactly = 3) { api.activate(any(),operation,"PRO_ANNUAL") }
    }

    @Test fun `cancellation propagates and does not refresh`() = runTest {
        coEvery { api.activate(any(),any(),any()) } throws CancellationException("cancel")
        try {
            repository.activate(CommercialOption.ANNUAL)
            fail("expected cancellation")
        } catch (_: CancellationException) { }
        coVerify(exactly = 0) { refresh.refresh() }
    }

    @Test fun `owner change while activating cannot refresh or apply another user plan`() = runTest {
        coEvery { api.activate(any(),any(),any()) } coAnswers {
            owner = UUID.randomUUID()
            DevPlanActivationDto("APPLIED",planType="PRO_MONTHLY",operationId=operation.toString())
        }
        assertEquals(BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED"),repository.activate(CommercialOption.MONTHLY))
        coVerify(exactly = 0) { refresh.refresh() }
        coVerify(exactly = 0) { access.isPremiumVerified(any()) }
    }

    @Test fun `mode is read from server for the current authenticated owner`() = runTest {
        coEvery { api.mode(any()) } returnsMany listOf(true,false)
        assertTrue(repository.isDevelopmentMode())
        assertFalse(repository.isDevelopmentMode())
    }

    @Test fun `retry cannot transfer an unresolved attempt to a newly signed in owner`() = runTest {
        coEvery { api.activate(any(),any(),any()) } returns DevPlanActivationDto("RETRYABLE","NETWORK")
        repository.activate(CommercialOption.ANNUAL)
        owner = UUID.randomUUID()
        val result = async { repository.purchaseResults.first() }
        runCurrent()
        repository.refreshPurchases()
        assertEquals(BillingVerificationResult.Rejected("AUTH_SESSION_CHANGED"),result.await())
        coVerify(exactly = 1) { api.activate(any(),any(),any()) }
    }
}
