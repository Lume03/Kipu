package com.kipu.app.feature.goals.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity

@Entity(
    tableName = "goals",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "linked_account_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["user_id", "status", "updated_at"]),
        Index(value = ["user_id", "linked_account_id"]),
    ],
)
data class GoalEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "target_minor") val targetMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "target_date") val targetDate: String? = null,
    @ColumnInfo(name = "linked_account_id") val linkedAccountId: String? = null,
    @ColumnInfo(name = "icon_key") val iconKey: String? = null,
    @ColumnInfo(name = "color_argb") val colorArgb: Int? = null,
    @ColumnInfo(name = "status", defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    @ColumnInfo(name = "revision", defaultValue = "0") val revision: Long = 0L,
    @ColumnInfo(name = "sync_state", defaultValue = "'PENDING'") val syncState: String = "PENDING",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "goal_events",
    primaryKeys = ["user_id", "id"],
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "goal_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "backing_account_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["user_id", "id"],
            childColumns = ["user_id", "transaction_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["user_id", "goal_id", "occurred_at", "id"]),
        Index(value = ["user_id", "backing_account_id", "occurred_at"]),
        Index(value = ["user_id", "transaction_id"]),
        Index(value = ["user_id", "operation_id"]),
    ],
)
data class GoalEventEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String? = null,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "delta_minor") val deltaMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "backing_account_id") val backingAccountId: String? = null,
    @ColumnInfo(name = "operation_id") val operationId: String? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    val isLegacyUnlinked: Boolean get() = backingAccountId == null
}
