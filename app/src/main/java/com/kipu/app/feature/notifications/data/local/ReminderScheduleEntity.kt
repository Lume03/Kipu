package com.kipu.app.feature.notifications.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "reminder_schedules",
    primaryKeys = ["user_id", "source_id", "event_type", "due_date"],
    indices = [
        Index(value = ["user_id", "source_id", "status"], name = "reminder_schedules_owner_source_status_idx"),
    ],
)
data class ReminderScheduleEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "event_type") val eventType: String = BILLING_DUE,
    /** ISO-8601 civil date (YYYY-MM-DD), not an instant. */
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "lead_days") val leadDays: Int,
    @ColumnInfo(name = "status") val status: String = STATUS_SCHEDULED,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val BILLING_DUE = "BILLING_DUE"
        const val STATUS_SCHEDULED = "SCHEDULED"
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
