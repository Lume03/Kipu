package com.kipu.app.feature.debts.data.sync

import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesRequestDto
import com.kipu.app.feature.debts.data.local.DebtDao
import javax.inject.Inject

class DebtHydration @Inject constructor(private val debtDao: DebtDao, private val financialApi: FinancialInstrumentsApi, private val changeFeedApplier: DebtChangeFeedApplier, private val sessionCoordinator: SessionCoordinator) {
    suspend fun hydrateFully(userId: String): Boolean {
        com.kipu.app.feature.movements.data.sync.requireSyncOwner(sessionCoordinator, userId)
        if (debtDao.countUnresolvedCommands(userId) > 0) return false
        debtDao.upsertSyncCheckpoint(com.kipu.app.feature.debts.data.local.DebtSyncCheckpointEntity(userId, 0L, System.currentTimeMillis()))
        return pullChanges(userId, full = true)
    }
    suspend fun pullChanges(userId: String, full: Boolean = false): Boolean {
        var checkpoint = debtDao.getSyncCheckpoint(userId)?.sequence ?: 0L
        repeat(10000) {
            com.kipu.app.feature.movements.data.sync.requireSyncOwner(sessionCoordinator, userId)
            when (val response = financialApi.pullChanges(PullChangesRequestDto(afterSequence = checkpoint), userId)) {
                is FinancialApiResponse.Success -> {
                    com.kipu.app.feature.movements.data.sync.requireSyncOwner(sessionCoordinator, userId)
                    val page = response.data
                    when (val applied = changeFeedApplier.applyPage(userId, page)) {
                        is DebtChangeApplyResult.Applied -> checkpoint = applied.nextSequence
                        is DebtChangeApplyResult.Conflict -> {
                            SecureLog.w("DebtHydration", "Debt change feed stopped on a stale revision")
                            return !full
                        }
                        is DebtChangeApplyResult.Rejected -> {
                            SecureLog.w("DebtHydration", "Debt change page rejected: ${applied.code}")
                            return false
                        }
                    }
                    if (page.changes.isEmpty()) return !page.hasMore
                    if (!page.hasMore) return true
                }
                is FinancialApiResponse.Error -> return !full && response.statusCode in 400..499 && response.statusCode != 401
                is FinancialApiResponse.NetworkFailure -> return false
            }
        }
        return false
    }

}
