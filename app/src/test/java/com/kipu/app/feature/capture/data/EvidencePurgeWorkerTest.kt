package com.kipu.app.feature.capture.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.kipu.app.feature.capture.data.work.EvidencePurgeWorker
import com.kipu.app.feature.capture.domain.CaptureCandidateStore
import io.mockk.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class EvidencePurgeWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = mockk<CaptureCandidateStore>()
    private val clock = Clock.fixed(Instant.ofEpochMilli(7000L), ZoneOffset.UTC)
    private fun worker() = EvidencePurgeWorker(context, mockk<WorkerParameters>(relaxed = true), store, clock)

    @Test fun purgesAtInjectedTimeWithoutSessionOrNetworkAndSucceedsWhenNothingExpired() = runTest {
        coEvery { store.purgeExpiredEvidence(7000L) } returnsMany listOf(2, 0)
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        coVerify(exactly = 2) { store.purgeExpiredEvidence(7000L) }
        confirmVerified(store)
    }

    @Test fun storageFailureRetriesAndSuccessfulRetryPurgesAtSameDeadline() = runTest {
        coEvery { store.purgeExpiredEvidence(7000L) } throws java.io.IOException("synthetic") andThen 1
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        coVerify(exactly = 2) { store.purgeExpiredEvidence(7000L) }
    }

    @Test fun cancellationPropagatesInsteadOfSchedulingFalseSuccess() = runTest {
        coEvery { store.purgeExpiredEvidence(any()) } throws CancellationException("stop")
        try { worker().doWork(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
}
