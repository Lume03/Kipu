package com.kipu.app.feature.capture.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.feature.capture.domain.CaptureAccessGate
import com.kipu.app.feature.capture.domain.CaptureCandidateStore
import com.kipu.app.feature.capture.domain.ExtractCaptureCandidate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Replays durable work for the unlocked owner, without rereading notification evidence. */
@HiltWorker
class CaptureFollowupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gate: CaptureAccessGate,
    private val candidates: CaptureCandidateStore,
    private val pipeline: ExtractCaptureCandidate,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = gate.currentOwnerId() ?: return Result.retry()
        return try {
            var failed = false
            for (candidate in candidates.pendingFollowups(owner)) {
                if (gate.currentOwnerId() != owner) return Result.retry()
                try { pipeline.resume(candidate) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            }
            if (failed || candidates.pendingFollowups(owner).isNotEmpty()) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
