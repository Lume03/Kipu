package com.kipu.app.feature.budgets.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
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

    @Update
    suspend fun updatePeriod(period: BudgetPeriodEntity): Int

    @Query("UPDATE budget_periods SET limit_minor = :limitMinor WHERE user_id = :userId AND id = :periodId")
    suspend fun updatePeriodLimit(userId: String, periodId: String, limitMinor: Long): Int

    @Query("DELETE FROM budget_periods WHERE user_id = :userId AND budget_id = :budgetId")
    suspend fun deletePeriods(userId: String, budgetId: String): Int

    @Query("SELECT * FROM budget_tombstones WHERE user_id = :userId AND budget_id = :budgetId LIMIT 1")
    suspend fun getTombstone(userId: String, budgetId: String): BudgetTombstoneEntity?

    @Query("SELECT budget_id FROM budget_tombstones WHERE user_id = :userId")
    suspend fun tombstonedBudgetIds(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTombstoneIfAbsent(tombstone: BudgetTombstoneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: BudgetTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutboxIfAbsent(command: BudgetSyncOutboxEntity): Long

    @Query("SELECT * FROM budget_sync_outbox WHERE user_id = :userId AND state = 'PENDING' ORDER BY sequence ASC")
    suspend fun pendingOutbox(userId: String): List<BudgetSyncOutboxEntity>

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
