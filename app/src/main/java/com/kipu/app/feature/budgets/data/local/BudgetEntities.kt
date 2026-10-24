package com.kipu.app.feature.budgets.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(value = ["user_id", "status"], name = "budget_owner_status_idx"),
        Index(value = ["user_id", "created_on"], name = "budget_owner_created_idx"),
    ],
)
data class BudgetEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "name") val name: String,
    /** Null represents the TOTAL scope; a value represents that category and its descendants. */
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "period_type") val periodType: String,
    @ColumnInfo(name = "range_start_on") val rangeStartOn: String?,
    @ColumnInfo(name = "range_end_on") val rangeEndOn: String?,
    /** Comma-separated integer basis points; values are validated by the domain. */
    @ColumnInfo(name = "alert_thresholds_bps") val alertThresholdsBps: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "created_on") val createdOn: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "pending_scope_type") val pendingScopeType: String? = null,
    @ColumnInfo(name = "pending_scope_category_id") val pendingScopeCategoryId: String? = null,
    @ColumnInfo(name = "pending_schedule_type") val pendingScheduleType: String? = null,
    @ColumnInfo(name = "pending_range_start_on") val pendingRangeStartOn: String? = null,
    @ColumnInfo(name = "pending_range_end_on") val pendingRangeEndOn: String? = null,
    @ColumnInfo(name = "pending_effective_from") val pendingEffectiveFrom: String? = null,
)

@Entity(
    tableName = "budget_periods",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(
            value = ["user_id", "budget_id", "starts_on", "ends_on"],
            unique = true,
            name = "budget_period_owner_interval_uq",
        ),
        Index(value = ["user_id", "budget_id", "starts_on"], name = "budget_period_owner_budget_start_idx"),
    ],
)
data class BudgetPeriodEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "budget_id") val budgetId: String,
    @ColumnInfo(name = "starts_on") val startsOn: String,
    /** Exclusive civil date boundary, matching the approved [start, end) interval. */
    @ColumnInfo(name = "ends_on") val endsOn: String,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    /** Snapshot of the effective category scope for this period; null means TOTAL. */
    @ColumnInfo(name = "scope_category_id") val scopeCategoryId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "budget_alert_ledger",
    primaryKeys = ["user_id", "budget_id", "period_id", "threshold_bps"],
    indices = [Index(value = ["user_id", "dedupe_key"], unique = true, name = "budget_alert_owner_dedupe_uq")],
)
data class BudgetAlertLedgerEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "budget_id") val budgetId: String,
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "threshold_bps") val thresholdBps: Int,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    @ColumnInfo(name = "emitted_at") val emittedAt: Long,
)

@Entity(tableName = "budget_tombstones", primaryKeys = ["user_id", "budget_id"])
data class BudgetTombstoneEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "budget_id") val budgetId: String,
    @ColumnInfo(name = "last_revision") val lastRevision: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
)

@Entity(
    tableName = "budget_sync_outbox",
    indices = [
        Index(value = ["user_id", "operation_id"], unique = true, name = "budget_outbox_owner_operation_uq"),
        Index(value = ["user_id", "state", "sequence"], name = "budget_outbox_owner_state_sequence_idx"),
    ],
)
data class BudgetSyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "sequence") val sequence: Long = 0,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "command_type") val commandType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "expected_revision") val expectedRevision: Long? = null,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "state") val state: String = "PENDING",
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
