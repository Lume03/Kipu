package com.kipu.app.feature.budgets.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.data.local.BudgetAlertLedgerDao
import com.kipu.app.feature.budgets.data.local.BudgetDao
import com.kipu.app.feature.budgets.data.local.BudgetEntity
import com.kipu.app.feature.budgets.data.local.BudgetPeriodEntity
import com.kipu.app.feature.budgets.data.local.BudgetSyncOutboxEntity
import com.kipu.app.feature.budgets.data.local.BudgetTombstoneEntity
import com.kipu.app.feature.budgets.data.local.toDomain
import com.kipu.app.feature.budgets.data.local.toEntity
import com.kipu.app.feature.budgets.data.remote.BudgetSyncPayload
import com.kipu.app.feature.budgets.data.remote.toRemoteDto
import com.kipu.app.feature.budgets.data.sync.BudgetSyncScheduler
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.budgets.domain.BudgetWriteResult
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineFirstBudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val alertLedgerDao: BudgetAlertLedgerDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val sessionCoordinator: SessionCoordinator,
    private val syncScheduler: BudgetSyncScheduler,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : BudgetRepository {

    override fun observeBudgets(ownerId: String): Flow<List<Budget>> =
        if (!isCurrentOwner(ownerId)) emptyFlow() else budgetDao.observeBudgets(ownerId).map { rows -> rows.map(BudgetEntity::toDomain) }

    override fun observePeriods(ownerId: String, budgetId: String): Flow<List<BudgetPeriod>> =
        if (!isCurrentOwner(ownerId)) emptyFlow() else {
            budgetDao.observePeriods(ownerId, budgetId).map { rows -> rows.map(BudgetPeriodEntity::toDomain) }
        }

    override suspend fun getBudget(ownerId: String, budgetId: String): Budget? {
        if (!isCurrentOwner(ownerId)) return null
        return budgetDao.getBudget(ownerId, budgetId)?.toDomain()
    }

    override suspend fun listBudgets(ownerId: String): List<Budget> {
        if (!isCurrentOwner(ownerId)) return emptyList()
        return budgetDao.listBudgets(ownerId).map(BudgetEntity::toDomain)
    }

    override suspend fun listPeriods(ownerId: String, budgetId: String): List<BudgetPeriod> {
        if (!isCurrentOwner(ownerId)) return emptyList()
        return budgetDao.listPeriods(ownerId, budgetId).map(BudgetPeriodEntity::toDomain)
    }

    override suspend fun create(budget: Budget, firstPeriod: BudgetPeriod): BudgetWriteResult {
        if (!isCurrentOwner(budget.ownerId) || firstPeriod.ownerId != budget.ownerId || firstPeriod.budgetId != budget.id) {
            return BudgetWriteResult.NOT_FOUND
        }
        val now = clock.millis()
        val result = transactionRunner {
            if (budgetDao.getTombstone(budget.ownerId, budget.id) != null) return@transactionRunner BudgetWriteResult.ALREADY_EXISTS
            val row = budget.toEntity(existing = null, now = now)
            if (budgetDao.insertBudgetIfAbsent(row) < 0L) return@transactionRunner BudgetWriteResult.ALREADY_EXISTS
            val period = firstPeriod.toEntity(now)
            check(budgetDao.insertPeriodIfAbsent(period) >= 0L) { "Budget period already exists without its budget" }
            enqueueBudget(row, expectedRevision = null, now = now)
            enqueuePeriod(period, now)
            BudgetWriteResult.OK
        }
        if (result == BudgetWriteResult.OK) scheduleSync(budget.ownerId)
        return result
    }

    override suspend fun update(budget: Budget, expectedRevision: Long): BudgetWriteResult {
        if (!isCurrentOwner(budget.ownerId)) return BudgetWriteResult.NOT_FOUND
        val now = clock.millis()
        val result = transactionRunner {
            if (budgetDao.getTombstone(budget.ownerId, budget.id) != null) return@transactionRunner BudgetWriteResult.NOT_FOUND
            val existing = budgetDao.getBudget(budget.ownerId, budget.id) ?: return@transactionRunner BudgetWriteResult.NOT_FOUND
            if (existing.revision != expectedRevision) return@transactionRunner BudgetWriteResult.REVISION_CONFLICT
            if (budget.revision <= expectedRevision) return@transactionRunner BudgetWriteResult.REVISION_CONFLICT

            val updated = budget.toEntity(existing, now)
            check(budgetDao.updateBudget(updated) == 1) { "Budget disappeared during its local update" }
            enqueueBudget(updated, expectedRevision, now)

            // Domain edits change the active period limit as well as the budget definition.
            // Persist both in this transaction so returning success cannot leave mismatched snapshots.
            if (existing.limitMinor != updated.limitMinor) {
                val today = LocalDate.now(clock.withZone(zoneId))
                val activePeriod = budgetDao.listPeriods(budget.ownerId, budget.id)
                    .firstOrNull { today >= LocalDate.parse(it.startsOn) && today < LocalDate.parse(it.endsOn) }
                if (activePeriod != null) {
                    budgetDao.updatePeriodLimit(budget.ownerId, activePeriod.id, updated.limitMinor)
                    val refreshed = activePeriod.copy(limitMinor = updated.limitMinor)
                    enqueuePeriod(refreshed, now)
                }
            }
            BudgetWriteResult.OK
        }
        if (result == BudgetWriteResult.OK) scheduleSync(budget.ownerId)
        return result
    }

    override suspend fun applyRenewal(
        ownerId: String,
        budget: Budget?,
        expectedRevision: Long,
        newPeriods: List<BudgetPeriod>,
    ): BudgetWriteResult {
        if (!isCurrentOwner(ownerId) || budget?.ownerId?.let { it != ownerId } == true ||
            newPeriods.any { it.ownerId != ownerId || (budget != null && it.budgetId != budget.id) }
        ) return BudgetWriteResult.NOT_FOUND
        val budgetId = budget?.id ?: newPeriods.firstOrNull()?.budgetId
            ?: return BudgetWriteResult.OK
        val now = clock.millis()
        val result = transactionRunner {
            if (budgetDao.getTombstone(ownerId, budgetId) != null) return@transactionRunner BudgetWriteResult.NOT_FOUND
            val existing = budgetDao.getBudget(ownerId, budgetId) ?: return@transactionRunner BudgetWriteResult.NOT_FOUND
            if (budget != null) {
                when {
                    existing.revision == expectedRevision && budget.revision > expectedRevision -> {
                        val updated = budget.toEntity(existing, now)
                        check(budgetDao.updateBudget(updated) == 1) { "Budget disappeared during renewal" }
                        enqueueBudget(updated, expectedRevision, now)
                    }
                    existing.revision == budget.revision && existing.toDomain() == budget -> Unit
                    else -> return@transactionRunner BudgetWriteResult.REVISION_CONFLICT
                }
            }
            for (period in newPeriods) {
                if (period.budgetId != budgetId) return@transactionRunner BudgetWriteResult.NOT_FOUND
                val row = period.toEntity(now)
                if (budgetDao.insertPeriodIfAbsent(row) >= 0L) enqueuePeriod(row, now)
            }
            BudgetWriteResult.OK
        }
        if (result == BudgetWriteResult.OK && (budget != null || newPeriods.isNotEmpty())) scheduleSync(ownerId)
        return result
    }

    override suspend fun updatePeriodLimit(ownerId: String, periodId: String, limitMinor: Long): BudgetWriteResult {
        if (!isCurrentOwner(ownerId)) return BudgetWriteResult.NOT_FOUND
        val now = clock.millis()
        val result = transactionRunner {
            val period = budgetDao.getPeriod(ownerId, periodId) ?: return@transactionRunner BudgetWriteResult.NOT_FOUND
            if (period.limitMinor == limitMinor) return@transactionRunner BudgetWriteResult.OK
            if (budgetDao.getTombstone(ownerId, period.budgetId) != null) return@transactionRunner BudgetWriteResult.NOT_FOUND
            budgetDao.updatePeriodLimit(ownerId, periodId, limitMinor)
            enqueuePeriod(period.copy(limitMinor = limitMinor), now)
            BudgetWriteResult.OK
        }
        if (result == BudgetWriteResult.OK) scheduleSync(ownerId)
        return result
    }

    override suspend fun delete(ownerId: String, budgetId: String): BudgetWriteResult {
        if (!isCurrentOwner(ownerId)) return BudgetWriteResult.NOT_FOUND
        val now = clock.millis()
        val result = transactionRunner {
            val existing = budgetDao.getBudget(ownerId, budgetId)
            val tombstone = budgetDao.getTombstone(ownerId, budgetId)
            if (existing == null && tombstone == null) return@transactionRunner BudgetWriteResult.NOT_FOUND
            val lastRevision = existing?.revision ?: tombstone!!.lastRevision
            budgetDao.insertTombstoneIfAbsent(
                BudgetTombstoneEntity(ownerId, budgetId, lastRevision, tombstone?.deletedAt ?: now),
            )
            budgetDao.deletePeriods(ownerId, budgetId)
            alertLedgerDao.deleteForBudget(ownerId, budgetId)
            budgetDao.deleteBudget(ownerId, budgetId)
            budgetDao.removeSupersededBudgetCommands(ownerId, budgetId)
            val payload = BudgetSyncPayload(budgetId = budgetId, lastRevision = lastRevision)
            budgetDao.insertOutboxIfAbsent(
                BudgetSyncOutboxEntity(
                    operationId = "budget-delete:$budgetId",
                    userId = ownerId,
                    commandType = DELETE_BUDGET,
                    aggregateId = budgetId,
                    expectedRevision = lastRevision,
                    payloadJson = json.encodeToString(payload),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            BudgetWriteResult.OK
        }
        if (result == BudgetWriteResult.OK) scheduleSync(ownerId)
        return result
    }

    private suspend fun enqueueBudget(entity: BudgetEntity, expectedRevision: Long?, now: Long) {
        val payload = BudgetSyncPayload(budget = entity.toRemoteDto())
        budgetDao.insertOutboxIfAbsent(
            BudgetSyncOutboxEntity(
                operationId = "budget-upsert:${entity.id}:${entity.revision}",
                userId = entity.userId,
                commandType = UPSERT_BUDGET,
                aggregateId = entity.id,
                expectedRevision = expectedRevision,
                payloadJson = json.encodeToString(payload),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun enqueuePeriod(entity: BudgetPeriodEntity, now: Long) {
        val payload = BudgetSyncPayload(period = entity.toRemoteDto())
        budgetDao.insertOutboxIfAbsent(
            BudgetSyncOutboxEntity(
                operationId = "budget-period:${entity.userId}:${entity.id}:${UUID.randomUUID()}",
                userId = entity.userId,
                commandType = UPSERT_PERIOD,
                aggregateId = entity.budgetId,
                payloadJson = json.encodeToString(payload),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun isCurrentOwner(ownerId: String): Boolean =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId == ownerId

    private fun scheduleSync(ownerId: String) {
        runCatching { syncScheduler.scheduleSync(ownerId) }
    }

    companion object {
        const val UPSERT_BUDGET = "UPSERT_BUDGET"
        const val UPSERT_PERIOD = "UPSERT_PERIOD"
        const val DELETE_BUDGET = "DELETE_BUDGET"
        private val json = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = true }
    }
}
