package com.kipu.app.feature.capture.data

import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.capture.domain.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoomCaptureInstrumentResolverTest {
    private val owner = "owner"
    private var activeAccounts = listOf(account("bank"))
    private var activeCards = emptyList<CardEntity>()
    private val accounts = mockk<AccountDao> {
        coEvery { getActiveComputable(owner) } answers { activeAccounts }
    }
    private val cards = mockk<CardDao> {
        coEvery { getActive(owner) } answers { activeCards }
    }
    private val resolver = RoomCaptureInstrumentResolver(accounts, cards)
    private fun account(id: String, preset: String? = null, linked: String? = null) = AccountEntity(
        id = id, userId = owner, creationOperationId = id, alias = id,
        type = if (preset == null) "BANK" else "DIGITAL_WALLET", currency = "PEN",
        presetId = preset, color = null, icon = null, initialBalanceMinorUnits = 0,
        openedAt = 0, createdAt = 0, updatedAt = 0, linkedAccountId = linked,
    )
    private fun card(id: String, type: String = "DEBIT", currency: String = "PEN") = CardEntity(
        id = id, userId = owner, creationOperationId = id, accountId = if (type == "DEBIT") "bank" else null,
        type = type, currency = currency, network = "VISA", issuer = "BCP", lastFourDigits = "1234",
        createdAt = 0, updatedAt = 0,
    )
    private fun parsed(last4: String? = "1234", kind: CaptureInstrumentKind? = null) = ParsedCapture(
        1000, "PEN", CaptureDirection.EXPENSE, "Tienda", last4, null, false, emptySet(), kind,
    )

    @Test fun `unique debit and credit cards resolve without guessing`() = runTest {
        activeCards = listOf(card("debit"))
        assertEquals(CaptureInstrumentResolution(CaptureInstrumentKind.DEBIT_ACCOUNT, "bank", "debit"), resolver.resolve(owner, CaptureProvider.BCP, parsed()))
        activeCards = listOf(card("credit", "CREDIT"))
        assertEquals(CaptureInstrumentResolution(CaptureInstrumentKind.CREDIT_CARD, null, "credit"), resolver.resolve(owner, CaptureProvider.BCP, parsed()))
    }

    @Test fun `matching last four on two cards is ambiguous even with different currencies`() = runTest {
        activeCards = listOf(card("one"), card("two", currency = "USD"))
        val result = resolver.resolve(owner, CaptureProvider.BCP, parsed())
        assertTrue(result.ambiguous)
        assertNull(result.cardId)
        assertNull(result.accountId)
    }

    @Test fun `conflicting type or missing card requires review`() = runTest {
        activeCards = listOf(card("one"))
        val conflict = resolver.resolve(owner, CaptureProvider.BCP, parsed(kind = CaptureInstrumentKind.CREDIT_CARD))
        assertTrue(conflict.ambiguous)
        assertNull(conflict.cardId)
        activeCards = emptyList()
        assertNull(resolver.resolve(owner, CaptureProvider.BCP, parsed()).accountId)
    }

    @Test fun `wallet uses its unique linked account and multiple wallets are not guessed`() = runTest {
        val bank = account("bank")
        activeAccounts = listOf(bank, account("wallet", "PLIN", "bank"))
        assertEquals(CaptureInstrumentResolution(CaptureInstrumentKind.WALLET, "bank"), resolver.resolve(owner, CaptureProvider.PLIN, parsed(null)))
        activeAccounts = activeAccounts + account("other-wallet", "PLIN", "bank")
        val ambiguous = resolver.resolve(owner, CaptureProvider.PLIN, parsed(null))
        assertTrue(ambiguous.ambiguous)
        assertNull(ambiguous.accountId)
        activeAccounts = listOf(bank)
        assertNull(resolver.resolve(owner, CaptureProvider.YAPE, parsed(null)).accountId)
    }

    @Test fun `standalone Yape is resolved to its own account but broken links are not used`() = runTest {
        activeAccounts = listOf(account("yape", "YAPE"))
        assertEquals("yape", resolver.resolve(owner, CaptureProvider.YAPE, parsed(null)).accountId)
        activeAccounts = listOf(account("yape", "YAPE", "missing-bank"))
        assertNull(resolver.resolve(owner, CaptureProvider.YAPE, parsed(null)).accountId)
    }
}
