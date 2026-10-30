package com.kipu.app.feature.goals.data.local

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalBackingAccountMappingTest {
    @Test
    fun bankMetadataReachesUiWithoutChangingNegativeAvailability() {
        val account = GoalBackingAccountRow(
            accountId = "account",
            accountName = "Cuenta personal",
            currencyCode = "PEN",
            confirmedBalanceMinor = 1_000L,
            reservedMinor = 6_200L,
            accountType = "SAVINGS",
            presetId = "BCP",
        ).toDomain()

        assertEquals(AccountType.SAVINGS, account.accountType)
        assertEquals(AccountPreset.BCP, account.preset)
        assertEquals(Money(-5_200L, Currency.PEN), account.available)
        assertEquals(Money(5_200L, Currency.PEN), account.shortfall)
    }

    @Test
    fun cashMetadataDoesNotDependOnItsAliasOrABankPreset() {
        val account = GoalBackingAccountRow(
            accountId = "cash",
            accountName = "Mi bolsillo",
            currencyCode = "PEN",
            confirmedBalanceMinor = 2_000L,
            reservedMinor = 0L,
            accountType = "CASH",
            presetId = null,
        ).toDomain()

        assertEquals(AccountType.CASH, account.accountType)
        assertEquals(null, account.preset)
        assertEquals(Money(2_000L, Currency.PEN), account.available)
    }
}
