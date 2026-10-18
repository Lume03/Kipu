package com.kipu.app.feature.goals.domain

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalCommandHasherTest {
    @Test
    fun canonicalHashIgnoresObjectInsertionOrderAtEveryDepth() {
        val first = buildJsonObject {
            put("currency_code", "PEN")
            putJsonObject("goal") {
                put("target_minor", 12_500)
                put("name", "Viaje")
            }
        }
        val second = buildJsonObject {
            putJsonObject("goal") {
                put("name", "Viaje")
                put("target_minor", 12_500)
            }
            put("currency_code", "PEN")
        }

        assertEquals(GoalCommandHasher.sha256(first), GoalCommandHasher.sha256(second))
    }

    @Test
    fun identityCarriesStableOperationIdAndSha256Digest() {
        val payload = buildJsonObject { put("goal_id", "goal-1") }
        val identity = GoalCommandHasher.identity("operation-1", payload)

        assertEquals("operation-1", identity.operationId)
        assertEquals(64, identity.requestHash.length)
        assertEquals(identity.requestHash, identity.requestHash.lowercase())
    }
}
