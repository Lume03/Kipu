package com.kipu.app.feature.goals.data.remote

import com.kipu.app.feature.goals.domain.model.GoalCommandEnvelope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
data class GoalRpcRequestDto(
    @SerialName("p_payload") val payload: JsonObject,
)

@Serializable
data class GoalCommandRemoteResult(
    @SerialName("status") val status: String,
    @SerialName("goal_id") val goalId: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("revision") val revision: Long? = null,
    @SerialName("current_revision") val currentRevision: Long? = null,
    @SerialName("sequence") val sequence: Long? = null,
    @SerialName("result") val result: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("goals") val goals: List<GoalCommandGoalRemoteResult>? = null,
)

@Serializable
data class GoalCommandGoalRemoteResult(
    @SerialName("goal_id") val goalId: String,
    @SerialName("status") val status: String,
    @SerialName("revision") val revision: Long,
    @SerialName("sequence") val sequence: Long,
)

fun GoalCommandEnvelope.toRpcPayload(): JsonObject = buildJsonObject {
    put("contract_version", identity.contractVersion)
    put("operation_id", identity.operationId)
    put("request_hash", identity.requestHash)
    put("command_type", type.name)
    put("goal_id", goalId)
    expectedRevision?.let { put("expected_revision", it) }
    putJsonObject("payload") {
        payload.forEach { (key, value) -> put(key, value) }
    }
}
