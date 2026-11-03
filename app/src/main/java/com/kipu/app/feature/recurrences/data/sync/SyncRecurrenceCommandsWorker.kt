package com.kipu.app.feature.recurrences.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.accounts.data.remote.parseFeedInstant
import com.kipu.app.feature.recurrences.data.local.RecurrenceCommandOutboxEntity
import com.kipu.app.feature.recurrences.data.local.RecurrenceDao
import com.kipu.app.feature.recurrences.data.local.RecurrenceOutboxDao
import com.kipu.app.feature.recurrences.data.remote.RecurrencesApi
import com.kipu.app.feature.recurrences.data.remote.RecurrencesApiResponse
import com.kipu.app.feature.recurrences.domain.RecurrenceCommandHasher
import com.kipu.app.feature.recurrences.domain.RecurrenceCommandReceipt
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

@HiltWorker
class SyncRecurrenceCommandsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val outboxDao: RecurrenceOutboxDao,
    private val recurrenceDao: RecurrenceDao,
    private val repository: RecurrenceRepository,
    private val api: RecurrencesApi,
    private val sessionCoordinator: SessionCoordinator,
    private val clock: Clock,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = try {
        sync()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SecureLog.w("SyncRecurrenceCommandsWorker", "Recurrence sync will be retried")
        Result.retry()
    }

    private suspend fun sync(): Result {
        val userId = inputData.getString(KEY_USER_ID)?.takeIf(String::isNotBlank) ?: return Result.failure()
        if (!isCurrentOwner(userId)) return Result.retry()
        for (index in 0 until MAX_COMMANDS_PER_RUN) {
            val command = outboxDao.claimReady(userId, clock.millis(), limit = 1).firstOrNull() ?: break
            if (command.userId != userId || !isCurrentOwner(command.userId)) return Result.retry()
            send(command)
        }
        if (!isCurrentOwner(userId)) return Result.retry()
        if (!RecurrenceHydration(recurrenceDao, repository, api, sessionCoordinator).pullChanges(userId)) return Result.retry()
        return if (outboxDao.hasOutstanding(userId)) Result.retry() else Result.success()
    }

    private suspend fun send(command: RecurrenceCommandOutboxEntity) {
        val payload = try {
            val canonical = Json.parseToJsonElement(command.payload).jsonObject
            require(RecurrenceCommandHasher.sha256(canonical) == command.requestHash)
            require(canonical["command_type"]?.jsonPrimitive?.content == command.commandType)
            require(canonical["recurrence_id"]?.jsonPrimitive?.content == command.recurrenceId)
            JsonObject(canonical + mapOf("operation_id" to JsonPrimitive(command.operationId), "request_hash" to JsonPrimitive(command.requestHash)))
        } catch (_: IllegalArgumentException) {
            finish(command, "REJECTED", error = "INVALID_LOCAL_COMMAND")
            return
        }
        // Recheck after claiming/parsing; the API also verifies the authenticated token's owner.
        if (!isCurrentOwner(command.userId)) return
        when (val response = api.execute(command.userId, command.commandType, payload)) {
            is RecurrencesApiResponse.Success -> {
                val result = response.data
                val outcome = result.status.uppercase()
                if (outcome in setOf("APPLIED", "DUPLICATE") &&
                    (result.recurrenceId != command.recurrenceId || result.revision != (command.expectedRevision ?: 0L) + 1L)) {
                    finish(command, "RETRYABLE", error = "INVALID_REMOTE_RECEIPT")
                } else if (outcome in setOf("APPLIED", "DUPLICATE", "CONFLICT", "REJECTED", "RETRYABLE")) {
                    finish(command, outcome, result.revision, result.error?.code)
                } else finish(command, "RETRYABLE", error = "UNKNOWN_REMOTE_RESULT")
            }
            is RecurrencesApiResponse.Error -> finish(command, when {
                response.statusCode == 409 -> "CONFLICT"
                shouldRetry(response.statusCode) -> "RETRYABLE"
                else -> "REJECTED"
            }, error = "HTTP_${response.statusCode}")
            is RecurrencesApiResponse.NetworkFailure -> finish(command, "RETRYABLE", error = "REMOTE_UNAVAILABLE")
        }
    }

    private suspend fun finish(command: RecurrenceCommandOutboxEntity, outcome: String, revision: Long? = null, error: String? = null) {
        val delay = (30_000L * (1L shl (command.attemptCount - 1).coerceIn(0, 10))).coerceAtMost(21_600_000L)
        repository.reconcileReceipt(RecurrenceCommandReceipt(
            userId = command.userId, operationId = command.operationId, outcome = outcome,
            resultingRevision = revision, errorCode = error,
            retryAt = if (outcome == "RETRYABLE") clock.millis() + delay else null,
        ))
    }

    private fun isCurrentOwner(userId: String) = sessionCoordinator.currentOwner?.verifiedUserId == userId
    private fun shouldRetry(code: Int) = code in setOf(401, 408, 429) || code >= 500

    companion object {
        const val KEY_USER_ID = "recurrence_sync_user_id"
        private const val MAX_COMMANDS_PER_RUN = 10
        private const val MAX_CHANGE_PAGES = 20
    }
}
