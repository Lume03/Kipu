package com.kipu.app.feature.capture.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kipu.app.feature.capture.domain.CaptureCandidateStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

interface EvidencePurgeScheduler {
    fun ensurePeriodic()
}

@Singleton
class WorkManagerEvidencePurgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : EvidencePurgeScheduler {
    override fun ensurePeriodic() {
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork(
            "capture-followups-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CaptureFollowupWorker>(15, TimeUnit.MINUTES).build(),
        )
        manager.enqueueUniqueWork(
            "capture-followups-startup", androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<CaptureFollowupWorker>().build(),
        )
        val request = PeriodicWorkRequestBuilder<EvidencePurgeWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val WORK_PERIODIC = "capture-evidence-purge-periodic"
    }
}

/** Clears expired capture evidence for every owner on the device; it needs no session or network. */
@HiltWorker
class EvidencePurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val candidates: CaptureCandidateStore,
    private val clock: Clock,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            candidates.purgeExpiredEvidence(clock.millis())
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
