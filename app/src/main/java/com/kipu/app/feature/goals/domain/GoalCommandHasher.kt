package com.kipu.app.feature.goals.domain

import com.kipu.app.feature.goals.domain.model.GoalCommandIdentity
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Hashes canonical command JSON so retries remain stable across map insertion order. */
object GoalCommandHasher {
    fun sha256(payload: JsonObject): String = sha256(canonicalize(payload))

    fun sha256(canonicalPayload: String): String {
        require(canonicalPayload.isNotBlank()) { "A canonical goal command payload is required" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalPayload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun identity(
        operationId: String = UUID.randomUUID().toString(),
        payload: JsonObject,
    ): GoalCommandIdentity = GoalCommandIdentity(operationId, sha256(payload))

    fun canonicalize(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                encodePrimitive(JsonPrimitive(key)) + ":" + canonicalize(value)
            }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::canonicalize)
        is JsonPrimitive -> Json.encodeToString(JsonElement.serializer(), element)
    }

    private fun encodePrimitive(value: JsonPrimitive): String =
        Json.encodeToString(JsonElement.serializer(), value)
}
