package com.kipu.app.feature.debts.presentation

import com.kipu.app.feature.accounts.data.local.AccountEntity
import org.junit.Assert.*
import org.junit.Test

class DebtAccountOptionsTest {
    private fun account(id: String, parent: String? = null, currency: String = "PEN", type: String = "BANK") =
        AccountEntity(id, "owner", id, id, type, currency, null, null, null, 0, 0, createdAt = 0, updatedAt = 0, linkedAccountId = parent)

    @Test fun affiliatedWalletUsesParentBalanceAndKeepsSelectedChannel() {
        val options = debtAccountOptions(listOf(account("BCP"), account("Yape", "BCP")), mapOf("BCP" to 12345L, "Yape" to 0L))
        val wallet = options.single { it.id == "Yape" }
        assertEquals("BCP", wallet.effectiveAccountId)
        assertEquals("BCP", wallet.sharedWith)
        assertEquals(12345L, wallet.balanceMinor)
        assertEquals(options.first().balanceMinor, wallet.balanceMinor)
    }
    @Test fun absentOrDifferentCurrencyParentCannotBeSelected() {
        assertTrue(debtAccountOptions(listOf(account("Yape", "missing")), emptyMap()).isEmpty())
        assertEquals(listOf("BCP"), debtAccountOptions(listOf(account("BCP", currency = "USD"), account("Yape", "BCP")), emptyMap()).map { it.id })
    }
    @Test fun independentWalletAndNormalAccountKeepOwnBalance() {
        val options = debtAccountOptions(listOf(account("cash"), account("wallet", type = "DIGITAL_WALLET")), mapOf("cash" to 50L, "wallet" to 90L))
        assertEquals(listOf(50L, 90L), options.map { it.balanceMinor })
        options.forEach { assertEquals(it.id, it.effectiveAccountId); assertNull(it.sharedWith) }
    }
    @Test fun excludedAndChainedParentsAreNotEligible() {
        val options = debtAccountOptions(listOf(account("virtual", type = "GOALS_VIRTUAL"), account("card", type = "CREDIT_LIABILITY"), account("wallet", "virtual")), emptyMap())
        assertTrue(options.isEmpty())
    }
}
