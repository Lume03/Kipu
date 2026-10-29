package com.kipu.app.feature.plans.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanEntitlementRefreshScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensurePeriodic(owner: String) {
        val request = PeriodicWorkRequestBuilder<PlanEntitlementRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(PlanEntitlementRefreshWorker.KEY_OWNER to owner))
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "$WORK_TAG-$owner", ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    companion object {
        const val WORK_TAG = "plan-entitlement-refresh"
    }
}
