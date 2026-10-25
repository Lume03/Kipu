package com.kipu.app.feature.movements.data.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Both ACCOUNT snapshots emitted by `pull_financial_changes_v1` must restore the same account. */
class SyncAccountPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun commandSnapshotDecodes() {
        val payload = json.decodeFromString<SyncAccountPayload>(
            """{"id":"acc-1","type":"CASH","alias":"Efectivo","currency":"PEN","opened_at":"2026-10-25T17:03:52.104+00:00",
               "preset_id":"CASH","linked_account_id":null,"creation_operation_id":"op-1","initial_balance_minor_units":50000}""",
        )

        assertEquals("Efectivo", payload.alias)
        assertEquals("CASH", payload.type)
        assertEquals("PEN", payload.currency)
        assertEquals(50_000L, payload.initialBalanceMinorUnits)
    }

    @Test
    fun tableRowSnapshotDecodesToTheSameFields() {
        val payload = json.decodeFromString<SyncAccountPayload>(
            """{"id":"acc-2","icon":null,"name":"Cuenta BCP","color":null,"user_id":"owner","icon_key":null,"revision":2,
               "opened_at":"2026-10-21T20:02:03.686+00:00","preset_id":"BCP","color_argb":null,"created_at":"2026-10-21T20:02:03+00:00",
               "deleted_at":null,"updated_at":"2026-10-21T20:02:03+00:00","is_archived":true,"account_type":"SAVINGS",
               "currency_code":"PEN","institution_code":null,"linked_account_id":null,"creation_operation_id":"op-2",
               "initial_balance_minor_units":100000}""",
        )

        assertEquals("Cuenta BCP", payload.alias)
        assertEquals("SAVINGS", payload.type)
        assertEquals("PEN", payload.currency)
        assertEquals(100_000L, payload.initialBalanceMinorUnits)
        assertTrue(payload.isArchived)
    }
}
