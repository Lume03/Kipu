package com.kipu.app.core.session

import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.data.sync.requireSyncOwner
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
class SessionOwnerSyncTest {
    @Test fun `owner switch cancels previous sync and late response is rejected`() = runTest {
        val scheduler = mockk<MovementSyncScheduler>(relaxed = true)
        val instruments = mockk<com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler>(relaxed = true)
        val coordinator = SessionCoordinatorImpl(scheduler, instruments)
        coordinator.setActiveOwner("A")
        requireSyncOwner(coordinator, "A")
        coordinator.setActiveOwner("B")
        verify(exactly = 1) { scheduler.cancelSync("A"); instruments.cancelSync("A") }
        assertTrue(runCatching { requireSyncOwner(coordinator, "A") }.exceptionOrNull() is CancellationException)
        requireSyncOwner(coordinator, "B")
    }
    @Test fun `logout revokes owner before cancelling work`() = runTest {
        val scheduler = mockk<MovementSyncScheduler>(relaxed = true)
        val instruments = mockk<com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler>(relaxed = true)
        val coordinator = SessionCoordinatorImpl(scheduler, instruments)
        coordinator.setActiveOwner("A")
        every { scheduler.cancelSync("A") } answers { assertNull(coordinator.currentOwner) }
        coordinator.clearActiveOwner(true)
        verify { scheduler.cancelSync("A"); instruments.cancelSync("A") }
        assertTrue(runCatching { requireSyncOwner(coordinator, "A") }.exceptionOrNull() is CancellationException)
    }
    @Test fun `same owner activation does not cancel its sync`() = runTest {
        val scheduler = mockk<MovementSyncScheduler>(relaxed = true)
        val instruments = mockk<com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler>(relaxed = true)
        val coordinator = SessionCoordinatorImpl(scheduler, instruments)
        coordinator.setActiveOwner("A")
        coordinator.setActiveOwner("A")
        verify(exactly = 0) { scheduler.cancelSync(any()); instruments.cancelSync(any()) }
    }
}
