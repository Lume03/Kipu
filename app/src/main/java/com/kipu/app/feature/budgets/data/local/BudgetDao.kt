package com.kipu.app.feature.budgets.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @androidx.room.Transaction
    suspend fun complete(command: BudgetSyncOutboxEntity, state: String, error: String?, now: Long) {
        val stored=allOutbox(command.userId).firstOrNull { it.operationId==command.operationId } ?: return
        if(stored.state!="IN_FLIGHT" || stored.updatedAt!=command.updatedAt) return
        updateOutbox(command.copy(state=state,errorCode=error,attemptCount=command.attemptCount+1,updatedAt=now))
        if(com.kipu.app.core.sync.OutboxPolicy.isTerminal(state)) blockSuccessors(command.userId,command.aggregateId,command.sequence)
    }


    @Query("UPDATE budget_sync_outbox SET state='BLOCKED', error_code='BLOCKED_BY_PREVIOUS' WHERE user_id=:userId AND state IN ('PENDING','ERROR','WAITING_FOR_AUTH') AND EXISTS (SELECT 1 FROM budget_sync_outbox p WHERE p.user_id=budget_sync_outbox.user_id AND p.aggregate_id=budget_sync_outbox.aggregate_id AND p.sequence<budget_sync_outbox.sequence AND p.state IN ('FAILED','FAILED_PERMANENT','CONFLICT'))")
    suspend fun blockBehindFailures(userId: String)


    @Query("UPDATE budget_sync_outbox SET state='WAITING_FOR_AUTH' WHERE user_id=:userId AND state IN ('PENDING','ERROR')")
    suspend fun waitForAuth(userId: String)


    @Query("SELECT * FROM budget_sync_outbox WHERE user_id=:userId AND state='PENDING' ORDER BY sequence")
    suspend fun pendingOutbox(userId: String): List<BudgetSyncOutboxEntity>


    @androidx.room.Transaction
    suspend fun claim(userId: String, now: Long): BudgetSyncOutboxEntity? {
        val cmd = readyOutbox(userId,now).firstOrNull() ?: return null
        val leased = cmd.copy(state="IN_FLIGHT",updatedAt=now)
        updateOutbox(leased)
        return leased
    }
    @Query("UPDATE budget_sync_outbox SET state='BLOCKED', error_code='BLOCKED_BY_PREVIOUS' WHERE user_id=:userId AND aggregate_id=:aggregateId AND sequence>:sequence AND state IN ('PENDING','ERROR','WAITING_FOR_AUTH')")
    suspend fun blockSuccessors(userId: String, aggregateId: String, sequence: Long)
    @Query("UPDATE budget_sync_outbox SET state='PENDING', error_code=NULL WHERE user_id=:userId AND aggregate_id=:aggregateId AND state='BLOCKED' AND NOT EXISTS (SELECT 1 FROM budget_sync_outbox p WHERE p.user_id=:userId AND p.aggregate_id=:aggregateId AND p.state IN ('FAILED','FAILED_PERMANENT','CONFLICT'))")
    suspend fun unblock(userId: String, aggregateId: String)
    @Query("UPDATE budget_sync_outbox SET state='PENDING' WHERE user_id=:userId AND state='WAITING_FOR_AUTH'")
    suspend fun resumeAuth(userId: String)

    @Query("SELECT * FROM budget_sync_outbox WHERE user_id=:userId AND state='FAILED' ORDER BY sequence ASC")
    fun observeFailedCommands(userId: String): Flow<List<BudgetSyncOutboxEntity>>
    @Query("SELECT * FROM budgets WHERE user_id = :userId ORDER BY created_on DESC, id ASC")
    fun observeBudgets(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND id = :budgetId LIMIT 1")
    suspend fun getBudget(userId: String, budgetId: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE user_id = :userId ORDER BY created_on DESC, id ASC")
    suspend fun listBudgets(userId: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBudgetIfAbsent(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudget(budget: BudgetEntity): Int

    @Query("DELETE FROM budgets WHERE user_id = :userId AND id = :budgetId")
    suspend fun deleteBudget(userId: String, budgetId: String): Int

    @Query("SELECT * FROM budget_periods WHERE user_id = :userId AND budget_id = :budgetId ORDER BY starts_on ASC, ends_on ASC")
    fun observePeriods(userId: String, budgetId: String): Flow<List<BudgetPeriodEntity>>

    @Query("SELECT * FROM budget_periods WHERE user_id = :userId AND budget_id = :budgetId ORDER BY starts_on ASC, ends_on ASC")
    suspend fun listPeriods(userId: String, budgetId: String): List<BudgetPeriodEntity>

    @Query("SELECT * FROM budget_periods WHERE user_id = :userId AND id = :periodId LIMIT 1")
    suspend fun getPeriod(userId: String, periodId: String): BudgetPeriodEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeriodIfAbsent(period: BudgetPeriodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeriod(period: BudgetPeriodEntity)

    @Query("UPDATE budget_periods SET limit_minor = :limitMinor WHERE user_id = :userId AND id = :periodId")
    suspend fun updatePeriodLimit(userId: String, periodId: String, limitMinor: Long): Int

    @Query("DELETE FROM budget_periods WHERE user_id = :userId AND budget_id = :budgetId")
    suspend fun deletePeriods(userId: String, budgetId: String): Int

    @Query("SELECT * FROM budget_tombstones WHERE user_id = :userId AND budget_id = :budgetId LIMIT 1")
    suspend fun getTombstone(userId: String, budgetId: String): BudgetTombstoneEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTombstoneIfAbsent(tombstone: BudgetTombstoneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: BudgetTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutboxIfAbsent(command: BudgetSyncOutboxEntity): Long

    @Query("SELECT c.* FROM budget_sync_outbox c WHERE c.user_id=:userId AND (c.state='PENDING' OR (c.state='ERROR' AND c.updated_at<=:now-30000) OR (c.state='IN_FLIGHT' AND c.updated_at<=:now-60000)) AND NOT EXISTS (SELECT 1 FROM budget_sync_outbox p WHERE p.user_id=c.user_id AND p.aggregate_id=c.aggregate_id AND p.sequence<c.sequence AND p.state NOT IN ('COMPLETED','DISCARDED')) ORDER BY c.sequence ASC LIMIT 1")
    suspend fun readyOutbox(userId: String, now: Long): List<BudgetSyncOutboxEntity>

    @Query("SELECT * FROM budget_sync_outbox WHERE user_id = :userId")
    suspend fun allOutbox(userId: String): List<BudgetSyncOutboxEntity>

    @Update
    suspend fun updateOutbox(command: BudgetSyncOutboxEntity): Int

    @Query("DELETE FROM budget_sync_outbox WHERE user_id = :userId AND aggregate_id = :budgetId AND command_type <> 'DELETE_BUDGET'")
    suspend fun removeSupersededBudgetCommands(userId: String, budgetId: String): Int

    @Query("SELECT COUNT(*) FROM budget_sync_outbox WHERE user_id = :userId AND state = 'PENDING'")
    suspend fun pendingOutboxCount(userId: String): Int
}

@Dao
interface BudgetAlertLedgerDao {
    @Query("SELECT threshold_bps FROM budget_alert_ledger WHERE user_id = :userId AND budget_id = :budgetId AND period_id = :periodId")
    suspend fun emittedThresholds(userId: String, budgetId: String, periodId: String): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: BudgetAlertLedgerEntity): Long

    @Query("DELETE FROM budget_alert_ledger WHERE user_id = :userId AND budget_id = :budgetId")
    suspend fun deleteForBudget(userId: String, budgetId: String): Int

    @Query("SELECT COUNT(*) FROM budget_alert_ledger WHERE user_id = :userId AND budget_id = :budgetId")
    suspend fun countForBudget(userId: String, budgetId: String): Int
}
