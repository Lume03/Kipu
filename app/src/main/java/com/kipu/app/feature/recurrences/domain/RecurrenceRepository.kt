package com.kipu.app.feature.recurrences.domain

import com.kipu.app.feature.recurrences.domain.model.RecurrenceCommandResult
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface RecurrenceRepository {
    fun observeAll(userId: String): Flow<List<RecurrenceRule>>
    fun observe(userId: String, recurrenceId: String): Flow<RecurrenceRule?>
    suspend fun create(userId: String, draft: RecurrenceDraft): RecurrenceCommandResult
    suspend fun update(userId: String, recurrenceId: String, expectedRevision: Long, draft: RecurrenceUpdate): RecurrenceCommandResult
    suspend fun setStatus(userId: String, recurrenceId: String, expectedRevision: Long, target: String, confirmedNextDueDate: LocalDate? = null): RecurrenceCommandResult
    suspend fun reconcileReceipt(receipt: RecurrenceCommandReceipt)
    suspend fun applyChangeFeed(userId: String, sequence: Long, changes: List<RecurrenceRule>)
}

data class RecurrenceCommandReceipt(
    val userId: String,
    val operationId: String,
    val outcome: String,
    val resultingRevision: Long? = null,
    val errorCode: String? = null,
    val resultPayload: String? = null,
    val retryAt: Long? = null,
)

data class RecurrenceDraft(
    val templateTransaction: JsonObject,
    val amountMode: String,
    val frequency: String,
    val intervalCount: Long,
    val customIntervalUnit: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextDueDate: LocalDate,
    val reminderLeadDays: Int = 2,
)

data class RecurrenceUpdate(
    val templateTransaction: JsonObject,
    val amountMode: String,
    val frequency: String,
    val intervalCount: Long,
    val customIntervalUnit: String?,
    val endDate: LocalDate?,
    val nextDueDate: LocalDate,
    val reminderLeadDays: Int,
)
