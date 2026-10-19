package com.kipu.app.feature.goals.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class GoalCommandType {
    CREATE_GOAL,
    UPDATE_GOAL,
    DELETE_EMPTY_GOAL,
    RESERVE_GOAL,
    RELEASE_GOAL,
    REASSIGN_GOAL_RESERVE,
    ARCHIVE_GOAL,
}

@Serializable
data class GoalCommandIdentity(
    val operationId: String,
    val requestHash: String,
    val contractVersion: Int = CURRENT_CONTRACT_VERSION,
) {
    init {
        require(operationId.isNotBlank()) { "operationId must not be blank" }
        require(requestHash.matches(Regex("[0-9a-f]{64}"))) {
            "requestHash must be a lowercase SHA-256 digest"
        }
        require(contractVersion == CURRENT_CONTRACT_VERSION) { "Unsupported goal command contract version" }
    }

    companion object {
        const val CURRENT_CONTRACT_VERSION = 1
    }
}

@Serializable
data class GoalCommandEnvelope(
    val identity: GoalCommandIdentity,
    val type: GoalCommandType,
    val goalId: String,
    val expectedRevision: Long? = null,
    val payload: JsonObject,
) {
    init {
        require(goalId.isNotBlank()) { "goalId must not be blank" }
        require(expectedRevision == null || expectedRevision >= 0L) { "expectedRevision cannot be negative" }
    }
}

sealed interface GoalCommandResult {
    data class Applied(val goalId: String, val revision: Long, val eventId: String? = null) : GoalCommandResult
    data class Duplicate(val goalId: String, val revision: Long, val eventId: String? = null) : GoalCommandResult
    data class Conflict(val code: String, val currentRevision: Long? = null) : GoalCommandResult
    data class Rejected(val code: String) : GoalCommandResult
    data class Retryable(val code: String) : GoalCommandResult
}
