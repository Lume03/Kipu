package com.kipu.app.feature.portability

import com.kipu.app.feature.movements.data.local.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementExportTest {
    private val movement = TransactionEntity(id = "id-1", userId = "owner", type = "EXPENSE", amountMinor = 12345, currencyCode = "PEN", sourceAccountId = "account-1", categoryId = "category-1", occurredAt = 1700000000000, note = "Comida, \"bien\"\nmañana")

    @Test fun csvUsesMinorUnitsAndEscapesFieldsAndFiltersOwner() {
        val csv = MovementExport.csv("owner", listOf(movement, movement.copy(id = "other-id", userId = "other")))
        assertTrue(csv.startsWith("id,type,amount_minor,currency_code,occurred_at,note,source_account_id,destination_account_id,card_id,category_id,merchant_id\r\n"))
        assertTrue(csv.contains("id-1,EXPENSE,12345,PEN,1700000000000,\"Comida, \"\"bien\"\"\nmañana\""))
        assertFalse(csv.contains("other-id"))
    }

    @Test fun emptyCsvContainsStableHeader() {
        assertEquals("id,type,amount_minor,currency_code,occurred_at,note,source_account_id,destination_account_id,card_id,category_id,merchant_id\r\n", MovementExport.csv("owner", emptyList()))
    }
}
