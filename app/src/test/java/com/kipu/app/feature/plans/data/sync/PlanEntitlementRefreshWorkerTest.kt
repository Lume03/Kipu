package com.kipu.app.feature.plans.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.plans.data.entitlement.PlanEntitlementRefresh
import com.kipu.app.feature.plans.data.entitlement.TestPlanRefreshResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEntitlementRefreshWorkerTest {
    private val refresh = mockk<PlanEntitlementRefresh>()
    private val session = mockk<SessionCoordinator>()
    private val auth = mockk<AuthRepository>()
    private var owner: LocalOwner? = LocalOwner(OWNER)

    private fun worker(): PlanEntitlementRefreshWorker {
        every { session.currentOwner } answers { owner }
        return PlanEntitlementRefreshWorker(
            mockk<Context>(relaxed = true),
            mockk<WorkerParameters> { every { inputData } returns workDataOf(PlanEntitlementRefreshWorker.KEY_OWNER to OWNER) },
            refresh, session, auth,
        )
    }

    @Test fun `refreshes the matching signed in owner`() = runTest {
        coEvery { refresh.refreshIfNeeded(OWNER) } returns TestPlanRefreshResult(true)
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { refresh.refreshIfNeeded(OWNER) }
        coVerify(exactly = 0) { auth.restoreSession() }
    }

    @Test fun `transient failures retry without mutating cached access`() = runTest {
        coEvery { refresh.refreshIfNeeded(OWNER) } returns TestPlanRefreshResult(false, "OFFLINE")
        assertTrue(worker().doWork() is ListenableWorker.Result.Retry)
        coEvery { refresh.refreshIfNeeded(OWNER) } throws IllegalStateException("offline")
        assertTrue(worker().doWork() is ListenableWorker.Result.Retry)
    }

    @Test fun `old account work and signed out owner never refresh`() = runTest {
        owner = LocalOwner("other")
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        owner = LocalOwner(OWNER, explicitlySignedOut = true)
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { refresh.refreshIfNeeded(any()) }
        coVerify(exactly = 0) { auth.restoreSession() }
    }

    @Test fun `cold process restores the persisted session before refresh`() = runTest {
        owner = null
        coEvery { auth.restoreSession() } answers { owner = LocalOwner(OWNER); Result.success(null) }
        coEvery { refresh.refreshIfNeeded(OWNER) } returns TestPlanRefreshResult(true)
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { auth.restoreSession() }
        coVerify(exactly = 1) { refresh.refreshIfNeeded(OWNER) }
    }

    @Test fun `cold process without saved session does not request an entitlement`() = runTest {
        owner = null
        coEvery { auth.restoreSession() } returns Result.success(null)
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { refresh.refreshIfNeeded(any()) }
    }

    private companion object { const val OWNER = "owner" }
}
