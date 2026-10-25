package com.kipu.app.feature.budgets.data.sync

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
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.parseFeedInstant
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kipu.app.feature.budgets.data.local.BudgetAlertLedgerDao
import com.kipu.app.feature.budgets.data.local.BudgetDao
import com.kipu.app.feature.budgets.data.local.BudgetSyncOutboxEntity
import com.kipu.app.feature.budgets.data.local.BudgetTombstoneEntity
import com.kipu.app.feature.budgets.data.remote.BudgetApi
import com.kipu.app.feature.budgets.data.remote.BudgetApiResponse
import com.kipu.app.feature.budgets.data.remote.BudgetPeriodRemoteDto
import com.kipu.app.feature.budgets.data.remote.BudgetRemoteDto
import com.kipu.app.feature.budgets.data.remote.BudgetSyncPayload
import com.kipu.app.feature.budgets.data.remote.toLocalEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SyncBudgetCommandsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val budgetDao: BudgetDao,
    private val alertLedgerDao: BudgetAlertLedgerDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val api: BudgetApi,
    private val sessionCoordinator: SessionCoordinator,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : CoroutineWorker(appContext, workerParams) {

    private enum class Outcome { APPLIED, CONFLICT, RETRY }

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val localAccess = sessionCoordinator.localAccess.value as? LocalAccess.Available
        if (localAccess?.userId != userId) {
            SecureLog.w("SyncBudgetCommandsWorker", "Skipping budget sync for an inactive owner")
            return Result.success()
        }

        when (val tombstones = api.fetchTombstones(userId)) {
            is BudgetApiResponse.Success -> applyRemoteTombstones(userId, tombstones.data)
            is BudgetApiResponse.Error -> return if (shouldRetry(tombstones.statusCode)) Result.retry() else Result.failure()
            is BudgetApiResponse.NetworkFailure -> return Result.retry()
        }

        for (command in budgetDao.pendingOutbox(userId)) {
            val tombstone = budgetDao.getTombstone(userId, command.aggregateId)
            if (tombstone != null && command.commandType != DELETE_BUDGET) {
                budgetDao.updateOutbox(command.copy(state = COMPLETED, errorCode = "TOMBSTONED", updatedAt = clock.millis()))
                continue
            }
            when (process(command)) {
                Outcome.APPLIED -> budgetDao.updateOutbox(
                    command.copy(state = COMPLETED, errorCode = null, updatedAt = clock.millis()),
                )
                Outcome.CONFLICT -> budgetDao.updateOutbox(
                    command.copy(state = FAILED, errorCode = "REVISION_CONFLICT", updatedAt = clock.millis()),
                )
                Outcome.RETRY -> {
                    budgetDao.updateOutbox(
                        command.copy(
                            attemptCount = command.attemptCount + 1,
                            state = PENDING,
                            errorCode = "REMOTE_UNAVAILABLE",
                            updatedAt = clock.millis(),
                        ),
                    )
                    return Result.retry()
                }
            }
        }

        return hydrate(userId)
    }

    private suspend fun process(command: BudgetSyncOutboxEntity): Outcome {
        val payload = runCatching { json.decodeFromString<BudgetSyncPayload>(command.payloadJson) }.getOrNull()
            ?: return Outcome.CONFLICT
        return when (command.commandType) {
            UPSERT_BUDGET -> pushBudget(command, payload)
            UPSERT_PERIOD -> pushPeriod(command, payload)
            DELETE_BUDGET -> when (val result = api.deleteBudget(command.userId, payload.budgetId ?: command.aggregateId)) {
                is BudgetApiResponse.Success -> if (result.data.status == null || result.data.status == "APPLIED") Outcome.APPLIED else Outcome.CONFLICT
                is BudgetApiResponse.Error -> if (shouldRetry(result.statusCode)) Outcome.RETRY else Outcome.CONFLICT
                is BudgetApiResponse.NetworkFailure -> Outcome.RETRY
            }
            else -> Outcome.CONFLICT
        }
    }

    private suspend fun pushBudget(command: BudgetSyncOutboxEntity, payload: BudgetSyncPayload): Outcome {
        val budget = payload.budget ?: return Outcome.CONFLICT
        return when (val result = api.upsertBudget(command.userId, budget, command.expectedRevision)) {
            is BudgetApiResponse.Success -> {
                if (result.data.any { it.sameBusinessStateAs(budget) }) Outcome.APPLIED
                else confirmBudget(command.userId, budget)
            }
            is BudgetApiResponse.Error -> when {
                result.statusCode == 409 -> confirmBudget(command.userId, budget)
                shouldRetry(result.statusCode) -> Outcome.RETRY
                else -> Outcome.CONFLICT
            }
            is BudgetApiResponse.NetworkFailure -> Outcome.RETRY
        }
    }

    private suspend fun confirmBudget(userId: String, expected: BudgetRemoteDto): Outcome =
        when (val current = api.fetchBudget(userId, expected.id)) {
            is BudgetApiResponse.Success -> if (current.data.any { it.sameBusinessStateAs(expected) }) Outcome.APPLIED else Outcome.CONFLICT
            is BudgetApiResponse.Error -> if (shouldRetry(current.statusCode)) Outcome.RETRY else Outcome.CONFLICT
            is BudgetApiResponse.NetworkFailure -> Outcome.RETRY
        }

    private suspend fun pushPeriod(command: BudgetSyncOutboxEntity, payload: BudgetSyncPayload): Outcome {
        val period = payload.period ?: return Outcome.CONFLICT
        return when (val result = api.upsertPeriod(command.userId, period)) {
            is BudgetApiResponse.Success -> Outcome.APPLIED
            is BudgetApiResponse.Error -> when {
                result.statusCode == 409 -> confirmPeriod(command.userId, period)
                shouldRetry(result.statusCode) -> Outcome.RETRY
                else -> Outcome.CONFLICT
            }
            is BudgetApiResponse.NetworkFailure -> Outcome.RETRY
        }
    }

    private suspend fun confirmPeriod(userId: String, expected: BudgetPeriodRemoteDto): Outcome =
        when (val current = api.fetchPeriods(userId)) {
            is BudgetApiResponse.Success -> if (current.data.any { it.sameBusinessStateAs(expected) }) Outcome.APPLIED else Outcome.CONFLICT
            is BudgetApiResponse.Error -> if (shouldRetry(current.statusCode)) Outcome.RETRY else Outcome.CONFLICT
            is BudgetApiResponse.NetworkFailure -> Outcome.RETRY
        }

    private suspend fun applyRemoteTombstones(userId: String, rows: List<com.kipu.app.feature.budgets.data.remote.BudgetTombstoneRemoteDto>) {
        transactionRunner {
            rows.asSequence().filter { it.userId == userId }.forEach { remote ->
                val current = budgetDao.getTombstone(userId, remote.budgetId)
                val tombstone = BudgetTombstoneEntity(
                    userId = userId,
                    budgetId = remote.budgetId,
                    lastRevision = maxOf(current?.lastRevision ?: 0L, remote.lastRevision),
                    deletedAt = current?.deletedAt ?: parseFeedInstant(remote.deletedAt).toEpochMilli(),
                )
                budgetDao.upsertTombstone(tombstone)
                budgetDao.deletePeriods(userId, remote.budgetId)
                alertLedgerDao.deleteForBudget(userId, remote.budgetId)
                budgetDao.deleteBudget(userId, remote.budgetId)
                budgetDao.removeSupersededBudgetCommands(userId, remote.budgetId)
            }
        }
    }

    private suspend fun hydrate(userId: String): Result {
        val budgets = when (val result = api.fetchBudgets(userId)) {
            is BudgetApiResponse.Success -> result.data
            is BudgetApiResponse.Error -> return if (shouldRetry(result.statusCode)) Result.retry() else Result.failure()
            is BudgetApiResponse.NetworkFailure -> return Result.retry()
        }
        val periods = when (val result = api.fetchPeriods(userId)) {
            is BudgetApiResponse.Success -> result.data
            is BudgetApiResponse.Error -> return if (shouldRetry(result.statusCode)) Result.retry() else Result.failure()
            is BudgetApiResponse.NetworkFailure -> return Result.retry()
        }

        val blockedBudgetIds = budgetDao.allOutbox(userId)
            .filter { it.state == PENDING || it.state == FAILED }
            .mapTo(mutableSetOf()) { it.aggregateId }
        transactionRunner {
            for (remote in budgets) {
                if (remote.userId != userId || remote.id in blockedBudgetIds || budgetDao.getTombstone(userId, remote.id) != null) continue
                if (remote.periodType == "CUSTOM" && (remote.rangeStartOn == null || remote.rangeEndOn == null)) {
                    SecureLog.w("SyncBudgetCommandsWorker", "Skipping a legacy custom budget without date bounds")
                    continue
                }
                val local = budgetDao.getBudget(userId, remote.id)
                if (local == null || remote.revision >= local.revision) {
                    budgetDao.upsertBudget(remote.toLocalEntity(zoneId))
                }
            }

            for (remote in periods) {
                if (remote.userId != userId || remote.budgetId in blockedBudgetIds || budgetDao.getTombstone(userId, remote.budgetId) != null) continue
                if (budgetDao.getBudget(userId, remote.budgetId) == null) continue
                // The remote snapshot of the period scope is authoritative.
                budgetDao.upsertPeriod(remote.toLocalEntity())
            }
        }
        return Result.success()
    }

    private fun shouldRetry(statusCode: Int): Boolean = statusCode == 401 || statusCode == 408 || statusCode == 429 || statusCode >= 500

    companion object {
        const val KEY_USER_ID = "budget_sync_user_id"
        const val WORK_PREFIX = "budget-sync"
        private const val UPSERT_BUDGET = "UPSERT_BUDGET"
        private const val UPSERT_PERIOD = "UPSERT_PERIOD"
        private const val DELETE_BUDGET = "DELETE_BUDGET"
        private const val PENDING = "PENDING"
        private const val COMPLETED = "COMPLETED"
        private const val FAILED = "FAILED"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Singleton
class WorkManagerBudgetSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : BudgetSyncScheduler {
    override fun scheduleSync(userId: String) {
        val request = OneTimeWorkRequestBuilder<SyncBudgetCommandsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(SyncBudgetCommandsWorker.KEY_USER_ID to userId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SyncBudgetCommandsWorker.WORK_PREFIX}_$userId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

private fun BudgetRemoteDto.sameBusinessStateAs(other: BudgetRemoteDto): Boolean =
    id == other.id && userId == other.userId && name == other.name && categoryId == other.categoryId &&
        limitMinor == other.limitMinor && currencyCode.trim() == other.currencyCode.trim() &&
        periodType == other.periodType && rangeStartOn == other.rangeStartOn && rangeEndOn == other.rangeEndOn &&
        alertThresholdsBps == other.alertThresholdsBps && status == other.status && revision == other.revision &&
        isActive == other.isActive && pendingScopeType == other.pendingScopeType &&
        pendingScopeCategoryId == other.pendingScopeCategoryId && pendingScheduleType == other.pendingScheduleType &&
        pendingRangeStartOn == other.pendingRangeStartOn && pendingRangeEndOn == other.pendingRangeEndOn &&
        pendingEffectiveFrom == other.pendingEffectiveFrom

private fun BudgetPeriodRemoteDto.sameBusinessStateAs(other: BudgetPeriodRemoteDto): Boolean =
    id == other.id && userId == other.userId && budgetId == other.budgetId && startsOn == other.startsOn &&
        endsOn == other.endsOn && limitMinor == other.limitMinor && scopeCategoryId == other.scopeCategoryId
