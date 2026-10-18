package com.kipu.app.feature.goals.data.sync

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.goals.data.local.GoalDao
import com.kipu.app.feature.goals.data.local.GoalOutboxDao
import com.kipu.app.feature.goals.data.local.GoalEntity
import com.kipu.app.feature.goals.data.local.GoalEventEntity
import com.kipu.app.feature.goals.data.local.GoalSyncCheckpointEntity
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.movements.data.local.MovementDao
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed interface GoalChangeApplyResult {
    data class Applied(val nextSequence: Long, val appliedChanges: Int) : GoalChangeApplyResult
    data class Conflict(val goalId: String, val localRevision: Long, val remoteRevision: Long) : GoalChangeApplyResult
    data class Rejected(val code: String) : GoalChangeApplyResult
}

@Singleton
class GoalChangeFeedApplier @Inject constructor(
    private val database: KipuDatabase,
    private val goalDao: GoalDao,
    private val outboxDao: GoalOutboxDao,
    private val accountDao: AccountDao,
    private val movementDao: MovementDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun applyPage(userId: String, page: PullChangesResponseDto): GoalChangeApplyResult {
        if (userId.isBlank() || page.changes.size > MAX_PAGE_SIZE) {
            return GoalChangeApplyResult.Rejected("INVALID_SYNC_PAGE")
        }
        val checkpoint = goalDao.getSyncCheckpoint(userId)?.sequence ?: 0L
        // The feed sequence is global across owners. A user's page can skip
        // sequence values committed by other users, but must stay monotonic.
        var previousSequence = checkpoint
        for (change in page.changes) {
            if (change.sequence <= previousSequence) return GoalChangeApplyResult.Rejected("SYNC_SEQUENCE_GAP")
            previousSequence = change.sequence
        }
        val nextSequence = page.changes.lastOrNull()?.sequence ?: checkpoint
        if (page.nextSequence != nextSequence) return GoalChangeApplyResult.Rejected("SYNC_SEQUENCE_GAP")

        return try {
            database.withTransaction {
                val currentCheckpoint = goalDao.getSyncCheckpoint(userId)?.sequence ?: 0L
                if (currentCheckpoint != checkpoint) throw InvalidGoalChangePage("SYNC_CHECKPOINT_CHANGED")
                var applied = 0
                page.changes.forEach { change ->
                    when (change.entityType.uppercase()) {
                        "GOAL" -> if (change.operation.equals("DELETE", ignoreCase = true)) {
                            applyGoalDelete(userId, change)
                        } else {
                            applyGoalUpsert(userId, change)
                        }
                        "GOAL_EVENT" -> applyGoalEventUpsert(userId, change)
                    }
                    applied += 1
                }
                if (page.changes.isNotEmpty()) {
                    goalDao.upsertSyncCheckpoint(GoalSyncCheckpointEntity(userId, page.nextSequence, clock.millis()))
                }
                GoalChangeApplyResult.Applied(page.nextSequence, applied)
            }
        } catch (stale: StaleGoalRevision) {
            database.withTransaction {
                val affectedIds = buildSet {
                    add(stale.goalId)
                    outboxDao.getPendingForUser(userId).forEach { command ->
                        val replacementId = runCatching {
                            Json.parseToJsonElement(command.payload).jsonObject["payload"]
                                ?.jsonObject?.get("replacement_goal_id")?.jsonPrimitive?.contentOrNull
                        }.getOrNull()
                        if (command.goalId == stale.goalId || replacementId == stale.goalId) {
                            add(command.goalId)
                            replacementId?.let(::add)
                        }
                    }
                }
                affectedIds.forEach { goalDao.updateSyncState(userId, it, "CONFLICT") }
            }
            GoalChangeApplyResult.Conflict(stale.goalId, stale.localRevision, stale.remoteRevision)
        } catch (invalid: InvalidGoalChangePage) {
            GoalChangeApplyResult.Rejected(invalid.code)
        } catch (_: Exception) {
            GoalChangeApplyResult.Rejected("INVALID_REMOTE_GOAL_CHANGE")
        }
    }

    private suspend fun applyGoalUpsert(userId: String, change: SyncChangeItemDto) {
        val payload = change.payload ?: throw InvalidGoalChangePage("GOAL_PAYLOAD_REQUIRED")
        val remote = json.decodeFromJsonElement<RemoteGoalDto>(payload)
        if (remote.userId != userId || remote.id != change.entityId || remote.revision != change.revision) {
            throw InvalidGoalChangePage("GOAL_OWNER_OR_REVISION_MISMATCH")
        }
        if (remote.targetMinor <= 0L || remote.currencyCode !in SUPPORTED_CURRENCIES || remote.revision <= 0L) {
            throw InvalidGoalChangePage("INVALID_GOAL_VALUE")
        }
        val status = runCatching { GoalStatus.valueOf(remote.status) }.getOrNull()
            ?: throw InvalidGoalChangePage("INVALID_GOAL_STATUS")
        val targetDate = remote.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (remote.targetDate != null && targetDate == null) throw InvalidGoalChangePage("INVALID_GOAL_TARGET_DATE")
        if (remote.linkedAccountId != null) {
            val account = accountDao.getById(userId, remote.linkedAccountId)
                ?: throw InvalidGoalChangePage("MISSING_LINKED_GOAL_ACCOUNT")
            if (account.currency != remote.currencyCode) throw InvalidGoalChangePage("GOAL_ACCOUNT_CURRENCY_MISMATCH")
        }

        val existing = goalDao.getGoalIncludingArchived(userId, remote.id)
        if (existing != null && remote.revision < existing.revision) {
            throw StaleGoalRevision(remote.id, existing.revision, remote.revision)
        }
        val entity = GoalEntity(
            id = remote.id,
            userId = userId,
            name = remote.name,
            targetMinor = remote.targetMinor,
            currencyCode = remote.currencyCode,
            targetDate = targetDate?.toString(),
            linkedAccountId = remote.linkedAccountId,
            iconKey = remote.iconKey,
            colorArgb = remote.colorArgb,
            status = status.name,
            revision = remote.revision,
            syncState = "SYNCED",
            createdAt = remote.createdAt?.let(::parseInstantMillis) ?: existing?.createdAt ?: clock.millis(),
            updatedAt = remote.updatedAt?.let(::parseInstantMillis) ?: clock.millis(),
        )
        if (existing != null && remote.revision == existing.revision) {
            if (!sameGoalContent(existing, entity)) {
                if (existing.syncState != "SYNCED") {
                    throw StaleGoalRevision(remote.id, existing.revision, remote.revision)
                }
                throw InvalidGoalChangePage("GOAL_REVISION_REUSED")
            }
            if (existing.syncState != "SYNCED") goalDao.upsertGoal(entity.copy(createdAt = existing.createdAt))
            return
        }
        if (existing != null && existing.syncState != "SYNCED" && !sameGoalContent(existing, entity)) {
            throw StaleGoalRevision(remote.id, existing.revision, remote.revision)
        }
        goalDao.upsertGoal(entity.copy(createdAt = existing?.createdAt ?: entity.createdAt))
    }

    private suspend fun applyGoalDelete(userId: String, change: SyncChangeItemDto) {
        val existing = goalDao.getGoalIncludingArchived(userId, change.entityId) ?: return
        if (change.revision < existing.revision) throw StaleGoalRevision(change.entityId, existing.revision, change.revision)
        if (goalDao.countEvents(userId, change.entityId) != 0) {
            throw InvalidGoalChangePage("REMOTE_DELETE_WOULD_REMOVE_GOAL_HISTORY")
        }
        if (goalDao.deleteEmptyGoal(userId, change.entityId) != 1) {
            throw InvalidGoalChangePage("REMOTE_DELETE_GOAL_STILL_REFERENCED")
        }
    }

    private suspend fun applyGoalEventUpsert(userId: String, change: SyncChangeItemDto) {
        if (change.operation.equals("DELETE", ignoreCase = true)) {
            throw InvalidGoalChangePage("GOAL_HISTORY_CANNOT_BE_DELETED")
        }
        val payload = change.payload ?: throw InvalidGoalChangePage("GOAL_EVENT_PAYLOAD_REQUIRED")
        val remote = json.decodeFromJsonElement<RemoteGoalEventDto>(payload)
        if (remote.userId != userId || remote.id != change.entityId) {
            throw InvalidGoalChangePage("GOAL_EVENT_OWNER_MISMATCH")
        }
        val goal = goalDao.getGoalIncludingArchived(userId, remote.goalId)
            ?: throw InvalidGoalChangePage("MISSING_GOAL_EVENT_PARENT")
        val eventType = runCatching { GoalEventType.valueOf(remote.eventType) }.getOrNull()
            ?: throw InvalidGoalChangePage("INVALID_GOAL_EVENT_TYPE")
        val delta = remote.deltaMinor ?: legacyDelta(eventType, remote.amountMinor)
        if (delta == 0L || remote.currencyCode != goal.currencyCode) {
            throw InvalidGoalChangePage("INVALID_GOAL_EVENT_VALUE")
        }
        if (remote.backingAccountId == null && remote.operationId != null) {
            throw InvalidGoalChangePage("NEW_GOAL_EVENT_REQUIRES_BACKING_ACCOUNT")
        }
        if (remote.backingAccountId != null) {
            val account = accountDao.getById(userId, remote.backingAccountId)
                ?: throw InvalidGoalChangePage("MISSING_GOAL_BACKING_ACCOUNT")
            if (account.currency != remote.currencyCode) throw InvalidGoalChangePage("GOAL_EVENT_ACCOUNT_CURRENCY_MISMATCH")
        }
        if (remote.transactionId != null && movementDao.getTransactionById(userId, remote.transactionId) == null) {
            throw InvalidGoalChangePage("MISSING_GOAL_EVENT_TRANSACTION")
        }
        val event = GoalEventEntity(
            id = remote.id,
            userId = userId,
            goalId = remote.goalId,
            transactionId = remote.transactionId,
            eventType = eventType.name,
            deltaMinor = delta,
            currencyCode = remote.currencyCode,
            backingAccountId = remote.backingAccountId,
            operationId = remote.operationId,
            occurredAt = parseInstantMillis(remote.occurredAt),
            createdAt = remote.createdAt?.let(::parseInstantMillis) ?: clock.millis(),
        )
        val existing = goalDao.getEvent(userId, remote.id)
        if (existing != null && !sameEventContent(existing, event)) {
            val receipt = existing.operationId?.let { movementDao.getReceipt(userId, it) }
            val isTransferReconciliation = existing.transactionId == null && event.transactionId != null &&
                existing.operationId != null && receipt != null && receipt.idempotencyKey == existing.operationId &&
                receipt.commandType in setOf("RESERVE_GOAL", "RELEASE_GOAL", "REASSIGN_GOAL_RESERVE") &&
                sameEventExceptTransaction(existing, event)
            if (!isTransferReconciliation) {
                throw InvalidGoalChangePage("GOAL_EVENT_ID_REUSED")
            }
            goalDao.upsertEvent(event)
        }
        if (existing == null) goalDao.insertEvent(event)
    }

    private fun legacyDelta(type: GoalEventType, amountMinor: Long): Long = when (type) {
        GoalEventType.RELEASE, GoalEventType.REASSIGNMENT_OUT -> -kotlin.math.abs(amountMinor)
        GoalEventType.ALLOCATION, GoalEventType.DIRECT_DEPOSIT, GoalEventType.REASSIGNMENT_IN ->
            kotlin.math.abs(amountMinor)
        GoalEventType.ADJUSTMENT -> amountMinor
    }

    private fun sameGoalContent(left: GoalEntity, right: GoalEntity): Boolean =
        left.id == right.id && left.userId == right.userId && left.name == right.name &&
            left.targetMinor == right.targetMinor && left.currencyCode == right.currencyCode &&
            left.targetDate == right.targetDate && left.linkedAccountId == right.linkedAccountId &&
            left.iconKey == right.iconKey && left.colorArgb == right.colorArgb && left.status == right.status

    private fun sameEventContent(left: GoalEventEntity, right: GoalEventEntity): Boolean =
        left.id == right.id && left.userId == right.userId && left.goalId == right.goalId &&
            left.transactionId == right.transactionId && left.eventType == right.eventType &&
            left.deltaMinor == right.deltaMinor && left.currencyCode == right.currencyCode &&
            left.backingAccountId == right.backingAccountId && left.operationId == right.operationId &&
            left.occurredAt == right.occurredAt

    private fun sameEventExceptTransaction(left: GoalEventEntity, right: GoalEventEntity): Boolean =
        left.id == right.id && left.userId == right.userId && left.goalId == right.goalId &&
            left.eventType == right.eventType && left.deltaMinor == right.deltaMinor &&
            left.currencyCode == right.currencyCode && left.backingAccountId == right.backingAccountId &&
            left.operationId == right.operationId && left.occurredAt == right.occurredAt

    private fun parseInstantMillis(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
            throw InvalidGoalChangePage("INVALID_REMOTE_TIMESTAMP")
        }

    private class InvalidGoalChangePage(val code: String) : RuntimeException()
    private class StaleGoalRevision(val goalId: String, val localRevision: Long, val remoteRevision: Long) : RuntimeException()

    private companion object {
        const val MAX_PAGE_SIZE = 100
        val SUPPORTED_CURRENCIES = setOf(Currency.PEN.name, Currency.USD.name)
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class RemoteGoalDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String,
    @SerialName("target_minor") val targetMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("linked_account_id") val linkedAccountId: String? = null,
    @SerialName("icon_key") val iconKey: String? = null,
    @SerialName("color_argb") val colorArgb: Int? = null,
    @SerialName("status") val status: String,
    @SerialName("revision") val revision: Long,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class RemoteGoalEventDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("goal_id") val goalId: String,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("event_type") val eventType: String,
    @SerialName("delta_minor") val deltaMinor: Long? = null,
    @SerialName("amount_minor") val amountMinor: Long = 0L,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("backing_account_id") val backingAccountId: String? = null,
    @SerialName("operation_id") val operationId: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("created_at") val createdAt: String? = null,
)
