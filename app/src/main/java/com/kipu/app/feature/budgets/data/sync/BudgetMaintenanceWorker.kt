package com.kipu.app.feature.budgets.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.RunBudgetMaintenance
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

interface BudgetMaintenanceScheduler {
    fun scheduleNow(ownerId: String? = null)
    fun scheduleAfterConsumptionChange(ownerId: String)
    fun ensurePeriodic()
}

@Singleton
class WorkManagerBudgetMaintenanceScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : BudgetMaintenanceScheduler {
    override fun scheduleNow(ownerId: String?) {
        enqueue(ownerId, ExistingWorkPolicy.KEEP)
    }

    override fun scheduleAfterConsumptionChange(ownerId: String) {
        enqueue(ownerId, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(ownerId: String?, policy: ExistingWorkPolicy) {
        val builder = OneTimeWorkRequestBuilder<BudgetMaintenanceWorker>()
        if (!ownerId.isNullOrBlank()) {
            builder.setInputData(workDataOf(BudgetMaintenanceWorker.KEY_USER_ID to ownerId))
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            ownerId?.let { "${WORK_NOW}_$it" } ?: WORK_NOW,
            policy,
            builder.build(),
        )
    }

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<BudgetMaintenanceWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val WORK_NOW = "budget-maintenance-now"
        private const val WORK_PERIODIC = "budget-maintenance-periodic"
    }
}

@HiltWorker
class BudgetMaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionCoordinator: SessionCoordinator,
    private val maintenance: RunBudgetMaintenance,
    private val zoneId: ZoneId,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val available = sessionCoordinator.localAccess.value as? LocalAccess.Available ?: return Result.success()
        val requestedOwner = inputData.getString(KEY_USER_ID)
        if (requestedOwner != null && requestedOwner != available.userId) return Result.success()
        return try {
            val outcome = maintenance(available.userId, LocalDate.now(zoneId), zoneId)
            if (outcome.renewals.values.any { it is BudgetOutcome.Rejected }) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_USER_ID = "budget_maintenance_user_id"
    }
}
