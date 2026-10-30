package com.kipu.app.feature.accounts.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.presentation.SessionEntryCurtain
import com.kipu.app.feature.movements.data.sync.SyncMovementsWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/** Await the existing owner-scoped sync (including all feed pages), never a second partial pull. */
class DashboardEntryPreloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionCoordinator: SessionCoordinator,
    private val curtain: SessionEntryCurtain,
) {
    // Keep the hydration requirement across retries after the curtain has dismissed to an error screen.
    private var hydrationRequired = false

    suspend fun awaitInitialData() {
        hydrationRequired = hydrationRequired || curtain.state.value?.authenticated == true
        if (!hydrationRequired) return
        val owner = requireNotNull(sessionCoordinator.currentOwner?.verifiedUserId)
        val request = OneTimeWorkRequestBuilder<SyncMovementsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(SyncMovementsWorker.KEY_USER_ID to owner))
            .build()
        val manager = WorkManager.getInstance(context)
        val name = "${SyncMovementsWorker.WORK_PREFIX}_$owner"
        // Retry joins a running sync; REPLACE only wakes one that is idle or backing off.
        val running = manager.getWorkInfosForUniqueWorkFlow(name).first().any { it.state == WorkInfo.State.RUNNING }
        // Awaited so a SUCCEEDED left by an earlier sync is never read as this entry's result.
        manager.enqueueUniqueWork(name, if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request).await()
        // Follow the unique name: other schedulers may REPLACE this request while we wait.
        awaitDashboardSync(manager.getWorkInfosForUniqueWorkFlow(name).map { it.currentSync() })
        check(sessionCoordinator.currentOwner?.verifiedUserId == owner) { "La sesión cambió." }
    }
}

internal fun List<WorkInfo>.currentSync(): WorkInfo? =
    firstOrNull { !it.state.isFinished } ?: firstOrNull { it.state == WorkInfo.State.SUCCEEDED } ?: lastOrNull()

/**
 * Stops waiting, never syncing: the work keeps running after a timeout or error, and the
 * dashboard observes Room, so it recovers on its own once the data lands.
 */
internal suspend fun awaitDashboardSync(work: Flow<WorkInfo?>) {
    val result = withTimeoutOrNull(15_000L) {
        // runAttemptCount already counts the attempt in progress; only a re-enqueued attempt is a retry.
        work.filterNotNull().first {
            it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED ||
                (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0)
        }
    }
    // An explicit retry screen is a resolved destination; a loading skeleton is not.
    check(result?.state == WorkInfo.State.SUCCEEDED) { "No se pudo preparar tu dinero. Intenta nuevamente." }
}
