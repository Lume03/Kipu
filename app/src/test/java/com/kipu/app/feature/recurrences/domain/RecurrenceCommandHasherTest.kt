package com.kipu.app.feature.recurrences.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecurrenceCommandHasherTest {
    private val paused = Json.parseToJsonElement(
        """{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"operation_id":"retry-id","request_hash":"ignored","recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001","expected_revision":7,"payload":{"status":"PAUSED"}}""",
    )

    @Test
    fun approvedPausedGoldenVectorAndObjectInsertionOrderInvariance() {
        val reordered = buildJsonObject {
            putJsonObject("payload") { put("status", "PAUSED") }
            put("expected_revision", 7)
            put("recurrence_id", "018f47d9-7b6a-7c01-9b14-8d40c2a8a001")
            put("contract_version", 1)
            put("command_type", "SET_RECURRENCE_STATUS")
            put("request_hash", "different")
            put("operation_id", "another-retry-id")
        }
        assertEquals(
            """{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"expected_revision":7,"payload":{"status":"PAUSED"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}""",
            RecurrenceCommandHasher.canonicalBytes(paused),
        )
        assertEquals("0085caad3e569114dbd6399a53d9d667384fbbc65b767664ec19efeccffcc67c", RecurrenceCommandHasher.sha256(paused))
        assertEquals(RecurrenceCommandHasher.canonicalBytes(paused), RecurrenceCommandHasher.canonicalBytes(reordered))
    }

    @Test
    fun explicitNullAndOmittedPatchFieldHaveDifferentCanonicalBytes() {
        val withNull = Json.parseToJsonElement(
            """{"command_type":"CREATE_RECURRENCE","contract_version":1,"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001","payload":{"frequency":"MONTHLY","end_date":null}}""",
        )
        val omitted = Json.parseToJsonElement(
            """{"command_type":"CREATE_RECURRENCE","contract_version":1,"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001","payload":{"frequency":"MONTHLY"}}""",
        )
        assertEquals("59c7ccef28123bb7ebad8d5ec5d05ffc73303f72b8415dc9a3f2291b56e2f508", RecurrenceCommandHasher.sha256(withNull))
        assertNotEquals(RecurrenceCommandHasher.canonicalBytes(withNull), RecurrenceCommandHasher.canonicalBytes(omitted))
    }

    @Test
    fun changingReminderLeadDaysChangesDigestAndUsesApprovedCreateVector() {
        val two = Json.parseToJsonElement(
            """{"command_type":"CREATE_RECURRENCE","contract_version":1,"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001","payload":{"amount_mode":"APPROXIMATE","reminder_lead_days":2,"template_transaction":{"concept":"x","currency_code":"PEN"}}}""",
        )
        val three = Json.parseToJsonElement(
            """{"command_type":"CREATE_RECURRENCE","contract_version":1,"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001","payload":{"amount_mode":"APPROXIMATE","reminder_lead_days":3,"template_transaction":{"concept":"x","currency_code":"PEN"}}}""",
        )
        assertEquals("b89a573522265c85c6b348689502f7bb8b81eefcdd1f13d597ab9a1434e2542c", RecurrenceCommandHasher.sha256(two))
        assertEquals("649fac8789974eff2499a5b3617cc78c91874c5735e5306cde36b00c9ee334bb", RecurrenceCommandHasher.sha256(three))
        assertNotEquals(RecurrenceCommandHasher.sha256(two), RecurrenceCommandHasher.sha256(three))
    }

    @Test
    fun exactNumericGoldenVectorsNormalizeIntegralDecimalAndExponentButRejectFractions() {
        val integralSpellings = listOf("100", "100.00", "1e2")
        val canonical = integralSpellings.map { spelling -> RecurrenceCommandHasher.canonicalBytes(numericEnvelope(spelling)) }
        assertEquals(1, canonical.distinct().size)
        assertEquals(
            """{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"expected_revision":100,"payload":{"status":"PAUSED"},"recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001"}""",
            canonical.first(),
        )
        assertEquals("55ee6fee2909de6a4e72bb75956be25eb91f59e77f2339c33072e63fb2d6bbf9", RecurrenceCommandHasher.sha256(numericEnvelope("100")))
        assertThrows(IllegalArgumentException::class.java) { RecurrenceCommandHasher.sha256(numericEnvelope("100.5")) }
        assertThrows(IllegalArgumentException::class.java) { RecurrenceCommandHasher.sha256(numericEnvelope("1e-1")) }
    }

    @Test
    fun canonicalStringEscapingPreservesUnicodeAndEscapesControls() {
        val scalarToken = Json.parseToJsonElement("\"é\\\"\\\\/\\b\\f\\n\\r\\t\\u0001\\u001f\"")
        assertEquals("\"é\\\"\\\\/\\b\\f\\n\\r\\t\\u0001\\u001f\"", RecurrenceCommandHasher.canonicalBytes(scalarToken))
        assertEquals(
            "982f93929969c10fd43f868cf05150ec7e954d105e43e283fe69cfb6b305f16e",
            RecurrenceCommandHasher.sha256(scalarToken),
        )
        val nfc = Json.parseToJsonElement("\"é\"")
        val nfd = Json.parseToJsonElement("\"e\\u0301\"")
        assertNotEquals(RecurrenceCommandHasher.canonicalBytes(nfc), RecurrenceCommandHasher.canonicalBytes(nfd))
    }

    @Test
    fun completeCreateEnvelopeMatchesContractBytesAndDigest() {
        val command = buildJsonObject {
            put("command_type", "CREATE_RECURRENCE")
            put("contract_version", 1)
            put("recurrence_id", "018f47d9-7b6a-7c01-9b14-8d40c2a8a001")
            putJsonObject("payload") {
                put("amount_mode", "APPROXIMATE"); put("custom_interval_unit", kotlinx.serialization.json.JsonNull)
                put("end_date", kotlinx.serialization.json.JsonNull); put("frequency", "MONTHLY")
                put("interval_count", 1); put("next_due_date", "2026-10-31"); put("reminder_lead_days", 2)
                put("start_date", "2026-10-31")
                putJsonObject("template_transaction") {
                    put("amount_minor", 125000); put("category_id", "018f47d9-7b6a-7c01-9b14-8d40c2a8a002")
                    put("concept", "á\"\\/\n\u0001\u2028\u2029"); put("currency_code", "PEN")
                    put("destination_account_id", kotlinx.serialization.json.JsonNull)
                    put("merchant_id", kotlinx.serialization.json.JsonNull)
                    put("merchant_provisional_text", kotlinx.serialization.json.JsonNull)
                    put("source_account_id", "018f47d9-7b6a-7c01-9b14-8d40c2a8a003")
                    put("type", "EXPENSE")
                }
            }
        }
        val canonical = RecurrenceCommandHasher.canonicalBytes(command)
        assertEquals(629, canonical.toByteArray(Charsets.UTF_8).size)
        assertEquals(
            "8a4dc1abcca8607b0bd4df1f36ec3e1768bbae179a4971c151465a893ccf029e",
            RecurrenceCommandHasher.sha256(command),
        )
    }

    private fun numericEnvelope(value: String) = Json.parseToJsonElement(
        """{"command_type":"SET_RECURRENCE_STATUS","contract_version":1,"operation_id":"o","request_hash":"h","recurrence_id":"018f47d9-7b6a-7c01-9b14-8d40c2a8a001","expected_revision":$value,"payload":{"status":"PAUSED"}}""",
    )
}
