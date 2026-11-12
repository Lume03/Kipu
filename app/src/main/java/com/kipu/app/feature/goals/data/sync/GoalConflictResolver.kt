package com.kipu.app.feature.goals.data.sync

import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.goals.data.remote.GoalsApi
import com.kipu.app.feature.goals.data.remote.GoalsApiResponse
import com.kipu.app.feature.movements.data.sync.requireSyncOwner
import javax.inject.Inject
import kotlinx.serialization.json.*

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
        val all = database.goalOutboxDao().getUnresolvedForUser(userId)
        fun ids(command: com.kipu.app.feature.goals.data.local.GoalOutboxEntity): Set<String> = buildSet {
            add(command.goalId)
            val body = runCatching { Json.parseToJsonElement(command.payload).jsonObject["payload"]?.jsonObject }.getOrNull()
            listOf("replacement_goal_id", "destination_goal_id").forEach { key -> body?.get(key)?.jsonPrimitive?.contentOrNull?.let(::add) }
        }
        val affected = mutableSetOf(goalId)
        do {
            val size = affected.size
            all.filter { ids(it).any(affected::contains) }.forEach { affected.addAll(ids(it)) }
        } while (affected.size != size)
        val commands = all.filter { ids(it).any(affected::contains) }
        if (commands.none { it.state in setOf("CONFLICT", "REJECTED", "BLOCKED") } || commands.any { it.state == "LEASED" })
            return GoalChangeApplyResult.Rejected("SYNC_CHECKPOINT_CHANGED")
        val snapshots = mutableMapOf<String, JsonObject>()
        for (id in affected) {
            val response = api.fetchGoal(userId, id)
            requireSyncOwner(session, userId)
            if (response !is GoalsApiResponse.Success) return GoalChangeApplyResult.Rejected("CONNECTION_REQUIRED")
            snapshots[id] = response.data
        }
        return applier.discardTerminalGoals(userId, snapshots, commands.map { it.operationId }.toSet()).also {
            if (it is GoalChangeApplyResult.Applied) scheduler.schedule(userId)
        }
    }
}
