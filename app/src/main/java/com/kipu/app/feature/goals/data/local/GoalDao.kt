package com.kipu.app.feature.goals.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val BACKING_ACCOUNT_POSITION_QUERY = """
    SELECT a.id AS account_id,
           a.alias AS account_name,
           a.currency AS currency_code,
           COALESCE(b.confirmed_balance_minor, 0) AS confirmed_balance_minor,
           COALESCE(r.reserved_minor, 0) AS reserved_minor
    FROM accounts a
    LEFT JOIN (
        SELECT le.user_id, le.account_id, SUM(le.signed_amount_minor) AS confirmed_balance_minor
        FROM ledger_entries le
        INNER JOIN transactions t ON t.user_id = le.user_id AND t.id = le.transaction_id
        WHERE t.status IN ('ACTIVE', 'CONFIRMED', 'REVISED')
        GROUP BY le.user_id, le.account_id
    ) b ON b.user_id = a.user_id AND b.account_id = a.id
    LEFT JOIN (
        SELECT e.user_id, e.backing_account_id AS account_id, SUM(e.delta_minor) AS reserved_minor
        FROM goal_events e
        LEFT JOIN transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE e.backing_account_id IS NOT NULL
          AND (e.transaction_id IS NULL OR t.status IN ('ACTIVE', 'CONFIRMED', 'REVISED'))
        GROUP BY e.user_id, e.backing_account_id
    ) r ON r.user_id = a.user_id AND r.account_id = a.id
    WHERE a.user_id = :userId
      AND a.is_archived = 0
      AND a.linked_account_id IS NULL
      AND a.type NOT IN ('GOALS_VIRTUAL', 'CREDIT_LIABILITY')
"""

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE user_id = :userId ORDER BY updated_at DESC, id ASC")
    fun observeGoals(userId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE user_id = :userId AND id = :goalId LIMIT 1")
    fun observeGoal(userId: String, goalId: String): Flow<GoalEntity?>

    @Query("SELECT * FROM goals WHERE user_id = :userId AND id = :goalId LIMIT 1")
    suspend fun getGoal(userId: String, goalId: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE user_id = :userId AND id = :goalId LIMIT 1")
    suspend fun getGoalIncludingArchived(userId: String, goalId: String): GoalEntity?

    @Query("SELECT * FROM goal_sync_checkpoints WHERE user_id = :userId LIMIT 1")
    suspend fun getSyncCheckpoint(userId: String): GoalSyncCheckpointEntity?

    @Query(
        """
        SELECT e.*, t.status AS linked_transaction_status
        FROM goal_events e
        LEFT JOIN transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE e.user_id = :userId AND e.goal_id = :goalId
        ORDER BY e.occurred_at ASC, e.id ASC
        """,
    )
    fun observeEvents(userId: String, goalId: String): Flow<List<GoalEventWithTransactionStatusRow>>

    @Query(
        """
        SELECT e.*, t.status AS linked_transaction_status
        FROM goal_events e
        LEFT JOIN transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE e.user_id = :userId AND e.goal_id = :goalId
        ORDER BY e.occurred_at ASC, e.id ASC
        """,
    )
    suspend fun getEvents(userId: String, goalId: String): List<GoalEventWithTransactionStatusRow>

    @Query("SELECT * FROM goal_events WHERE user_id = :userId AND id = :eventId LIMIT 1")
    suspend fun getEvent(userId: String, eventId: String): GoalEventEntity?

    @Query(
        """
        SELECT COALESCE(SUM(e.delta_minor), 0)
        FROM goal_events e
        LEFT JOIN transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE e.user_id = :userId AND e.goal_id = :goalId
          AND (e.transaction_id IS NULL OR t.status IN ('ACTIVE', 'CONFIRMED', 'REVISED'))
        """,
    )
    suspend fun getReservedMinor(userId: String, goalId: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(e.delta_minor), 0)
        FROM goal_events e
        LEFT JOIN transactions t ON t.user_id = e.user_id AND t.id = e.transaction_id
        WHERE e.user_id = :userId AND e.backing_account_id = :accountId
          AND (e.transaction_id IS NULL OR t.status IN ('ACTIVE', 'CONFIRMED', 'REVISED'))
        """,
    )
    suspend fun getReservedForAccountMinor(userId: String, accountId: String): Long

    @Query("$BACKING_ACCOUNT_POSITION_QUERY ORDER BY a.created_at DESC, a.id ASC")
    fun observeBackingAccounts(userId: String): Flow<List<GoalBackingAccountRow>>

    @Query("$BACKING_ACCOUNT_POSITION_QUERY AND a.id = :accountId LIMIT 1")
    suspend fun getBackingAccountPosition(userId: String, accountId: String): GoalBackingAccountRow?

    @Query(
        """
        SELECT COUNT(*) FROM goals
        WHERE user_id = :userId AND status = 'ACTIVE'
        """,
    )
    suspend fun countOperationalGoals(userId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM goal_events
        WHERE user_id = :userId AND goal_id = :goalId
        """,
    )
    suspend fun countEvents(userId: String, goalId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM goal_events
        WHERE user_id = :userId AND goal_id = :goalId AND transaction_id IS NOT NULL
        """,
    )
    suspend fun countLinkedTransfers(userId: String, goalId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: GoalEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: GoalEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvents(events: List<GoalEventEntity>)

    @Update
    suspend fun updateGoal(goal: GoalEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncCheckpoint(checkpoint: GoalSyncCheckpointEntity)

    @Query("UPDATE goals SET sync_state = :syncState WHERE user_id = :userId AND id = :goalId")
    suspend fun updateSyncState(userId: String, goalId: String, syncState: String): Int

    @Query("UPDATE goals SET sync_state = :syncState, revision = MAX(revision, :revision) WHERE user_id = :userId AND id = :goalId")
    suspend fun updateRemoteRevision(userId: String, goalId: String, revision: Long, syncState: String): Int

    @Query("UPDATE goals SET status = :status, sync_state = :syncState, revision = MAX(revision, :revision) WHERE user_id = :userId AND id = :goalId")
    suspend fun updateRemoteGoalResult(userId: String, goalId: String, status: String, revision: Long, syncState: String): Int

    @Query("DELETE FROM goals WHERE user_id = :userId AND id = :goalId AND NOT EXISTS (SELECT 1 FROM goal_events e WHERE e.user_id = :userId AND e.goal_id = :goalId) AND NOT EXISTS (SELECT 1 FROM goal_command_outbox o WHERE o.user_id = :userId AND o.goal_id = :goalId)")
    suspend fun deleteEmptyGoal(userId: String, goalId: String): Int
}
