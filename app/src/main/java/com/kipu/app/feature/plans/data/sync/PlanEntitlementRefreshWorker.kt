package com.kipu.app.feature.plans.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.plans.data.entitlement.PlanEntitlementRefresh
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class PlanEntitlementRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refresh: PlanEntitlementRefresh,
    private val session: SessionCoordinator,
    private val auth: AuthRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString(KEY_OWNER) ?: return Result.success()
        return try {
            // WorkManager can start a new process without an Activity restoring the session.
            if (session.currentOwner == null) {
                if (auth.restoreSession().isFailure) return Result.retry()
            }
            val active = session.currentOwner
            if (active == null || active.explicitlySignedOut || active.verifiedUserId != owner) {
                return Result.success()
            }
            if (refresh.refreshIfNeeded(owner).success) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_OWNER = "plan_refresh_owner"
    }
}
