package com.kipu.app.feature.portability

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PortabilityContractTest {
    @Test fun emptyExportHasVersionAndEveryEntity() {
        val result = Json.parseToJsonElement(PortableJson.encode("owner", emptyMap())).jsonObject
        assertEquals(1, result.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(PortableJson.columns.keys, result.getValue("entities").jsonObject.keys)
        assertTrue(result.getValue("entities").jsonObject.values.all { it.jsonArray.isEmpty() })
    }
    @Test fun exportFiltersOwnersSecretsOperationalTablesAndNestedPayloads() {
        val row = buildJsonObject {
            put("user_id", "owner"); put("id", "tx"); put("amount_minor", Long.MAX_VALUE); put("currency_code", "PEN")
            put("access_token", "secret"); put("evidence_snippet", "private")
        }
        val revision = buildJsonObject {
            put("user_id", "owner"); put("revision_id", "r1")
            put("new_payload", """{"user_id":"owner","amount_minor":123,"token":"secret","evidence_snippet":"private","nested_secret":"hidden"}""")
        }
        val result = PortableJson.encode("owner", mapOf(
            "transactions" to listOf(row, JsonObject(row + ("user_id" to JsonPrimitive("other")))),
            "transaction_revisions" to listOf(revision), "feature_access_cache" to listOf(row), "receipt_candidates" to listOf(row),
        ))
        val entities = Json.parseToJsonElement(result).jsonObject.getValue("entities").jsonObject
        assertEquals(1, entities.getValue("transactions").jsonArray.size)
        assertEquals(Long.MAX_VALUE, entities.getValue("transactions").jsonArray[0].jsonObject.getValue("amount_minor").jsonPrimitive.long)
        assertFalse(result.contains("secret")); assertFalse(result.contains("private")); assertFalse(result.contains("other"))
        assertFalse(entities.containsKey("feature_access_cache")); assertFalse(entities.containsKey("receipt_candidates"))
    }
    @Test fun partialResultRetriesOnlyFailedModuleAndNeverReportsFalseSuccess() = runTest {
        var firstCalls = 0; var secondCalls = 0
        val runner = RestoreRunner(linkedMapOf<String, suspend (String) -> Boolean>(
            "movements" to { firstCalls++; true }, "goals" to { secondCalls++; secondCalls > 1 },
        ))
        val first = runner.run("owner")
        assertEquals(RestoreStatus.PARTIAL, first.status)
        val retry = runner.run("owner", first)
        assertEquals(RestoreStatus.COMPLETED, retry.status)
        assertEquals(1, firstCalls); assertEquals(2, secondCalls)
        assertEquals(RestoreStatus.ERROR, RestoreRunner(mapOf("offline" to { throw java.io.IOException() })).run("owner").status)
    }
    @Test fun cancellationAndOwnerChangeCannotBeReportedAsSuccess() = runTest {
        val runner = RestoreRunner(mapOf("cancel" to { throw CancellationException() }))
        try { runner.run("owner"); fail() } catch (_: CancellationException) { }
        try { runner.run("other", RestoreResult("owner", mapOf("cancel" to true))); fail() } catch (_: IllegalArgumentException) { }
    }
}
