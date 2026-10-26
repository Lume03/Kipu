package com.kipu.app.feature.goals.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface GoalOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(command: GoalOutboxEntity)

    @Update
    suspend fun update(command: GoalOutboxEntity): Int

    @Query("SELECT * FROM goal_command_outbox WHERE user_id = :userId AND operation_id = :operationId LIMIT 1")
    suspend fun get(userId: String, operationId: String): GoalOutboxEntity?

    @Query("SELECT * FROM goal_command_outbox WHERE user_id = :userId AND state IN ('PENDING','LEASED','RETRYABLE')")
    suspend fun getPendingForUser(userId: String): List<GoalOutboxEntity>

    @Query(
        """
        SELECT c.* FROM goal_command_outbox c
        WHERE c.user_id = :userId
          AND NOT EXISTS (SELECT 1 FROM goal_command_outbox prior
            WHERE prior.user_id=c.user_id AND prior.goal_id=c.goal_id
              AND (prior.created_at<c.created_at OR (prior.created_at=c.created_at AND COALESCE(prior.expected_revision,-1)<COALESCE(c.expected_revision,-1)))
              AND prior.state NOT IN ('APPLIED','DUPLICATE'))
          AND (
              (state IN ('PENDING', 'RETRYABLE') AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
              OR (state = 'LEASED' AND lease_until <= :now)
          )
        ORDER BY created_at ASC
        LIMIT :limit
        """,
    )
    suspend fun getReady(userId: String, now: Long, limit: Int): List<GoalOutboxEntity>

    @Query("UPDATE goal_command_outbox SET state='LEASED', lease_until=:leaseUntil, attempt_count=attempt_count+1, updated_at=:now WHERE user_id=:userId AND operation_id=:operationId AND (state IN ('PENDING','RETRYABLE') OR (state='LEASED' AND lease_until <= :now))")
    suspend fun lease(userId: String, operationId: String, now: Long, leaseUntil: Long): Int

    @Transaction
    suspend fun claimPending(userId: String, now: Long, limit: Int = 10): List<GoalOutboxEntity> {
        require(limit > 0) { "Claim limit must be positive" }
        val leaseUntil = Math.addExact(now, 60_000L)
        val candidates = getReady(userId, now, limit)
        return candidates.mapNotNull { command ->
            if (lease(userId, command.operationId, now, leaseUntil) == 1) {
                command.copy(
                    state = "LEASED",
                    attemptCount = command.attemptCount + 1,
                    leaseUntil = leaseUntil,
                    updatedAt = now,
                )
            } else {
                null
            }
        }
    }

    @Query("UPDATE goal_command_outbox SET state=:state, next_attempt_at=:nextAttemptAt, lease_until=NULL, last_error_code=:errorCode, result_payload=:resultPayload, updated_at=:now WHERE user_id=:userId AND operation_id=:operationId")
    suspend fun updateResult(
        userId: String,
        operationId: String,
        state: String,
        nextAttemptAt: Long?,
        errorCode: String?,
        resultPayload: String?,
        now: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM goal_command_outbox WHERE user_id=:userId AND goal_id=:goalId AND state IN ('PENDING','LEASED','RETRYABLE')")
    suspend fun countPending(userId: String, goalId: String): Int

    @Query("DELETE FROM goal_command_outbox WHERE user_id=:userId AND operation_id=:operationId")
    suspend fun delete(userId: String, operationId: String): Int

    @Query("DELETE FROM goal_command_outbox WHERE user_id=:userId AND operation_id=:operationId AND state IN ('APPLIED','DUPLICATE')")
    suspend fun deleteApplied(userId: String, operationId: String): Int
}
