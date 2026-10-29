package com.kipu.app.feature.accounts.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.presentation.SessionEntryCurtain
import com.kipu.app.feature.movements.data.sync.SyncMovementsWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        manager.enqueueUniqueWork("${SyncMovementsWorker.WORK_PREFIX}_$owner", ExistingWorkPolicy.REPLACE, request)
        awaitDashboardSync(manager.getWorkInfoByIdFlow(request.id))
        check(sessionCoordinator.currentOwner?.verifiedUserId == owner) { "La sesión cambió." }
    }
}

internal suspend fun awaitDashboardSync(work: Flow<WorkInfo?>) {
    val result = withTimeoutOrNull(15_000L) {
        work.filterNotNull().first { it.state.isFinished || it.runAttemptCount > 0 }
    }
    // An explicit retry screen is a resolved destination; a loading skeleton is not.
    check(result?.state == WorkInfo.State.SUCCEEDED) { "No se pudo preparar tu dinero. Intenta nuevamente." }
}
