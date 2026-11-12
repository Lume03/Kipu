package com.kipu.app.feature.debts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DebtOutboxDao {

    @Query("UPDATE debt_command_outbox SET state='BLOCKED', last_error_code='BLOCKED_BY_PREVIOUS' WHERE user_id=:userId AND state IN ('PENDING','RETRY','WAITING_FOR_AUTH') AND EXISTS (SELECT 1 FROM debt_command_outbox p WHERE p.user_id=debt_command_outbox.user_id AND p.debt_id=debt_command_outbox.debt_id AND (p.created_at<debt_command_outbox.created_at OR (p.created_at=debt_command_outbox.created_at AND p.rowid<debt_command_outbox.rowid)) AND p.command_type<>'REMOTE_SNAPSHOT' AND p.state IN ('FAILED_PERMANENT','CONFLICT'))")
    suspend fun blockBehindFailures(userId: String)


    @Query("UPDATE debt_command_outbox SET state='WAITING_FOR_AUTH' WHERE user_id=:userId AND state IN ('PENDING','RETRY')")
    suspend fun waitForAuth(userId: String)


    /** Internal feed snapshots are retained for later reconciliation, never dispatched as commands. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRemoteSnapshot(snapshot: DebtOutboxEntity)


    @Query("UPDATE debt_command_outbox SET state='BLOCKED', last_error_code='BLOCKED_BY_PREVIOUS', next_attempt_at=NULL, lease_until=NULL WHERE user_id=:userId AND debt_id=:debtId AND (created_at>:createdAt OR (created_at=:createdAt AND rowid>(SELECT rowid FROM debt_command_outbox WHERE user_id=:userId AND operation_id=:id))) AND state IN ('PENDING','RETRY','WAITING_FOR_AUTH')")
    suspend fun blockSuccessors(userId: String, debtId: String, createdAt: Long, id: String)
    @Query("UPDATE debt_command_outbox SET state='PENDING', last_error_code=NULL WHERE user_id=:userId AND debt_id=:debtId AND state='BLOCKED' AND NOT EXISTS (SELECT 1 FROM debt_command_outbox p WHERE p.user_id=:userId AND p.debt_id=:debtId AND p.state IN ('CONFLICT','FAILED_PERMANENT') AND p.command_type<>'REMOTE_SNAPSHOT')")
    suspend fun unblock(userId: String, debtId: String)

    @Query("SELECT * FROM debt_command_outbox WHERE user_id = :userId AND operation_id = :operationId LIMIT 1")
    suspend fun get(userId: String, operationId: String): DebtOutboxEntity?

    @Query("SELECT c.* FROM debt_command_outbox c WHERE user_id = :userId AND (state IN ('PENDING','RETRY') OR (state='IN_FLIGHT' AND lease_until <= :now)) AND (next_attempt_at IS NULL OR next_attempt_at <= :now) AND (lease_until IS NULL OR lease_until <= :now) AND NOT EXISTS (SELECT 1 FROM debt_command_outbox prior WHERE prior.user_id=c.user_id AND prior.debt_id=c.debt_id AND (prior.created_at<c.created_at OR (prior.created_at=c.created_at AND prior.rowid<c.rowid)) AND prior.command_type<>'REMOTE_SNAPSHOT' AND prior.state NOT IN ('SYNCED','DISCARDED')) ORDER BY created_at ASC, c.rowid ASC LIMIT :limit")
    suspend fun getClaimable(userId: String, now: Long, limit: Int): List<DebtOutboxEntity>

    @Query("UPDATE debt_command_outbox SET state = 'RETRY', next_attempt_at = NULL, lease_until = NULL WHERE user_id = :userId AND state = 'WAITING_FOR_AUTH'")
    suspend fun requeueWaitingForAuth(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(command: DebtOutboxEntity)

    @Query("UPDATE debt_command_outbox SET state = :state, lease_until = :leaseUntil, updated_at = :now WHERE user_id = :userId AND operation_id = :operationId AND state IN ('PENDING','RETRY','IN_FLIGHT')")
    suspend fun setLease(userId: String, operationId: String, state: String, leaseUntil: Long?, now: Long): Int

    @Query("UPDATE debt_command_outbox SET state = :state, attempt_count = attempt_count + :incrementAttempt, next_attempt_at = :nextAttemptAt, lease_until = NULL, last_error_code = :errorCode, updated_at = :now WHERE user_id = :userId AND operation_id = :operationId")
    suspend fun updateResult(userId: String, operationId: String, state: String, incrementAttempt: Int, nextAttemptAt: Long?, errorCode: String?, now: Long): Int

    /**
     * A missing debt RPC answers 404 until its migration is deployed. Older builds parked those
     * commands as permanent failures; put them back in the queue so they replay once it exists.
     */
    @Query("UPDATE debt_command_outbox SET state = 'RETRY', next_attempt_at = :now, lease_until = NULL, updated_at = :now WHERE user_id = :userId AND last_error_code = 'HTTP_404' AND (state = 'FAILED_PERMANENT' OR (state = 'RETRY' AND next_attempt_at > :now))")
    suspend fun requeueMissingEndpointFailures(userId: String, now: Long): Int

    @Query("UPDATE debts SET sync_state = 'PENDING' WHERE user_id = :userId AND sync_state = 'FAILED' AND id IN (SELECT debt_id FROM debt_command_outbox WHERE user_id = :userId AND state = 'RETRY' AND last_error_code = 'HTTP_404')")
    suspend fun markRequeuedDebtsPending(userId: String): Int

    @Transaction
    suspend fun requeueMissingEndpoint(userId: String, now: Long): Int {
        val requeued = requeueMissingEndpointFailures(userId, now)
        if (requeued > 0) markRequeuedDebtsPending(userId)
        return requeued
    }

    @Transaction
    suspend fun claimPending(userId: String, now: Long, limit: Int = 10): List<DebtOutboxEntity> {
        require(limit > 0) { "Debt command claim limit must be positive" }
        val leaseUntil = Math.addExact(now, 60_000L)
        return getClaimable(userId, now, limit).mapNotNull { candidate ->
            if (setLease(userId, candidate.operationId, "IN_FLIGHT", leaseUntil, now) != 1) {
                null
            } else {
                get(userId, candidate.operationId)?.copy(
                    state = "IN_FLIGHT",
                    leaseUntil = leaseUntil,
                    updatedAt = now,
                )
            }
        }
    }

    @Transaction
    suspend fun completeClaimed(
        command: DebtOutboxEntity,
        state: String,
        nextAttemptAt: Long?,
        errorCode: String?,
        now: Long,
    ): Boolean {
        val stored = get(command.userId, command.operationId) ?: return false
        if (stored.state != "IN_FLIGHT" || stored.leaseUntil != command.leaseUntil || stored.updatedAt != command.updatedAt) {
            return false
        }
        val completed = updateResult(
            userId = command.userId,
            operationId = command.operationId,
            state = state,
            incrementAttempt = if (state == "RETRY" || state == "FAILED_PERMANENT") 1 else 0,
            nextAttemptAt = nextAttemptAt,
            errorCode = errorCode,
            now = now,
        ) == 1
        if(completed && com.kipu.app.core.sync.OutboxPolicy.isTerminal(state)) blockSuccessors(command.userId,command.debtId,command.createdAt,command.operationId)
        return completed
    }

    @Query("SELECT COUNT(*) FROM debt_command_outbox WHERE user_id = :userId AND state IN ('PENDING','IN_FLIGHT','RETRY')")
    suspend fun countPending(userId: String): Int

    @Query("SELECT COUNT(*) FROM debt_command_outbox WHERE user_id=:userId AND debt_id=:debtId AND state IN ('PENDING','IN_FLIGHT','RETRY')")
    suspend fun countPendingForDebt(userId: String, debtId: String): Int
}
