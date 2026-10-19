package com.kipu.app.feature.notifications.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "local_reminder_events",
    primaryKeys = ["user_id", "id"],
    indices = [
        Index(
            value = ["user_id", "source_id", "event_type", "due_date"],
            unique = true,
            name = "local_reminder_events_owner_source_type_date_uq",
        ),
        Index(value = ["user_id", "created_at"], name = "local_reminder_events_owner_created_idx"),
    ],
)
data class LocalReminderEventEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "event_type") val eventType: String = ReminderScheduleEntity.BILLING_DUE,
    /** ISO-8601 civil date (YYYY-MM-DD), not an instant. */
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
