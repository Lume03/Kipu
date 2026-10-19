package com.kipu.app.feature.goals.data.local

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.goals.data.remote.toRpcPayload
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalCommandEnvelope
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.model.GoalCommandType
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.TransactionEntity
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Singleton
class GoalLocalDataSource @Inject constructor(
    private val database: KipuDatabase,
    private val goalDao: GoalDao,
    private val outboxDao: GoalOutboxDao,
    private val movementDao: MovementDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun observeGoals(userId: String): Flow<List<GoalEntity>> = goalDao.observeGoals(userId)

    fun observeGoal(userId: String, goalId: String): Flow<GoalEntity?> = goalDao.observeGoal(userId, goalId)

    fun observeEvents(userId: String, goalId: String): Flow<List<GoalEventWithTransactionStatusRow>> =
        goalDao.observeEvents(userId, goalId)

    fun observeBackingAccounts(userId: String): Flow<List<GoalBackingAccountRow>> =
        goalDao.observeBackingAccounts(userId)

    suspend fun getGoal(userId: String, goalId: String): GoalEntity? = goalDao.getGoalIncludingArchived(userId, goalId)

    suspend fun getEvents(userId: String, goalId: String): List<GoalEventWithTransactionStatusRow> =
        goalDao.getEvents(userId, goalId)

    suspend fun getBackingAccountPosition(userId: String, accountId: String): GoalBackingAccountRow? =
        goalDao.getBackingAccountPosition(userId, accountId)

    suspend fun countOperationalGoals(userId: String): Int = goalDao.countOperationalGoals(userId)

    suspend fun pendingCommandCount(userId: String, goalId: String): Int =
        outboxDao.countPending(userId, goalId)

    suspend fun countEvents(userId: String, goalId: String): Int = goalDao.countEvents(userId, goalId)

    suspend fun countLinkedTransfers(userId: String, goalId: String): Int =
        goalDao.countLinkedTransfers(userId, goalId)

    suspend fun finishRemoteCommand(
        command: GoalOutboxEntity,
        state: String,
        errorCode: String?,
        remoteRevision: Long?,
        remoteGoals: List<com.kipu.app.feature.goals.data.remote.GoalCommandGoalRemoteResult> = emptyList(),
    ): Boolean = database.withTransaction {
        val affectedGoalIds = buildSet {
            add(command.goalId)
            runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(command.payload)
                    .jsonObject["payload"]!!.jsonObject["replacement_goal_id"]
                    ?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.let(::add)
        }
        if (remoteGoals.isNotEmpty() && remoteGoals.map { it.goalId }.toSet() != affectedGoalIds) return@withTransaction false
        if (affectedGoalIds.size > 1 && state in setOf("APPLIED", "DUPLICATE") && remoteGoals.map { it.goalId }.toSet() != affectedGoalIds) {
            return@withTransaction false
        }
        val now = clock.millis()
        val nextAttemptAt = if (state == "RETRYABLE") {
            now + retryDelay(command.attemptCount)
        } else {
            null
        }
        if (
            outboxDao.updateResult(
                userId = command.userId,
                operationId = command.operationId,
                state = state,
                nextAttemptAt = nextAttemptAt,
                errorCode = errorCode,
                resultPayload = null,
                now = now,
            ) != 1
        ) {
            return@withTransaction false
        }
        when (state) {
            "APPLIED", "DUPLICATE" -> {
                if (command.commandType == GoalCommandType.DELETE_EMPTY_GOAL.name) {
                    outboxDao.deleteApplied(command.userId, command.operationId)
                    if (goalDao.deleteEmptyGoal(command.userId, command.goalId) != 1) {
                        throw IllegalStateException("Goal delete was no longer safe after remote confirmation")
                    }
                } else {
                    if (remoteGoals.isNotEmpty()) {
                        remoteGoals.forEach { remote ->
                            val validStatus = runCatching { com.kipu.app.feature.goals.domain.model.GoalStatus.valueOf(remote.status) }.isSuccess
                            if (!validStatus || goalDao.updateRemoteGoalResult(command.userId, remote.goalId, remote.status, remote.revision, "SYNCED") != 1) {
                                throw IllegalStateException("Composite goal receipt does not match local projections")
                            }
                        }
                    } else if (remoteRevision != null) {
                        goalDao.updateRemoteRevision(command.userId, command.goalId, remoteRevision, "SYNCED")
                    } else {
                        goalDao.updateSyncState(command.userId, command.goalId, "SYNCED")
                    }
                    outboxDao.deleteApplied(command.userId, command.operationId)
                }
                movementDao.updateCommandReceiptStatus(command.userId, command.operationId, "SYNCED", now)
            }
            "CONFLICT" -> {
                affectedGoalIds.forEach { goalDao.updateSyncState(command.userId, it, "CONFLICT") }
                movementDao.updateCommandReceiptStatus(command.userId, command.operationId, "CONFLICT", now)
            }
            "REJECTED" -> {
                affectedGoalIds.forEach { goalDao.updateSyncState(command.userId, it, "FAILED") }
                movementDao.updateCommandReceiptStatus(command.userId, command.operationId, "REJECTED", now)
            }
            else -> {
                affectedGoalIds.forEach { goalDao.updateSyncState(command.userId, it, "PENDING") }
                movementDao.updateCommandReceiptStatus(command.userId, command.operationId, "PENDING", now)
            }
        }
        true
    }

    private fun retryDelay(attempt: Int): Long =
        (BASE_RETRY_DELAY_MS * (1L shl (attempt.coerceIn(1, 10) - 1))).coerceAtMost(MAX_RETRY_DELAY_MS)

    private companion object {
        const val BASE_RETRY_DELAY_MS = 30_000L
        const val MAX_RETRY_DELAY_MS = 3_600_000L
    }

    suspend fun stageCommand(
        userId: String,
        envelope: GoalCommandEnvelope,
        goals: List<Goal>,
        expectedRevisions: Map<String, Long?>,
        events: List<GoalEvent> = emptyList(),
    ): GoalCommandResult = database.withTransaction {
        if (userId.isBlank() || goals.isEmpty() || goals.any { it.userId != userId }) {
            return@withTransaction GoalCommandResult.Rejected("INVALID_OWNER_OR_GOAL")
        }
        val primaryGoal = goals.firstOrNull { it.id == envelope.goalId }
            ?: return@withTransaction GoalCommandResult.Rejected("COMMAND_GOAL_MISSING")
        if (expectedRevisions.keys != goals.map { it.id }.toSet()) {
            return@withTransaction GoalCommandResult.Rejected("INVALID_EXPECTED_REVISIONS")
        }

        val receipt = movementDao.getReceipt(userId, envelope.identity.operationId)
        val existingOutbox = outboxDao.get(userId, envelope.identity.operationId)
        if (receipt != null || existingOutbox != null) {
            val sameIdentity =
                (receipt == null ||
                    receipt.requestHash == envelope.identity.requestHash &&
                    receipt.commandType == envelope.type.name) &&
                    (existingOutbox == null ||
                        existingOutbox.requestHash == envelope.identity.requestHash &&
                        existingOutbox.commandType == envelope.type.name)
            if (!sameIdentity) return@withTransaction GoalCommandResult.Conflict("OPERATION_ID_REUSED")
            val localRevision = goalDao.getGoalIncludingArchived(userId, primaryGoal.id)?.revision
                ?: primaryGoal.revision
            return@withTransaction GoalCommandResult.Duplicate(primaryGoal.id, localRevision)
        }

        for ((goalId, expectedRevision) in expectedRevisions) {
            val current = goalDao.getGoalIncludingArchived(userId, goalId)
            if (expectedRevision == null) {
                if (current != null) return@withTransaction GoalCommandResult.Conflict("GOAL_ID_REUSED")
            } else if (current == null || current.revision != expectedRevision) {
                return@withTransaction GoalCommandResult.Conflict("REVISION_MISMATCH", current?.revision)
            }
        }
        if (events.any { it.userId != userId || it.operationId != envelope.identity.operationId }) {
            return@withTransaction GoalCommandResult.Rejected("INVALID_GOAL_EVENT")
        }
        if (events.any { event -> goals.none { it.id == event.goalId } && goalDao.getGoal(userId, event.goalId) == null }) {
            return@withTransaction GoalCommandResult.Rejected("GOAL_EVENT_PARENT_MISSING")
        }

        val now = clock.millis()
        val operationId = envelope.identity.operationId
        val transferAccounts = explicitTransferAccounts(envelope)
        if (transferAccounts != null) {
            if (movementDao.getTransactionById(userId, operationId) != null) {
                return@withTransaction GoalCommandResult.Conflict("OPERATION_ID_REUSED")
            }
            val (sourceAccountId, destinationAccountId, currencyCode, amountMinor) = transferAccounts
            val transaction = TransactionEntity(
                id = operationId,
                userId = userId,
                type = "TRANSFER",
                amountMinor = amountMinor,
                currencyCode = currencyCode,
                sourceAccountId = sourceAccountId,
                destinationAccountId = destinationAccountId,
                occurredAt = now,
                note = "Transferencia explícita para reserva de meta",
                status = "ACTIVE",
                syncStatus = "PENDING",
                createdAt = now,
                updatedAt = now,
                operationKind = "GOAL_FUNDING",
            )
            movementDao.insertTransaction(transaction)
            movementDao.insertLedgerEntries(
                listOf(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(), userId = userId, transactionId = operationId,
                        accountId = sourceAccountId, role = "SOURCE", signedAmountMinor = -amountMinor,
                        currencyCode = currencyCode, createdAt = now,
                    ),
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(), userId = userId, transactionId = operationId,
                        accountId = destinationAccountId, role = "DESTINATION", signedAmountMinor = amountMinor,
                        currencyCode = currencyCode, createdAt = now,
                    ),
                ),
            )
            listOf(sourceAccountId, destinationAccountId).distinct().forEach { accountId ->
                val balance = movementDao.calculateLedgerSumForAccount(userId, accountId) ?: 0L
                movementDao.upsertBalanceProjection(
                    com.kipu.app.feature.movements.data.local.BalanceProjectionEntity(
                        userId = userId,
                        accountId = accountId,
                        balanceMinor = balance,
                        currencyCode = currencyCode,
                        lastTransactionAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
        goals.forEach { goal ->
            val state = if (envelope.type == GoalCommandType.DELETE_EMPTY_GOAL) "DELETE_PENDING" else "PENDING"
            val entity = goal.toEntity(syncState = state)
            if (expectedRevisions.getValue(goal.id) == null) {
                goalDao.insertGoal(entity)
            } else {
                goalDao.upsertGoal(entity)
            }
        }
        events.forEach { event ->
            val linkedEvent = if (transferAccounts != null && event.type.name == "ALLOCATION") {
                event.copy(transactionId = operationId)
            } else {
                event
            }
            goalDao.insertEvent(linkedEvent.toEntity())
        }

        val payload = envelope.toRpcPayload().toString()
        outboxDao.insert(
            GoalOutboxEntity(
                userId = userId,
                operationId = envelope.identity.operationId,
                goalId = envelope.goalId,
                commandType = envelope.type.name,
                requestHash = envelope.identity.requestHash,
                contractVersion = envelope.identity.contractVersion,
                expectedRevision = envelope.expectedRevision,
                payload = payload,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val eventId = events.firstOrNull()?.id
        val response = buildJsonObject {
            put("goal_id", primaryGoal.id)
            put("revision", primaryGoal.revision)
            eventId?.let { put("event_id", it) }
            put("status", "PENDING")
        }.toString()
        movementDao.insertOrUpdateReceipt(
            LocalCommandReceiptEntity(
                userId = userId,
                idempotencyKey = envelope.identity.operationId,
                requestHash = envelope.identity.requestHash,
                transactionId = events.firstOrNull()?.let { event ->
                    if (transferAccounts != null && event.type.name == "ALLOCATION") operationId else event.transactionId
                },
                status = "PENDING",
                responsePayload = response,
                contractVersion = envelope.identity.contractVersion,
                commandType = envelope.type.name,
                expectedRevision = envelope.expectedRevision,
                resultingRevision = primaryGoal.revision,
                createdAt = now,
                updatedAt = now,
            ),
        )
        GoalCommandResult.Applied(primaryGoal.id, primaryGoal.revision, eventId)
    }

    private fun explicitTransferAccounts(
        envelope: GoalCommandEnvelope,
    ): TransferAccounts? {
        if (envelope.type != GoalCommandType.RESERVE_GOAL) return null
        val payload = envelope.payload
        val transfer = payload["transfer"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }
            ?: return null
        val values = (transfer as? kotlinx.serialization.json.JsonObject)?.toMap() ?: return null
        val source = values["source_account_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val destination = values["destination_account_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val currency = values["currency_code"]?.jsonPrimitive?.contentOrNull ?: return null
        val amount = payload["amount_minor"]?.jsonPrimitive?.longOrNull ?: return null
        if (source.isBlank() || destination.isBlank() || source == destination || amount <= 0L) return null
        return TransferAccounts(source, destination, currency, amount)
    }

    private data class TransferAccounts(
        val sourceAccountId: String,
        val destinationAccountId: String,
        val currencyCode: String,
        val amountMinor: Long,
    )
}
