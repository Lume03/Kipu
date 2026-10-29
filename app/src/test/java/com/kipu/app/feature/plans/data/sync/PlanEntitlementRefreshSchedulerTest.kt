package com.kipu.app.feature.plans.data.sync

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PlanEntitlementRefreshSchedulerTest {
    @Test fun `six hourly connected work keeps existing schedule and can cancel on logout`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = mockk<WorkManager>(relaxed = true)
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(context) } returns manager
            val request = slot<PeriodicWorkRequest>()
            val scheduler = PlanEntitlementRefreshScheduler(context)
            scheduler.ensurePeriodic("owner")
            verify {
                manager.enqueueUniquePeriodicWork("plan-entitlement-refresh-owner", ExistingPeriodicWorkPolicy.KEEP, capture(request))
            }
            assertEquals(TimeUnit.HOURS.toMillis(6), request.captured.workSpec.intervalDuration)
            assertEquals(NetworkType.CONNECTED, request.captured.workSpec.constraints.requiredNetworkType)
            assertEquals("owner", request.captured.workSpec.input.getString(PlanEntitlementRefreshWorker.KEY_OWNER))
            assertTrue(request.captured.tags.contains(PlanEntitlementRefreshScheduler.WORK_TAG))
            scheduler.cancel()
            verify { manager.cancelAllWorkByTag(PlanEntitlementRefreshScheduler.WORK_TAG) }
        } finally {
            unmockkObject(WorkManager.Companion)
        }
    }
}
