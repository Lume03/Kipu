package com.kipu.app.feature.accounts.presentation

import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EditAccountDebitRegistrationTest {
    private val account = Account(AccountId.generate(), UserId.generate(), "Ahorro BCP", AccountType.SAVINGS, Currency.PEN,
        preset = AccountPreset.BCP, initialBalance = Money(0, Currency.PEN), openedAt = Instant.EPOCH)

    @Test fun `editing without card registers linked debit using creation defaults`() = runBlocking {
        var registered: DebitCard? = null
        val result = registerDebitForEditedAccount(account, "Ahorro BCP", AccountPreset.BCP, null, null, "1234") {
            registered = it
            Result.success(Unit)
        }
        assertTrue(result.isSuccess)
        val card = requireNotNull(registered)
        assertEquals(account.id, card.linkedAccountId)
        assertEquals(account.currency, card.currency)
        assertEquals("BCP", card.issuer)
        assertEquals(CardNetwork.VISA, card.network)
        assertEquals(CardPreset.BCP_VISA, card.preset)
        assertEquals("1234", card.lastFourDigits)
    }

    @Test fun `empty digits do not register a card`() = runBlocking {
        var calls = 0
        val result = registerDebitForEditedAccount(account, "Ahorro BCP", AccountPreset.BCP, null, null, "") {
            calls++
            Result.success(Unit)
        }
        assertTrue(result.isSuccess)
        assertEquals(0, calls)
    }

    @Test fun `invalid digits do not register a card`() = runBlocking {
        var calls = 0
        val result = registerDebitForEditedAccount(account, "Ahorro BCP", AccountPreset.BCP, null, null, "１２３４") {
            calls++
            Result.success(Unit)
        }
        assertTrue(result.isFailure)
        assertEquals(0, calls)
    }

    @Test fun `banbif account registers debit with banbif issuer and style preset instead of other`() = runBlocking {
        val banbifAccount = Account(
            AccountId.generate(), UserId.generate(), "Cuenta Sueldo BanBif", AccountType.SAVINGS, Currency.PEN,
            preset = AccountPreset.GENERIC, initialBalance = Money(0, Currency.PEN), openedAt = Instant.EPOCH,
            colorToken = "banbif_cyan"
        )
        var registered: DebitCard? = null
        val result = registerDebitForEditedAccount(banbifAccount, "Cuenta Sueldo BanBif", AccountPreset.GENERIC, "banbif_cyan", null, "4321") {
            registered = it
            Result.success(Unit)
        }
        assertTrue(result.isSuccess)
        val card = requireNotNull(registered)
        assertEquals("BanBif", card.issuer)
        assertEquals("banbif-debit-classic", card.stylePresetId)
        assertEquals("4321", card.lastFourDigits)
    }

    @Test fun `scotiabank account defaults to mastercard network`() = runBlocking {
        val scoAccount = Account(
            AccountId.generate(), UserId.generate(), "Ahorro Scotiabank", AccountType.SAVINGS, Currency.PEN,
            preset = AccountPreset.SCOTIABANK, initialBalance = Money(0, Currency.PEN), openedAt = Instant.EPOCH
        )
        var registered: DebitCard? = null
        val result = registerDebitForEditedAccount(scoAccount, "Ahorro Scotiabank", AccountPreset.SCOTIABANK, null, null, "9876") {
            registered = it
            Result.success(Unit)
        }
        assertTrue(result.isSuccess)
        val card = requireNotNull(registered)
        assertEquals("Scotiabank", card.issuer)
        assertEquals(CardNetwork.MASTERCARD, card.network)
        assertEquals("9876", card.lastFourDigits)
    }

    @Test fun `explicit network parameter overrides default bank network`() = runBlocking {
        val scoAccount = Account(
            AccountId.generate(), UserId.generate(), "Ahorro Scotiabank", AccountType.SAVINGS, Currency.PEN,
            preset = AccountPreset.SCOTIABANK, initialBalance = Money(0, Currency.PEN), openedAt = Instant.EPOCH
        )
        var registered: DebitCard? = null
        val result = registerDebitForEditedAccount(
            scoAccount, "Ahorro Scotiabank", AccountPreset.SCOTIABANK, null, null, "9876",
            network = CardNetwork.VISA
        ) {
            registered = it
            Result.success(Unit)
        }
        assertTrue(result.isSuccess)
        val card = requireNotNull(registered)
        assertEquals(CardNetwork.VISA, card.network)
    }
}
