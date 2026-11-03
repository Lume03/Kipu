package com.kipu.app.feature.budgets.data.sync

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.feature.budgets.data.local.BudgetDao
import com.kipu.app.feature.budgets.data.remote.BudgetApi
import com.kipu.app.feature.budgets.data.remote.BudgetApiResponse
import com.kipu.app.feature.budgets.data.remote.toLocalEntity
import java.time.ZoneId
import javax.inject.Inject

class BudgetHydration @javax.inject.Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val api: BudgetApi,
    private val zoneId: ZoneId,
) {
    suspend fun hydrateFully(userId: String, strict: Boolean = true): Boolean {
        if (strict) {
            val tombstones = when (val result = api.fetchTombstones(userId)) {
                is BudgetApiResponse.Success -> result.data
                else -> return false
            }
            transactionRunner {
                for (remote in tombstones) {
                    require(remote.userId == userId)
                    val old = budgetDao.getTombstone(userId, remote.budgetId)
                    budgetDao.upsertTombstone(com.kipu.app.feature.budgets.data.local.BudgetTombstoneEntity(
                        userId, remote.budgetId, maxOf(old?.lastRevision ?: 0L, remote.lastRevision),
                        old?.deletedAt ?: com.kipu.app.feature.accounts.data.remote.parseFeedInstant(remote.deletedAt).toEpochMilli(),
                    ))
                    budgetDao.deletePeriods(userId, remote.budgetId)
                    budgetDao.deleteBudget(userId, remote.budgetId)
                    budgetDao.removeSupersededBudgetCommands(userId, remote.budgetId)
                }
            }
        }
        val budgets = when (val result = api.fetchBudgets(userId)) {
            is BudgetApiResponse.Success -> result.data
            is BudgetApiResponse.Error -> return false
            is BudgetApiResponse.NetworkFailure -> return false
        }
        val periods = when (val result = api.fetchPeriods(userId)) {
            is BudgetApiResponse.Success -> result.data
            is BudgetApiResponse.Error -> return false
            is BudgetApiResponse.NetworkFailure -> return false
        }

        val blockedBudgetIds = budgetDao.allOutbox(userId)
            .filter { it.state == "PENDING" || it.state == "FAILED" }
            .mapTo(mutableSetOf()) { it.aggregateId }
        transactionRunner {
            for (remote in budgets) {
                if (remote.userId != userId || remote.id in blockedBudgetIds || budgetDao.getTombstone(userId, remote.id) != null) continue
                if (remote.periodType == "CUSTOM" && (remote.rangeStartOn == null || remote.rangeEndOn == null)) {
                    SecureLog.w("SyncBudgetCommandsWorker", "Skipping a legacy custom budget without date bounds")
                    if (strict) throw IllegalArgumentException("Invalid remote budget date bounds") else continue
                }
                val local = budgetDao.getBudget(userId, remote.id)
                if (local == null || remote.revision >= local.revision) {
                    budgetDao.upsertBudget(remote.toLocalEntity(zoneId))
                }
            }

            for (remote in periods) {
                if (remote.userId != userId || remote.budgetId in blockedBudgetIds || budgetDao.getTombstone(userId, remote.budgetId) != null) continue
                if (budgetDao.getBudget(userId, remote.budgetId) == null) {
                    if (strict) throw IllegalArgumentException("Missing remote budget parent") else continue
                }
                // The remote snapshot of the period scope is authoritative.
                budgetDao.upsertPeriod(remote.toLocalEntity())
            }
        }
        return true
    }

}
