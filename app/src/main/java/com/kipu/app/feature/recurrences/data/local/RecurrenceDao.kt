package com.kipu.app.feature.recurrences.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurrenceDao {
    @Query("SELECT * FROM recurrences WHERE user_id = :userId ORDER BY updated_at DESC, id ASC")
    fun observeAll(userId: String): Flow<List<RecurrenceEntity>>

    @Query("SELECT * FROM recurrences WHERE user_id = :userId AND id = :recurrenceId LIMIT 1")
    fun observe(userId: String, recurrenceId: String): Flow<RecurrenceEntity?>

    @Query("SELECT * FROM recurrences WHERE user_id = :userId AND id = :recurrenceId LIMIT 1")
    suspend fun get(userId: String, recurrenceId: String): RecurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(recurrence: RecurrenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recurrence: RecurrenceEntity)

    @Query("UPDATE recurrences SET template_transaction=:templateTransaction, amount_mode=:amountMode, frequency=:frequency, interval_count=:intervalCount, custom_interval_unit=:customIntervalUnit, end_date=:endDate, next_due_date=:nextDueDate, reminder_lead_days=:reminderLeadDays, revision=:newRevision, sync_state=:syncState, sync_error=NULL, updated_at=:updatedAt WHERE user_id=:userId AND id=:recurrenceId AND revision=:expectedRevision AND status != 'CANCELLED'")
    suspend fun updateIfRevisionMatches(
        userId: String,
        recurrenceId: String,
        expectedRevision: Long,
        templateTransaction: String,
        amountMode: String,
        frequency: String,
        intervalCount: Long,
        customIntervalUnit: String?,
        endDate: String?,
        nextDueDate: String,
        reminderLeadDays: Int,
        newRevision: Long,
        syncState: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE recurrences SET status=:status, is_active=:isActive, next_due_date=:nextDueDate, revision=:newRevision, sync_state=:syncState, sync_error=NULL, updated_at=:updatedAt WHERE user_id=:userId AND id=:recurrenceId AND revision=:expectedRevision AND status != 'CANCELLED'")
    suspend fun setStatusIfRevisionMatches(
        userId: String,
        recurrenceId: String,
        expectedRevision: Long,
        status: String,
        isActive: Boolean,
        nextDueDate: String,
        newRevision: Long,
        syncState: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE recurrences SET sync_state=:syncState, sync_error=:errorCode, revision=MAX(revision,:remoteRevision), updated_at=:updatedAt WHERE user_id=:userId AND id=:recurrenceId")
    suspend fun applyRemoteState(userId: String, recurrenceId: String, syncState: String, errorCode: String?, remoteRevision: Long, updatedAt: Long): Int

    @Query("UPDATE recurrences SET sync_state=:syncState, sync_error=:errorCode, updated_at=:updatedAt WHERE user_id=:userId AND id=:recurrenceId AND revision=:expectedRevision")
    suspend fun setSyncState(userId: String, recurrenceId: String, expectedRevision: Long, syncState: String, errorCode: String?, updatedAt: Long): Int

    @Query("SELECT * FROM recurrence_sync_checkpoints WHERE user_id=:userId LIMIT 1")
    suspend fun checkpoint(userId: String): RecurrenceSyncCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckpoint(checkpoint: RecurrenceSyncCheckpointEntity)
}
