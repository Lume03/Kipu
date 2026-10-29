package com.kipu.app.feature.plans.data.sync

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.entitlement.PlanEntitlementRefresh
import com.kipu.app.feature.plans.data.entitlement.TestPlanRefreshResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlanEntitlementRefreshCoordinatorTest {
    @Test fun `app startup login foreground and logout wire automatic refresh and work lifecycle`() = runTest {
        val access = MutableStateFlow<LocalAccess>(LocalAccess.NoOwner)
        val session = mockk<SessionCoordinator> {
            every { localAccess } returns access
            every { currentOwner } returns null
        }
        val refresh = mockk<PlanEntitlementRefresh>()
        coEvery { refresh.refreshIfNeeded(any()) } returns TestPlanRefreshResult(true)
        val scheduler = mockk<PlanEntitlementRefreshScheduler>(relaxed = true)
        val coordinator = PlanEntitlementRefreshCoordinator(session, dagger.Lazy { refresh }, scheduler)
        coordinator.start(backgroundScope)
        runCurrent()
        // A cold process must preserve work until encrypted-session restoration finishes.
        verify(exactly = 0) { scheduler.cancel() }

        access.value = LocalAccess.Available("owner", RemoteSession.Absent)
        runCurrent()
        verify(exactly = 1) { scheduler.ensurePeriodic("owner") }
        coVerify(exactly = 1) { refresh.refreshIfNeeded("owner") }
        coordinator.onForeground()
        coVerify(exactly = 1) { refresh.refreshIfNeeded(null) }

        access.value = LocalAccess.Protected("owner", "LOCKED")
        runCurrent()
        verify(exactly = 0) { scheduler.cancel() }
        every { session.currentOwner } returns LocalOwner("owner", explicitlySignedOut = true)
        access.value = LocalAccess.NoOwner
        runCurrent()
        verify(atLeast = 1) { scheduler.cancel() }
    }
}
