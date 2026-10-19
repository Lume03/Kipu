package com.kipu.app.feature.recurrences.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "recurrences",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(value = ["user_id", "status", "updated_at"]),
        Index(value = ["user_id", "next_due_date", "status"]),
    ],
)
data class RecurrenceEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "template_transaction") val templateTransaction: String,
    @ColumnInfo(name = "amount_mode") val amountMode: String,
    @ColumnInfo(name = "frequency") val frequency: String,
    @ColumnInfo(name = "interval_count") val intervalCount: Long,
    @ColumnInfo(name = "custom_interval_unit") val customIntervalUnit: String?,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String?,
    @ColumnInfo(name = "next_due_date") val nextDueDate: String,
    @ColumnInfo(name = "reminder_lead_days") val reminderLeadDays: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_state") val syncState: String = "PENDING",
    @ColumnInfo(name = "sync_error") val syncError: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "recurrence_command_outbox",
    primaryKeys = ["user_id", "operation_id"],
    indices = [
        Index(value = ["user_id", "state", "next_attempt_at", "created_at"]),
        Index(value = ["user_id", "recurrence_id", "created_at"]),
    ],
)
data class RecurrenceCommandOutboxEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "recurrence_id") val recurrenceId: String,
    @ColumnInfo(name = "command_type") val commandType: String,
    @ColumnInfo(name = "request_hash") val requestHash: String,
    @ColumnInfo(name = "contract_version") val contractVersion: Int = 1,
    @ColumnInfo(name = "expected_revision") val expectedRevision: Long? = null,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "state") val state: String = "PENDING",
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "lease_until") val leaseUntil: Long? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "result_payload") val resultPayload: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "recurrence_sync_checkpoints", primaryKeys = ["user_id"])
data class RecurrenceSyncCheckpointEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
