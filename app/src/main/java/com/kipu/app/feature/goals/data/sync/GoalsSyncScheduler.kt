package com.kipu.app.feature.goals.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.BuildConfig
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesRequestDto
import com.kipu.app.feature.goals.data.local.GoalLocalDataSource
import com.kipu.app.feature.goals.data.local.GoalOutboxDao
import com.kipu.app.feature.goals.data.remote.GoalCommandRemoteResult
import com.kipu.app.feature.goals.data.remote.GoalsApi
import com.kipu.app.feature.goals.data.remote.GoalsApiResponse
import com.kipu.app.feature.goals.domain.model.GoalCommandType
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Singleton
class GoalsSyncScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun schedule(userId: String) {
        if (!BuildConfig.GOALS_REMOTE_COMMANDS_ENABLED || userId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<SyncGoalsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(SyncGoalsWorker.KEY_USER_ID to userId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncGoalsWorker.WORK_PREFIX}_$userId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

@HiltWorker
class SyncGoalsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val outboxDao: GoalOutboxDao,
    private val localDataSource: GoalLocalDataSource,
    private val goalsApi: GoalsApi,
    private val financialApi: FinancialInstrumentsApi,
    private val changeFeedApplier: GoalChangeFeedApplier,
    private val database: KipuDatabase,
    private val sessionCoordinator: SessionCoordinator,
    private val movementSyncScheduler: MovementSyncScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!BuildConfig.GOALS_REMOTE_COMMANDS_ENABLED) return Result.success()

        val activeOwner = sessionCoordinator.currentOwner?.verifiedUserId
        val userId = inputData.getString(KEY_USER_ID) ?: activeOwner
        if (userId.isNullOrBlank()) return Result.retry()
        if (activeOwner != userId) return Result.retry()

        localDataSource.repairUnsentLegacyRevisions(userId)
        var failed = false
        for (index in 0 until MAX_COMMANDS_PER_RUN) {
            val now = System.currentTimeMillis()
            val command = outboxDao.claimPending(userId, now, limit = 1).firstOrNull() ?: break
            if (!sendCommand(command)) {
                failed = true
            }
        }
        if (!pullChanges(userId)) failed = true
        if (outboxDao.getPendingForUser(userId).isNotEmpty()) failed = true
        return if (failed) Result.retry() else Result.success()
    }

    private suspend fun sendCommand(command: com.kipu.app.feature.goals.data.local.GoalOutboxEntity): Boolean {
        val type = runCatching { GoalCommandType.valueOf(command.commandType) }.getOrNull()
            ?: return localDataSource.finishRemoteCommand(command, "REJECTED", "UNKNOWN_GOAL_COMMAND", null)

        val payload = runCatching { Json.parseToJsonElement(command.payload).jsonObject }.getOrNull()
            ?: return localDataSource.finishRemoteCommand(command, "REJECTED", "INVALID_LOCAL_COMMAND", null)

        return when (val response = goalsApi.execute(type, payload)) {
            is GoalsApiResponse.Success -> applyCommandResult(command, response.data)
            is GoalsApiResponse.Error -> {
                when {
                    response.statusCode == 409 -> localDataSource.finishRemoteCommand(
                        command, "CONFLICT", "HTTP_409", null,
                    )
                    response.statusCode in setOf(400, 422) -> localDataSource.finishRemoteCommand(
                        command, "REJECTED", "HTTP_${response.statusCode}", null,
                    )
                    else -> localDataSource.finishRemoteCommand(
                        command, "RETRYABLE", "HTTP_${response.statusCode}", null,
                    )
                }
            }
            is GoalsApiResponse.NetworkFailure -> localDataSource.finishRemoteCommand(
                command, "RETRYABLE", response.exception.javaClass.simpleName.take(64), null,
            )
        }
    }

    private suspend fun applyCommandResult(
        command: com.kipu.app.feature.goals.data.local.GoalOutboxEntity,
        result: GoalCommandRemoteResult,
    ): Boolean {
        val state = when (result.status.uppercase()) {
            "APPLIED" -> "APPLIED"
            "DUPLICATE" -> "DUPLICATE"
            "CONFLICT" -> "CONFLICT"
            "REJECTED" -> "REJECTED"
            "RETRYABLE" -> "RETRYABLE"
            else -> "RETRYABLE"
        }
        val error = result.error?.take(96)
            ?: result.result?.take(96)
            ?: if (state == "RETRYABLE") "UNKNOWN_REMOTE_RESULT" else null
        return localDataSource.finishRemoteCommand(command, state, error, result.revision, result.goals.orEmpty())
    }

    private suspend fun pullChanges(userId: String): Boolean {
        repeat(MAX_CHANGE_PAGES) {
            val checkpoint = database.goalDao().getSyncCheckpoint(userId)?.sequence ?: 0L
            when (val response = financialApi.pullChanges(PullChangesRequestDto(afterSequence = checkpoint))) {
                is FinancialApiResponse.Success -> {
                    val page = response.data
                    if (page.changes.isEmpty()) return page.nextSequence == checkpoint
                    when (val applied = changeFeedApplier.applyPage(userId, page)) {
                        is GoalChangeApplyResult.Applied -> if (!page.hasMore) return true
                        is GoalChangeApplyResult.Conflict -> return false
                        is GoalChangeApplyResult.Rejected -> {
                            if (applied.code == "MISSING_GOAL_EVENT_TRANSACTION") {
                                movementSyncScheduler.scheduleSync(userId)
                            }
                            return false
                        }
                    }
                }
                is FinancialApiResponse.Error, is FinancialApiResponse.NetworkFailure -> return false
            }
        }
        return false
    }

    companion object {
        const val KEY_USER_ID = "goal_sync_user_id"
        const val WORK_PREFIX = "goal-sync"
        private const val MAX_COMMANDS_PER_RUN = 10
        private const val MAX_CHANGE_PAGES = 20
    }
}
