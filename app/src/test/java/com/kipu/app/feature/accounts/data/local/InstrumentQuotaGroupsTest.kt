package com.kipu.app.feature.accounts.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class InstrumentQuotaGroupsTest {
    private fun account(id: String, type: String = "SAVINGS", parent: String? = null, archived: Boolean = false) =
        AccountEntity(id, "owner", "op-$id", id, type, "PEN", null, null, null, 0, 0,
            isArchived = archived, createdAt = 0, updatedAt = 0, linkedAccountId = parent)

    private fun card(id: String, parent: String?, type: String = "DEBIT") =
        CardEntity(id, "owner", "op-$id", accountId = parent, type = type, currency = "PEN",
            network = "VISA", issuer = "BCP", lastFourDigits = "1234", createdAt = 0, updatedAt = 0)

    @Test fun bankDebitAndWalletsShareOneSlot() {
        val groups = instrumentQuotaGroups(
            listOf(account("bank"), account("yape", "DIGITAL_WALLET", "bank"), account("plin", "DIGITAL_WALLET", "bank")),
            listOf(card("debit", "bank")),
        )
        assertEquals(setOf("bank"), groups.values.toSet())
        assertEquals("bank", groups["yape"])
        assertEquals("bank", groups["debit"])
    }

    @Test fun separateBalancesConsumeSeparateSlotsWhileCashAndCreditLiabilityDoNot() {
        val groups = instrumentQuotaGroups(
            listOf(account("bank"), account("wallet", "DIGITAL_WALLET"), account("cash", "CASH"), account("liability", "CREDIT_LIABILITY")),
            listOf(card("credit", "liability", "CREDIT")),
        )
        assertEquals(setOf("bank", "wallet", "credit"), groups.values.toSet())
    }

    @Test fun inactiveParentCannotHideAnActiveInstrumentFromQuota() {
        val groups = instrumentQuotaGroups(listOf(account("bank", archived = true), account("wallet", "DIGITAL_WALLET", "bank")), listOf(card("debit", "bank")))
        assertEquals(setOf("wallet", "debit"), groups.values.toSet())
    }
}
