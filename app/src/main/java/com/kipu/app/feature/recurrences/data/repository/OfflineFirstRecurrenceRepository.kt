package com.kipu.app.feature.recurrences.data.repository

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.feature.notifications.data.local.ReminderEventReconciler
import com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator
import com.kipu.app.feature.recurrences.data.local.RecurrenceCommandOutboxEntity
import com.kipu.app.feature.recurrences.data.local.RecurrenceDao
import com.kipu.app.feature.recurrences.data.local.RecurrenceEntity
import com.kipu.app.feature.recurrences.data.local.RecurrenceOutboxDao
import com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderRequest
import com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderScheduler
import com.kipu.app.feature.recurrences.domain.RecurrenceCommandHasher
import com.kipu.app.feature.recurrences.domain.RecurrenceCommandReceipt
import com.kipu.app.feature.recurrences.domain.RecurrenceDraft
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import com.kipu.app.feature.recurrences.domain.RecurrenceUpdate
import com.kipu.app.feature.recurrences.domain.model.RecurrenceCommandResult
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import com.kipu.app.feature.recurrences.domain.schedule.RecurrenceScheduleCalculator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OfflineFirstRecurrenceRepository @Inject constructor(
    private val transactionRunner: DatabaseTransactionRunner,
    private val recurrenceDao: RecurrenceDao,
    private val outboxDao: RecurrenceOutboxDao,
    private val reminderScheduler: RecurrenceReminderScheduler,
    private val reminderEventReconciler: ReminderEventReconciler,
    private val reminderCoordinator: ReminderReconciliationCoordinator,
    private val clock: Clock,
) : RecurrenceRepository {
    private val reconciliationMutex = Mutex()
    override fun observeAll(userId: String): Flow<List<RecurrenceRule>> = recurrenceDao.observeAll(userId).map { rows -> rows.map { it.toDomain() } }
    override fun observe(userId: String, recurrenceId: String): Flow<RecurrenceRule?> = recurrenceDao.observe(userId, recurrenceId).map { it?.toDomain() }

    override suspend fun create(userId: String, draft: RecurrenceDraft): RecurrenceCommandResult = try {
        require(userId.isNotBlank()) { "Owner is required" }
        validateDraft(draft)
        val now = clock.millis()
        val recurrenceId = UUID.randomUUID().toString()
        val operationId = UUID.randomUUID().toString()
        val envelope = envelope("CREATE_RECURRENCE", recurrenceId, createPayload(draft), operationId)
        val entity = RecurrenceEntity(
            id = recurrenceId, userId = userId, templateTransaction = draft.templateTransaction.toString(),
            amountMode = draft.amountMode, frequency = draft.frequency, intervalCount = draft.intervalCount,
            customIntervalUnit = draft.customIntervalUnit, startDate = draft.startDate.toString(),
            endDate = draft.endDate?.toString(), nextDueDate = draft.nextDueDate.toString(),
            reminderLeadDays = draft.reminderLeadDays, status = "ACTIVE", isActive = true, revision = 1L,
            syncState = "PENDING", createdAt = now, updatedAt = now,
        )
        val command = outbox(userId, operationId, recurrenceId, "CREATE_RECURRENCE", null, envelope, now)
        transactionRunner { recurrenceDao.insert(entity); outboxDao.insert(command) }
        reconcileReminder(userId, recurrenceId)
        RecurrenceCommandResult.Pending(entity.toDomain(), operationId)
    } catch (exception: IllegalArgumentException) {
        RecurrenceCommandResult.Rejected("VALIDATION_FAILED", exception.message ?: "Invalid recurrence")
    }

    override suspend fun update(userId: String, recurrenceId: String, expectedRevision: Long, draft: RecurrenceUpdate): RecurrenceCommandResult = try {
        require(expectedRevision > 0L) { "Expected revision must be positive" }
        require(draft.amountMode in setOf("FIXED", "APPROXIMATE")) { "Invalid amount mode" }
        require(draft.reminderLeadDays in 0..30) { "Reminder lead time must be in 0..30" }
        validateTemplate(draft.templateTransaction)
        val current = recurrenceDao.get(userId, recurrenceId)
            ?: return RecurrenceCommandResult.Rejected("NOT_FOUND", "Recurrence was not found")
        if (current.revision != expectedRevision) return RecurrenceCommandResult.Conflict(recurrenceId, expectedRevision, current.revision)
        require(current.status != "CANCELLED") { "Cancelled recurrence is terminal" }
        require(draft.endDate == null || draft.endDate >= LocalDate.parse(current.startDate)) { "End date precedes start date" }
        require(draft.endDate == null || draft.nextDueDate <= draft.endDate) { "Next due date exceeds end date" }
        require(RecurrenceScheduleCalculator.isOccurrence(LocalDate.parse(current.startDate), draft.frequency, draft.intervalCount, draft.customIntervalUnit, draft.nextDueDate)) { "Next due date is not on the schedule" }
        val now = clock.millis()
        val nextRevision = Math.addExact(expectedRevision, 1L)
        val operationId = UUID.randomUUID().toString()
        val envelope = envelope("UPDATE_RECURRENCE", recurrenceId, updatePayload(draft), operationId, expectedRevision)
        val updated = current.copy(
            templateTransaction = draft.templateTransaction.toString(), amountMode = draft.amountMode,
            frequency = draft.frequency, intervalCount = draft.intervalCount, customIntervalUnit = draft.customIntervalUnit,
            endDate = draft.endDate?.toString(), nextDueDate = draft.nextDueDate.toString(),
            reminderLeadDays = draft.reminderLeadDays, revision = nextRevision, syncState = "PENDING", updatedAt = now,
        )
        val command = outbox(userId, operationId, recurrenceId, "UPDATE_RECURRENCE", expectedRevision, envelope, now)
        var changed = 0
        transactionRunner {
            changed = recurrenceDao.updateIfRevisionMatches(
                userId, recurrenceId, expectedRevision, updated.templateTransaction, draft.amountMode, draft.frequency,
                draft.intervalCount, draft.customIntervalUnit, draft.endDate?.toString(), draft.nextDueDate.toString(),
                draft.reminderLeadDays, nextRevision, "PENDING", now,
            )
            if (changed == 1) outboxDao.insert(command)
        }
        if (changed != 1) RecurrenceCommandResult.Conflict(recurrenceId, expectedRevision, recurrenceDao.get(userId, recurrenceId)?.revision)
        else {
            reconcileReminder(userId, recurrenceId)
            RecurrenceCommandResult.Pending(updated.toDomain(), operationId)
        }
    } catch (exception: IllegalArgumentException) {
        RecurrenceCommandResult.Rejected("VALIDATION_FAILED", exception.message ?: "Invalid recurrence update")
    } catch (exception: ArithmeticException) {
        RecurrenceCommandResult.Rejected("REVISION_OVERFLOW", "Recurrence revision cannot be incremented")
    }

    override suspend fun setStatus(userId: String, recurrenceId: String, expectedRevision: Long, target: String, confirmedNextDueDate: LocalDate?): RecurrenceCommandResult = try {
        require(expectedRevision > 0L) { "Expected revision must be positive" }
        val current = recurrenceDao.get(userId, recurrenceId)
            ?: return RecurrenceCommandResult.Rejected("NOT_FOUND", "Recurrence was not found")
        if (current.revision != expectedRevision) return RecurrenceCommandResult.Conflict(recurrenceId, expectedRevision, current.revision)
        val nextDate = when (target) {
            "PAUSED" -> { require(current.status == "ACTIVE"); LocalDate.parse(current.nextDueDate) }
            "CANCELLED" -> { require(current.status == "ACTIVE" || current.status == "PAUSED"); LocalDate.parse(current.nextDueDate) }
            "ACTIVE" -> {
                require(current.status == "PAUSED") { "Only paused recurrence may resume" }
                val firstFuture = RecurrenceScheduleCalculator.firstOccurrenceAfter(
                    LocalDate.parse(current.startDate), current.frequency, current.intervalCount,
                    current.customIntervalUnit, LocalDate.now(clock),
                )
                require(confirmedNextDueDate != null && confirmedNextDueDate == firstFuture) { "Resume requires confirmation of the first future due date" }
                require(current.endDate == null || confirmedNextDateWithinEnd(confirmedNextDueDate, current.endDate)) { "No future due date remains before end date" }
                confirmedNextDueDate
            }
            else -> throw IllegalArgumentException("Unsupported recurrence state")
        }
        val now = clock.millis()
        val nextRevision = Math.addExact(expectedRevision, 1L)
        val operationId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("status", target)
            if (target == "ACTIVE") put("confirmed_next_due_date", nextDate.toString())
        }
        val envelope = envelope("SET_RECURRENCE_STATUS", recurrenceId, payload, operationId, expectedRevision)
        val command = outbox(userId, operationId, recurrenceId, "SET_RECURRENCE_STATUS", expectedRevision, envelope, now)
        var changed = 0
        transactionRunner {
            changed = recurrenceDao.setStatusIfRevisionMatches(
                userId, recurrenceId, expectedRevision, target, target == "ACTIVE", nextDate.toString(), nextRevision, "PENDING", now,
            )
            if (changed == 1) outboxDao.insert(command)
        }
        if (changed != 1) RecurrenceCommandResult.Conflict(recurrenceId, expectedRevision, recurrenceDao.get(userId, recurrenceId)?.revision)
        else {
            reconcileReminder(userId, recurrenceId)
            RecurrenceCommandResult.Pending(current.toDomain().copy(
                status = RecurrenceStatus.valueOf(target), revision = nextRevision, syncState = RecurrenceSyncState.PENDING,
                nextDueDate = nextDate, updatedAt = Instant.ofEpochMilli(now),
            ), operationId)
        }
    } catch (exception: IllegalArgumentException) {
        RecurrenceCommandResult.Rejected("VALIDATION_FAILED", exception.message ?: "Invalid lifecycle transition")
    } catch (exception: ArithmeticException) {
        RecurrenceCommandResult.Rejected("REVISION_OVERFLOW", "Recurrence revision cannot be incremented")
    }

    override suspend fun reconcileReceipt(receipt: RecurrenceCommandReceipt) = reconciliationMutex.withLock {
        require(receipt.userId.isNotBlank() && receipt.operationId.isNotBlank())
        val command = outboxDao.get(receipt.userId, receipt.operationId) ?: return@withLock
        val now = clock.millis()
        when (receipt.outcome) {
            "APPLIED", "DUPLICATE" -> {
                val revision = requireNotNull(receipt.resultingRevision) { "Successful receipt requires resulting revision" }
                require(revision > 0L)
                transactionRunner {
                    outboxDao.updateResult(receipt.userId, receipt.operationId, receipt.outcome, null, null, receipt.resultPayload, now)
                    recurrenceDao.get(receipt.userId, command.recurrenceId)?.let { current ->
                        val pending = outboxDao.countPending(receipt.userId, command.recurrenceId)
                        if (current.revision == revision && pending == 0) {
                            recurrenceDao.setSyncState(receipt.userId, command.recurrenceId, current.revision, "SYNCED", null, now)
                        } else if (current.revision != revision) {
                            recurrenceDao.setSyncState(receipt.userId, command.recurrenceId, current.revision, "CONFLICT", "REVISION_MISMATCH", now)
                        }
                    }
                }
            }
            "CONFLICT" -> transactionRunner {
                outboxDao.updateResult(receipt.userId, receipt.operationId, "CONFLICT", null, receipt.errorCode ?: "REVISION_MISMATCH", receipt.resultPayload, now)
                recurrenceDao.get(receipt.userId, command.recurrenceId)?.let { current ->
                    recurrenceDao.setSyncState(receipt.userId, command.recurrenceId, current.revision, "CONFLICT", receipt.errorCode ?: "REVISION_MISMATCH", now)
                }
            }
            "RETRYABLE" -> transactionRunner {
                outboxDao.updateResult(receipt.userId, receipt.operationId, "RETRYABLE", receipt.retryAt, receipt.errorCode, receipt.resultPayload, now)
            }
            "REJECTED" -> transactionRunner {
                outboxDao.updateResult(receipt.userId, receipt.operationId, "FAILED", null, receipt.errorCode ?: "VALIDATION_FAILED", receipt.resultPayload, now)
                recurrenceDao.get(receipt.userId, command.recurrenceId)?.let { current ->
                    recurrenceDao.setSyncState(receipt.userId, command.recurrenceId, current.revision, "FAILED", receipt.errorCode, now)
                }
            }
            else -> throw IllegalArgumentException("Unsupported receipt outcome")
        }
    }

    override suspend fun applyChangeFeed(userId: String, sequence: Long, changes: List<RecurrenceRule>) = reconciliationMutex.withLock {
        require(userId.isNotBlank() && sequence >= 0L)
        require(changes.all { it.userId == userId }) { "Change feed owner mismatch" }
        val now = clock.millis()
        transactionRunner {
            val checkpoint = recurrenceDao.checkpoint(userId)
            if (checkpoint != null && sequence <= checkpoint.sequence) return@transactionRunner
            changes.forEach { remote ->
                val current = recurrenceDao.get(userId, remote.id)
                if (current == null) {
                    recurrenceDao.upsert(remote.toEntity())
                } else if (remote.revision > current.revision) {
                    if (outboxDao.countPending(userId, remote.id) > 0) {
                        recurrenceDao.setSyncState(userId, remote.id, current.revision, "CONFLICT", "REMOTE_REVISION_AHEAD", now)
                    } else {
                        recurrenceDao.upsert(remote.toEntity().copy(syncState = "SYNCED", syncError = null))
                    }
                }
            }
            recurrenceDao.saveCheckpoint(com.kipu.app.feature.recurrences.data.local.RecurrenceSyncCheckpointEntity(userId, sequence, now))
        }
        changes.forEach { recurrence -> reconcileReminder(userId, recurrence.id) }
    }

    private fun validateDraft(draft: RecurrenceDraft) {
        require(draft.amountMode in setOf("FIXED", "APPROXIMATE")) { "Invalid amount mode" }
        require(draft.reminderLeadDays in 0..30) { "Reminder lead time must be in 0..30" }
        validateTemplate(draft.templateTransaction)
        require(draft.endDate == null || draft.endDate >= draft.startDate) { "End date precedes start date" }
        require(draft.endDate == null || draft.nextDueDate <= draft.endDate) { "Next due date exceeds end date" }
        require(RecurrenceScheduleCalculator.isOccurrence(draft.startDate, draft.frequency, draft.intervalCount, draft.customIntervalUnit, draft.nextDueDate)) { "Next due date is not on the schedule" }
    }

    private fun validateTemplate(template: JsonObject) {
        val keys = setOf("concept", "type", "amount_minor", "currency_code", "source_account_id", "destination_account_id", "category_id", "merchant_id", "merchant_provisional_text")
        require(template.keys == keys) { "Template transaction fields are incomplete or unknown" }
        require((template["concept"] as? JsonPrimitive)?.content?.isNotBlank() == true) { "Template concept is required" }
        require((template["type"] as? JsonPrimitive)?.content in setOf("EXPENSE", "INCOME", "TRANSFER")) { "Template type is invalid" }
        require((template["currency_code"] as? JsonPrimitive)?.content?.matches(Regex("[A-Z]{3}")) == true) { "Currency code is invalid" }
        val amount = template["amount_minor"] as? JsonPrimitive
        require(amount != null && !amount.isString && amount.content.toBigDecimalOrNull()?.let {
            it.stripTrailingZeros().scale() <= 0 && it >= java.math.BigDecimal.ONE && it <= java.math.BigDecimal("9223372036854775807")
        } == true) { "Template amount must be a positive exact integer" }
        require(!(template["merchant_id"] !is JsonNull && template["merchant_provisional_text"] !is JsonNull)) { "Merchant ID and provisional text are mutually exclusive" }
        for (key in keys - setOf("concept", "type", "amount_minor", "currency_code")) {
            val value = template[key]
            require(value is JsonNull || value is JsonPrimitive && value.isString) { "$key must be a string or null" }
        }
    }

    private fun createPayload(draft: RecurrenceDraft) = buildJsonObject {
        put("amount_mode", draft.amountMode); put("custom_interval_unit", draft.customIntervalUnit?.let(::JsonPrimitive) ?: JsonNull)
        put("end_date", draft.endDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("frequency", draft.frequency); put("interval_count", JsonPrimitive(draft.intervalCount))
        put("next_due_date", draft.nextDueDate.toString()); put("reminder_lead_days", draft.reminderLeadDays)
        put("start_date", draft.startDate.toString()); put("template_transaction", draft.templateTransaction)
    }

    private fun updatePayload(draft: RecurrenceUpdate) = buildJsonObject {
        put("amount_mode", draft.amountMode); put("custom_interval_unit", draft.customIntervalUnit?.let(::JsonPrimitive) ?: JsonNull)
        put("end_date", draft.endDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("frequency", draft.frequency); put("interval_count", JsonPrimitive(draft.intervalCount))
        put("next_due_date", draft.nextDueDate.toString()); put("reminder_lead_days", draft.reminderLeadDays)
        put("template_transaction", draft.templateTransaction)
    }

    private fun envelope(type: String, recurrenceId: String, payload: JsonObject, operationId: String, expectedRevision: Long? = null) = buildJsonObject {
        put("command_type", type); put("contract_version", 1); put("operation_id", operationId); put("request_hash", "")
        put("recurrence_id", recurrenceId); if (expectedRevision != null) put("expected_revision", expectedRevision)
        put("payload", payload)
    }

    private fun outbox(userId: String, operationId: String, recurrenceId: String, type: String, expectedRevision: Long?, envelope: JsonObject, now: Long) =
        RecurrenceCommandOutboxEntity(
            userId, operationId, recurrenceId, type, RecurrenceCommandHasher.sha256(envelope), 1,
            expectedRevision, RecurrenceCommandHasher.canonicalBytes(envelope), createdAt = now, updatedAt = now,
        )

    private fun confirmedNextDateWithinEnd(date: LocalDate, endDate: String) = date <= LocalDate.parse(endDate)

    private suspend fun reconcileReminder(userId: String, recurrenceId: String) {
        reminderCoordinator.exclusive {
            val current = recurrenceDao.get(userId, recurrenceId)
            if (current == null || current.status != "ACTIVE" || !current.isActive || current.deletedAt != null || current.reminderLeadDays !in 1..30) {
                reminderScheduler.cancel(userId, recurrenceId)
                reminderEventReconciler.invalidateSource(userId, recurrenceId, clock.instant())
                return@exclusive
            }
            reminderEventReconciler.retainCurrentDate(userId, recurrenceId, current.nextDueDate, clock.instant())
            reminderScheduler.schedule(
                RecurrenceReminderRequest(
                    userId = userId,
                    recurrenceId = recurrenceId,
                    dueDate = LocalDate.parse(current.nextDueDate),
                    leadDays = current.reminderLeadDays,
                ),
            )
        }
    }

    private fun RecurrenceEntity.toDomain() = RecurrenceRule(
        id, userId, kotlinx.serialization.json.Json.parseToJsonElement(templateTransaction) as JsonObject,
        amountMode, frequency, intervalCount, customIntervalUnit, LocalDate.parse(startDate), endDate?.let(LocalDate::parse),
        LocalDate.parse(nextDueDate), reminderLeadDays, RecurrenceStatus.valueOf(status), revision,
        RecurrenceSyncState.valueOf(syncState), Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(updatedAt),
    )

    private fun RecurrenceRule.toEntity() = RecurrenceEntity(
        id = id, userId = userId, templateTransaction = templateTransaction.toString(), amountMode = amountMode,
        frequency = frequency, intervalCount = intervalCount, customIntervalUnit = customIntervalUnit,
        startDate = startDate.toString(), endDate = endDate?.toString(), nextDueDate = nextDueDate.toString(),
        reminderLeadDays = reminderLeadDays, status = status.name, isActive = status == RecurrenceStatus.ACTIVE,
        revision = revision, syncState = syncState.name, createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
    )
}
