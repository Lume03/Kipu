package com.kipu.app.feature.notifications.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LocalReminderEventDao {
    @Query("SELECT * FROM local_reminder_events WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at DESC")
    abstract fun observeActive(userId: String): Flow<List<LocalReminderEventEntity>>

    @Query("SELECT COUNT(*) FROM local_reminder_events WHERE user_id = :userId AND is_read = 0 AND deleted_at IS NULL")
    abstract fun observeUnreadCount(userId: String): Flow<Int>

    @Query("SELECT * FROM local_reminder_events WHERE user_id = :userId AND id = :id LIMIT 1")
    protected abstract suspend fun getById(userId: String, id: String): LocalReminderEventEntity?

    @Query("SELECT * FROM local_reminder_events WHERE user_id = :userId AND source_id = :sourceId AND event_type = :eventType AND due_date = :dueDate LIMIT 1")
    protected abstract suspend fun getByTuple(userId: String, sourceId: String, eventType: String, dueDate: String): LocalReminderEventEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(event: LocalReminderEventEntity)

    /** Retry-safe insert. A stable ID or tuple collision with different identity is rejected, never replaced. */
    @Transaction
    open suspend fun upsert(event: LocalReminderEventEntity) {
        val existingById = getById(event.userId, event.id)
        require(existingById == null || existingById.sameTuple(event)) {
            "Local reminder ID is already owned by a different event tuple"
        }
        val existingByTuple = getByTuple(event.userId, event.sourceId, event.eventType, event.dueDate)
        require(existingByTuple == null || existingByTuple.id == event.id) {
            "Local reminder tuple is already owned by a different event ID"
        }
        if (existingByTuple == null) insert(event)
    }

    @Query("UPDATE local_reminder_events SET is_read = 1 WHERE user_id = :userId AND id = :id AND deleted_at IS NULL")
    abstract suspend fun markRead(userId: String, id: String): Int

    @Query("UPDATE local_reminder_events SET is_read = 1 WHERE user_id = :userId AND is_read = 0 AND deleted_at IS NULL")
    abstract suspend fun markAllRead(userId: String): Int

    @Query("UPDATE local_reminder_events SET deleted_at = :deletedAt WHERE user_id = :userId AND id = :id AND deleted_at IS NULL")
    abstract suspend fun dismiss(userId: String, id: String, deletedAt: Instant): Int

    @Query("UPDATE local_reminder_events SET deleted_at = :deletedAt WHERE user_id = :userId AND source_id = :sourceId AND due_date != :currentDueDate AND deleted_at IS NULL")
    abstract suspend fun tombstoneReplacedEvents(userId: String, sourceId: String, currentDueDate: String, deletedAt: Instant): Int

    @Query("UPDATE local_reminder_events SET deleted_at = :deletedAt WHERE user_id = :userId AND source_id = :sourceId AND deleted_at IS NULL")
    abstract suspend fun tombstoneSource(userId: String, sourceId: String, deletedAt: Instant): Int

    private fun LocalReminderEventEntity.sameTuple(other: LocalReminderEventEntity): Boolean =
        userId == other.userId && sourceId == other.sourceId && eventType == other.eventType && dueDate == other.dueDate
}
