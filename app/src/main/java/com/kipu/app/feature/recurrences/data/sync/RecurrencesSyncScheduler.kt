package com.kipu.app.feature.recurrences.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface RecurrencesSyncScheduler {
    fun scheduleSync(userId: String)
}

@Singleton
class WorkManagerRecurrencesSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecurrencesSyncScheduler {
    override fun scheduleSync(userId: String) {
        if (userId.isBlank()) return
        val manager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val input = workDataOf(SyncRecurrenceCommandsWorker.KEY_USER_ID to userId)
        manager.enqueueUniquePeriodicWork(
            "recurrence-sync-rescue:$userId", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncRecurrenceCommandsWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints).setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
        )
        manager.enqueueUniqueWork(
            "recurrence-sync:$userId", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncRecurrenceCommandsWorker>()
                .setConstraints(constraints).setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
        )
    }
}
