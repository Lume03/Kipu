package com.kipu.app.feature.budgets.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BudgetSyncRescueTest {
    private class RecordingScheduler(private val fail: Boolean = false) : BudgetSyncScheduler {
        val scheduled = mutableListOf<String>()
        override fun scheduleSync(userId: String) {
            scheduled += userId
            if (fail) throw IllegalStateException("WorkManager no disponible")
        }
    }

    @Test
    fun pendingOutboxIsRequeuedByMaintenance() = runBlocking {
        val scheduler = RecordingScheduler()
        rescuePendingBudgetSync("owner", { 2 }, scheduler)
        assertEquals(listOf("owner"), scheduler.scheduled)
    }

    @Test
    fun emptyOutboxDoesNotSchedule() = runBlocking {
        val scheduler = RecordingScheduler()
        rescuePendingBudgetSync("owner", { 0 }, scheduler)
        assertEquals(emptyList<String>(), scheduler.scheduled)
    }

    @Test
    fun schedulerFailureDuringRescueIsSwallowed() = runBlocking {
        val scheduler = RecordingScheduler(fail = true)
        rescuePendingBudgetSync("owner", { 1 }, scheduler)
        assertEquals(listOf("owner"), scheduler.scheduled)
    }
}
