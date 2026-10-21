package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.DebitCard
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.InstrumentsUiState
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class UnifiedInstrumentWalletAffiliationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun affiliatedYapeAndPlinSubmitBankAccountsWithoutDebitCardDigits() {
        val owner = UserId("11111111-1111-1111-1111-111111111111")
        val bcpAccount = bankAccount(
            owner = owner,
            id = "22222222-2222-2222-2222-222222222222",
            alias = "Ahorros BCP",
            preset = AccountPreset.BCP,
        )
        val bbvaAccount = bankAccount(
            owner = owner,
            id = "33333333-3333-3333-3333-333333333333",
            alias = "Ahorros BBVA",
            preset = AccountPreset.BBVA,
        )
        val accounts = listOf(bcpAccount, bbvaAccount)
        val debitCards = listOf(
            debitCard(owner, "44444444-4444-4444-4444-444444444444", "BCP", bcpAccount, CardPreset.BCP_VISA),
            debitCard(owner, "55555555-5555-5555-5555-555555555555", "BBVA", bbvaAccount, CardPreset.BBVA_VISA),
        )
        val viewModel = mockk<AccountsViewModel>(relaxed = true) {
            every { creditProductCatalog } returns MutableStateFlow(emptyList())
            every { instrumentsUiState } returns MutableStateFlow(
                InstrumentsUiState(isLoading = false, activeAccounts = accounts, activeCards = emptyList()),
            )
            every { events } returns emptyFlow<AccountUiEvent>()
        }
        every {
            viewModel.createAccount(
                alias = any(),
                type = any(),
                currency = any(),
                preset = any(),
                initialBalanceMinorUnits = any(),
                colorToken = any(),
                iconToken = any(),
                linkedAccountId = any(),
                onSuccess = any(),
            )
        } answers {
            val alias = arg<String>(0)
            val type = arg<AccountType>(1)
            val currency = arg<Currency>(2)
            val preset = arg<AccountPreset?>(3)
            val initialBalanceMinorUnits = arg<Long>(4)
            arg<(Account) -> Unit>(8).invoke(
                Account(
                    id = AccountId.generate(),
                    userId = owner,
                    alias = alias,
                    type = type,
                    currency = currency,
                    preset = preset,
                    initialBalance = Money(initialBalanceMinorUnits, currency),
                    openedAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
            )
        }

        compose.setContent {
            KipuTheme {
                UnifiedInstrumentFormScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onSaveSuccess = {},
                )
            }
        }

        compose.onNodeWithText("Billetera digital").performClick()
        compose.onNodeWithText("Afiliada a cuenta").performClick()
        compose.onNodeWithText("Ahorros BCP", substring = true).performClick()
        compose.onNodeWithTag("btn_save_instrument").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Plin").performClick()
        compose.onNodeWithText("Ahorros BBVA", substring = true).performClick()
        compose.onNodeWithTag("btn_save_instrument").performClick()
        compose.waitForIdle()

        verify(exactly = 1) {
            viewModel.createAccount(
                alias = any(),
                type = AccountType.DIGITAL_WALLET,
                currency = Currency.PEN,
                preset = AccountPreset.YAPE,
                initialBalanceMinorUnits = 0L,
                colorToken = any(),
                iconToken = any(),
                linkedAccountId = bcpAccount.id.value,
                onSuccess = any(),
            )
        }
        verify(exactly = 1) {
            viewModel.createAccount(
                alias = any(),
                type = AccountType.DIGITAL_WALLET,
                currency = Currency.PEN,
                preset = AccountPreset.PLIN,
                initialBalanceMinorUnits = 0L,
                colorToken = any(),
                iconToken = any(),
                linkedAccountId = bbvaAccount.id.value,
                onSuccess = any(),
            )
        }
    }

    private fun bankAccount(
        owner: UserId,
        id: String,
        alias: String,
        preset: AccountPreset,
    ) = Account(
        id = AccountId(id),
        userId = owner,
        alias = alias,
        type = AccountType.SAVINGS,
        currency = Currency.PEN,
        preset = preset,
        initialBalance = Money(100_000, Currency.PEN),
        openedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun debitCard(
        owner: UserId,
        id: String,
        issuer: String,
        account: Account,
        preset: CardPreset,
    ) = DebitCard(
        id = CardId(id),
        userId = owner,
        issuer = issuer,
        network = CardNetwork.VISA,
        lastFourDigits = "4321",
        currency = Currency.PEN,
        linkedAccountId = account.id,
        preset = preset,
    )
}
