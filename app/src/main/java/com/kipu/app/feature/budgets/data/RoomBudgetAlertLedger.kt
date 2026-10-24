package com.kipu.app.feature.budgets.data

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.data.local.BudgetAlertLedgerDao
import com.kipu.app.feature.budgets.data.local.BudgetAlertLedgerEntity
import com.kipu.app.feature.budgets.domain.BudgetAlertKey
import com.kipu.app.feature.budgets.domain.BudgetAlertLedger
import java.time.Clock
import javax.inject.Inject

class RoomBudgetAlertLedger @Inject constructor(
    private val dao: BudgetAlertLedgerDao,
    private val sessionCoordinator: SessionCoordinator,
    private val clock: Clock,
) : BudgetAlertLedger {
    override suspend fun emittedThresholds(ownerId: String, budgetId: String, periodId: String): Set<Int> {
        if (!isCurrentOwner(ownerId)) return emptySet()
        return dao.emittedThresholds(ownerId, budgetId, periodId).toSet()
    }

    override suspend fun tryRecord(key: BudgetAlertKey): Boolean {
        if (!isCurrentOwner(key.ownerId)) return false
        val inserted = dao.insertIfAbsent(
            BudgetAlertLedgerEntity(
                userId = key.ownerId,
                budgetId = key.budgetId,
                periodId = key.periodId,
                thresholdBps = key.thresholdBps,
                dedupeKey = key.dedupeKey,
                emittedAt = clock.millis(),
            ),
        )
        return inserted >= 0L
    }

    private fun isCurrentOwner(ownerId: String): Boolean =
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId == ownerId
}
