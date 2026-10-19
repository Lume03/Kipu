package com.kipu.app.feature.notifications.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReminderScheduleDao {
    @Query("SELECT * FROM reminder_schedules WHERE user_id = :userId AND status = 'SCHEDULED' ORDER BY due_date ASC")
    fun observeScheduled(userId: String): Flow<List<ReminderScheduleEntity>>

    @Query("SELECT * FROM reminder_schedules WHERE user_id = :userId AND source_id = :sourceId ORDER BY due_date ASC")
    suspend fun getForSource(userId: String, sourceId: String): List<ReminderScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ReminderScheduleEntity)

    @Query("UPDATE reminder_schedules SET status = 'CANCELLED', updated_at = :updatedAt WHERE user_id = :userId AND source_id = :sourceId AND status = 'SCHEDULED' AND due_date != :currentDueDate")
    suspend fun cancelReplacedSchedules(userId: String, sourceId: String, currentDueDate: String, updatedAt: Instant): Int

    @Query("UPDATE reminder_schedules SET status = 'CANCELLED', updated_at = :updatedAt WHERE user_id = :userId AND source_id = :sourceId AND status = 'SCHEDULED'")
    suspend fun cancelSource(userId: String, sourceId: String, updatedAt: Instant): Int

    @Query("UPDATE reminder_schedules SET status = 'DELIVERED', updated_at = :updatedAt WHERE user_id = :userId AND source_id = :sourceId AND event_type = :eventType AND due_date = :dueDate AND status = 'SCHEDULED'")
    suspend fun markDelivered(userId: String, sourceId: String, eventType: String, dueDate: String, updatedAt: Instant): Int
}
