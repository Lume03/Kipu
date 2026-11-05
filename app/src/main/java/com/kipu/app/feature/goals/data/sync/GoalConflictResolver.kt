package com.kipu.app.feature.goals.data.sync

import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.goals.data.remote.GoalsApi
import com.kipu.app.feature.goals.data.remote.GoalsApiResponse
import com.kipu.app.feature.movements.data.sync.requireSyncOwner
import javax.inject.Inject

class GoalConflictResolver @Inject constructor(
    private val api: GoalsApi,
    private val applier: GoalChangeFeedApplier,
    private val session: SessionCoordinator,
    private val scheduler: GoalsSyncScheduler,
    private val database: com.kipu.app.core.database.KipuDatabase,
) {
    suspend fun retry(userId: String, goalId: String) {
        requireSyncOwner(session, userId)
        // Stable identities let the server return an existing receipt after an interrupted response.
        database.goalOutboxDao().retryConflicts(userId, goalId, System.currentTimeMillis())
        scheduler.schedule(userId)
    }
    suspend fun discard(userId: String, goalId: String, revision: Long): GoalChangeApplyResult {
        requireSyncOwner(session, userId)
        val response = api.fetchGoal(userId, goalId)
        requireSyncOwner(session, userId)
        return when (response) {
            is GoalsApiResponse.Success -> applier.discardMetadataConflict(userId, goalId, revision, response.data).also {
                if (it is GoalChangeApplyResult.Applied) scheduler.schedule(userId)
            }
            else -> GoalChangeApplyResult.Rejected("CONNECTION_REQUIRED")
        }
    }
}
