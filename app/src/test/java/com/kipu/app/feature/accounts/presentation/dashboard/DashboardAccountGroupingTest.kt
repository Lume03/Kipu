package com.kipu.app.feature.accounts.presentation.dashboard

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardAccountGroupingTest {
    private val user = UserId("f2000000-0000-4000-8000-000000000001")

    @Test
    fun affiliatedWalletsAreShownUnderTheirAccount() {
        val cash = item("1", AccountType.CASH, AccountPreset.CASH, 10_000)
        val bbva = item("2", AccountType.SAVINGS, AccountPreset.BBVA, 10_000)
        val plin = item("3", AccountType.DIGITAL_WALLET, AccountPreset.PLIN, 10_000, linkedTo = "2")
        val bcp = item("4", AccountType.SAVINGS, AccountPreset.BCP, 99_770)
        val yape = item("5", AccountType.DIGITAL_WALLET, AccountPreset.YAPE, 99_770, linkedTo = "4")

        val groups = groupAccountsWithWallets(listOf(cash, bbva, plin, bcp, yape))

        assertEquals(listOf(cash, bbva, bcp), groups.map { it.account })
        assertEquals(listOf(plin), groups[1].wallets)
        assertEquals(listOf(yape), groups[2].wallets)
    }

    @Test
    fun walletWhoseAccountIsNotListedStaysOnItsOwnRow() {
        val orphan = item("6", AccountType.DIGITAL_WALLET, AccountPreset.YAPE, 5_000, linkedTo = "99")

        val groups = groupAccountsWithWallets(listOf(orphan))

        assertEquals(listOf(orphan), groups.map { it.account })
    }

    @Test
    fun sharedWalletBalanceIsNotAddedTwiceToBankMoney() {
        val bcp = item("4", AccountType.SAVINGS, AccountPreset.BCP, 99_770)
        val yape = item("5", AccountType.DIGITAL_WALLET, AccountPreset.YAPE, 99_770, linkedTo = "4")
        val cash = item("1", AccountType.CASH, AccountPreset.CASH, 10_000)
        val all = listOf(bcp, yape, cash)

        val bank = sumByCurrency(all) { it.account.type != AccountType.CASH && it.account.linkedAccountId == null }
        val inCash = sumByCurrency(all) { it.account.type == AccountType.CASH }

        assertEquals(mapOf(Currency.PEN to 99_770L), bank)
        assertEquals(mapOf(Currency.PEN to 10_000L), inCash)
    }

    @Test
    fun emptyListsKeepAZeroInSolesAndPlanLabelUsesRealQuota() {
        assertEquals(mapOf(Currency.PEN to 0L), sumByCurrency(emptyList()) { true })
        assertEquals("4 de 4 cuentas y tarjetas", planUsageLabel(4, 4))
    }

    private fun item(
        suffix: String,
        type: AccountType,
        preset: AccountPreset,
        balance: Long,
        linkedTo: String? = null,
    ): AccountWithBalance {
        val account = Account(
            id = AccountId(id(suffix)),
            userId = user,
            alias = preset.defaultName,
            type = type,
            currency = Currency.PEN,
            preset = preset,
            initialBalance = Money(0L, Currency.PEN),
            openedAt = Instant.parse("2026-01-01T00:00:00Z"),
            linkedAccountId = linkedTo?.let { AccountId(id(it)) },
        )
        return AccountWithBalance(account, Money(balance, Currency.PEN))
    }

    private fun id(suffix: String) = "f2000000-0000-4000-8000-" + suffix.padStart(12, '0')
}
