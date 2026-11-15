package com.kipu.app.core.sync

import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import io.mockk.*
import org.junit.Test

class ForegroundSyncCoordinatorTest {
    @Test fun foregroundAndNavigationShareThrottleAndRefreshBothTransports() {
        val movements = mockk<MovementSyncScheduler>(relaxed = true)
        val categories = mockk<CategorySyncScheduler>(relaxed = true)
        val sync = ForegroundSyncCoordinator(movements, categories)
        sync.requestSync("A", 0)
        sync.requestSync("A", 1)
        sync.requestSync("A", 29_999)
        sync.requestSync("A", 30_000)
        verify(exactly = 2) { movements.scheduleSync("A") }
        verify(exactly = 2) { categories.scheduleSync("A") }
        sync.requestSync("B", 30_001)
        verify(exactly = 1) { movements.scheduleSync("B") }
        verify(exactly = 1) { categories.scheduleSync("B") }
    }
}
