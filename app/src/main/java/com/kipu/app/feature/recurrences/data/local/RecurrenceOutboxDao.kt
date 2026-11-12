package com.kipu.app.feature.recurrences.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface RecurrenceOutboxDao {

    @Query("UPDATE recurrence_command_outbox SET state='BLOCKED', last_error_code='BLOCKED_BY_PREVIOUS' WHERE user_id=:userId AND state IN ('PENDING','RETRYABLE','WAITING_FOR_AUTH') AND EXISTS (SELECT 1 FROM recurrence_command_outbox p WHERE p.user_id=recurrence_command_outbox.user_id AND p.recurrence_id=recurrence_command_outbox.recurrence_id AND (p.created_at<recurrence_command_outbox.created_at OR (p.created_at=recurrence_command_outbox.created_at AND p.rowid<recurrence_command_outbox.rowid)) AND p.state IN ('FAILED','CONFLICT'))")
    suspend fun blockBehindFailures(userId: String)


    @Query("UPDATE recurrence_command_outbox SET state='WAITING_FOR_AUTH' WHERE user_id=:userId AND state IN ('PENDING','RETRYABLE')")
    suspend fun waitForAuth(userId: String)


    @Query("UPDATE recurrence_command_outbox SET state='PENDING', next_attempt_at=NULL WHERE user_id=:userId AND state='WAITING_FOR_AUTH'")
    suspend fun resumeAuth(userId: String)

    @Query("UPDATE recurrence_command_outbox SET state='BLOCKED', last_error_code='BLOCKED_BY_PREVIOUS', lease_until=NULL, next_attempt_at=NULL WHERE user_id=:userId AND recurrence_id=:recurrenceId AND (created_at>:createdAt OR (created_at=:createdAt AND rowid>(SELECT rowid FROM recurrence_command_outbox WHERE user_id=:userId AND operation_id=:id))) AND state IN ('PENDING','RETRYABLE','WAITING_FOR_AUTH')")
    suspend fun blockSuccessors(userId: String, recurrenceId: String, createdAt: Long, id: String)

    @Query("UPDATE recurrence_command_outbox SET state='PENDING', last_error_code=NULL WHERE user_id=:userId AND recurrence_id=:recurrenceId AND state='BLOCKED' AND NOT EXISTS (SELECT 1 FROM recurrence_command_outbox p WHERE p.user_id=:userId AND p.recurrence_id=:recurrenceId AND p.state IN ('CONFLICT','FAILED'))")
    suspend fun unblock(userId: String, recurrenceId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(command: RecurrenceCommandOutboxEntity)

    @Update
    suspend fun update(command: RecurrenceCommandOutboxEntity): Int

    @Query("SELECT * FROM recurrence_command_outbox WHERE user_id=:userId AND operation_id=:operationId LIMIT 1")
    suspend fun get(userId: String, operationId: String): RecurrenceCommandOutboxEntity?

    @Query("SELECT * FROM recurrence_command_outbox WHERE user_id=:userId AND recurrence_id=:recurrenceId ORDER BY created_at ASC")
    suspend fun getForRecurrence(userId: String, recurrenceId: String): List<RecurrenceCommandOutboxEntity>

    @Query("""
        SELECT c.* FROM recurrence_command_outbox c
        WHERE c.user_id=:userId AND (
            (c.state IN ('PENDING','RETRYABLE') AND (c.next_attempt_at IS NULL OR c.next_attempt_at<=:now))
            OR (c.state='LEASED' AND c.lease_until<=:now)
        ) AND NOT EXISTS (
            SELECT 1 FROM recurrence_command_outbox previous
            WHERE previous.user_id=c.user_id AND previous.recurrence_id=c.recurrence_id
            AND (previous.created_at<c.created_at OR (previous.created_at=c.created_at AND previous.rowid<c.rowid))
            AND previous.state NOT IN ('APPLIED','DUPLICATE','DISCARDED')
        ) ORDER BY c.created_at ASC,c.rowid ASC LIMIT :limit
    """)
    suspend fun getReady(userId: String, now: Long, limit: Int): List<RecurrenceCommandOutboxEntity>

    @Query("UPDATE recurrence_command_outbox SET state='LEASED', lease_until=:leaseUntil, attempt_count=attempt_count+1, updated_at=:now WHERE user_id=:userId AND operation_id=:operationId AND ((state IN ('PENDING','RETRYABLE') AND (next_attempt_at IS NULL OR next_attempt_at<=:now)) OR (state='LEASED' AND lease_until<=:now))")
    suspend fun lease(userId: String, operationId: String, now: Long, leaseUntil: Long): Int

    @Transaction
    suspend fun claimReady(userId: String, now: Long, limit: Int = 10): List<RecurrenceCommandOutboxEntity> {
        require(limit > 0)
        val leaseUntil = Math.addExact(now, 60_000L)
        return getReady(userId, now, limit).mapNotNull { command ->
            if (lease(userId, command.operationId, now, leaseUntil) == 1) {
                command.copy(state = "LEASED", attemptCount = command.attemptCount + 1, leaseUntil = leaseUntil, updatedAt = now)
            } else null
        }
    }

    @Query("UPDATE recurrence_command_outbox SET state=:state, next_attempt_at=:nextAttemptAt, lease_until=NULL, last_error_code=:errorCode, result_payload=:resultPayload, updated_at=:now WHERE user_id=:userId AND operation_id=:operationId")
    suspend fun updateResult(userId: String, operationId: String, state: String, nextAttemptAt: Long?, errorCode: String?, resultPayload: String?, now: Long): Int

    @Query("""
        SELECT EXISTS(SELECT 1 FROM recurrence_command_outbox c
        WHERE c.user_id=:userId AND c.state IN ('PENDING','LEASED','RETRYABLE')
        AND NOT EXISTS (SELECT 1 FROM recurrence_command_outbox previous
            WHERE previous.user_id=c.user_id AND previous.recurrence_id=c.recurrence_id
            AND (previous.created_at<c.created_at OR (previous.created_at=c.created_at AND previous.rowid<c.rowid))
            AND previous.state IN ('CONFLICT','FAILED')))
    """)
    suspend fun hasOutstanding(userId: String): Boolean

    @Query("SELECT COUNT(*) FROM recurrence_command_outbox WHERE user_id=:userId AND recurrence_id=:recurrenceId AND state IN ('PENDING','LEASED','RETRYABLE')")
    suspend fun countPending(userId: String, recurrenceId: String): Int
}
