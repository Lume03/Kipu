package com.kipu.app.feature.goals.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "goal_command_outbox",
    primaryKeys = ["user_id", "operation_id"],
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "goal_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["user_id", "state", "next_attempt_at", "created_at"]),
        Index(value = ["user_id", "goal_id", "created_at"]),
    ],
)
data class GoalOutboxEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    @ColumnInfo(name = "command_type") val commandType: String,
    @ColumnInfo(name = "request_hash") val requestHash: String,
    @ColumnInfo(name = "contract_version", defaultValue = "1") val contractVersion: Int = 1,
    @ColumnInfo(name = "expected_revision") val expectedRevision: Long? = null,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "state", defaultValue = "'PENDING'") val state: String = "PENDING",
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "lease_until") val leaseUntil: Long? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "result_payload") val resultPayload: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
