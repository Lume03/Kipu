package com.kipu.app.feature.goals.data.sync

import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesRequestDto
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import javax.inject.Inject

class GoalHydration @Inject constructor(private val financialApi: FinancialInstrumentsApi, private val changeFeedApplier: GoalChangeFeedApplier, private val database: KipuDatabase, private val movementSyncScheduler: MovementSyncScheduler, private val sessionCoordinator: SessionCoordinator) {
    suspend fun hydrateFully(userId: String): Boolean {
        com.kipu.app.feature.movements.data.sync.requireSyncOwner(sessionCoordinator, userId)
        database.goalDao().upsertSyncCheckpoint(com.kipu.app.feature.goals.data.local.GoalSyncCheckpointEntity(userId, 0L, System.currentTimeMillis()))
        return pullChanges(userId)
    }
    suspend fun pullChanges(userId: String): Boolean {
        repeat(10000) {
            val checkpoint = database.goalDao().getSyncCheckpoint(userId)?.sequence ?: 0L
            com.kipu.app.feature.movements.data.sync.requireSyncOwner(sessionCoordinator, userId)
            when (val response = financialApi.pullChanges(PullChangesRequestDto(afterSequence = checkpoint), userId)) {
                is FinancialApiResponse.Success -> {
                    com.kipu.app.feature.movements.data.sync.requireSyncOwner(sessionCoordinator, userId)
                    val page = response.data
                    if (page.changes.isEmpty()) return !page.hasMore && page.nextSequence == checkpoint
                    when (val applied = changeFeedApplier.applyPage(userId, page)) {
                        is GoalChangeApplyResult.Applied -> if (!page.hasMore) return true
                        is GoalChangeApplyResult.Conflict -> return false
                        is GoalChangeApplyResult.Rejected -> {
                            if (applied.code == "MISSING_GOAL_EVENT_TRANSACTION") {
                                movementSyncScheduler.scheduleSync(userId)
                            }
                            return false
                        }
                    }
                }
                is FinancialApiResponse.Error, is FinancialApiResponse.NetworkFailure -> return false
            }
        }
        return false
    }

}
