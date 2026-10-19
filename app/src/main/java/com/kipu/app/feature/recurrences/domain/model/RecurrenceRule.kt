package com.kipu.app.feature.recurrences.domain.model

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject

enum class RecurrenceStatus { ACTIVE, PAUSED, CANCELLED }
enum class RecurrenceSyncState { PENDING, SYNCED, CONFLICT, FAILED }

data class RecurrenceRule(
    val id: String,
    val userId: String,
    val templateTransaction: JsonObject,
    val amountMode: String,
    val frequency: String,
    val intervalCount: Long,
    val customIntervalUnit: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextDueDate: LocalDate,
    val reminderLeadDays: Int,
    val status: RecurrenceStatus,
    val revision: Long,
    val syncState: RecurrenceSyncState,
    val createdAt: Instant,
    val updatedAt: Instant,
)

sealed interface RecurrenceCommandResult {
    data class Pending(val recurrence: RecurrenceRule, val operationId: String) : RecurrenceCommandResult
    data class Synced(val recurrence: RecurrenceRule, val operationId: String) : RecurrenceCommandResult
    data class Conflict(val recurrenceId: String, val expectedRevision: Long, val currentRevision: Long?) : RecurrenceCommandResult
    data class Rejected(val code: String, val message: String) : RecurrenceCommandResult
}
