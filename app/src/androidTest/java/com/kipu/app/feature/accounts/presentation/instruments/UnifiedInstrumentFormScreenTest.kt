package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

class UnifiedInstrumentFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun createsSavingsAccountWithoutRequiringDebitCardDigits() {
        val viewModel = mockk<AccountsViewModel>(relaxed = true) {
            every { creditProductCatalog } returns MutableStateFlow(emptyList())
            every { instrumentsUiState } returns MutableStateFlow(InstrumentsUiState(isLoading = false))
            every { events } returns emptyFlow<AccountUiEvent>()
        }
        compose.setContent {
            KipuTheme {
                UnifiedInstrumentFormScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }

        compose.onNodeWithText("Cuenta bancaria").performClick()
        compose.onNodeWithText("Últimos 4 dígitos").assertIsDisplayed()
        compose.onNodeWithText("Guardar cuenta").performClick()

        verify(timeout = 5_000) {
            viewModel.createAccount(
                alias = "Cuenta BCP",
                type = AccountType.SAVINGS,
                currency = Currency.PEN,
                preset = AccountPreset.BCP,
                initialBalanceMinorUnits = 0L,
                colorToken = any(),
                iconToken = any(),
                linkedAccountId = null,
                onSuccess = any(),
            )
        }
        verify(exactly = 0) {
            viewModel.registerDebitCard(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun affiliatedWalletProfileShowsOnlyCompatibleBankAccountAndClearOwnBalanceCopy() {
        val owner = UserId("11111111-1111-1111-1111-111111111111")
        val accountId = AccountId("22222222-2222-2222-2222-222222222222")
        val linkedAccount = Account(
            id = accountId,
            userId = owner,
            alias = "Ahorro principal",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            preset = AccountPreset.BCP,
            initialBalance = Money(10_000, Currency.PEN),
            openedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val debitCard = DebitCard(
            id = CardId("33333333-3333-3333-3333-333333333333"),
            userId = owner,
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "4321",
            currency = Currency.PEN,
            linkedAccountId = accountId,
            preset = CardPreset.BCP_VISA,
        )
        val viewModel = mockk<AccountsViewModel>(relaxed = true) {
            every { creditProductCatalog } returns MutableStateFlow(emptyList())
            every { instrumentsUiState } returns MutableStateFlow(
                InstrumentsUiState(isLoading = false, activeAccounts = listOf(linkedAccount), activeCards = listOf(debitCard)),
            )
            every { events } returns emptyFlow<AccountUiEvent>()
        }

        compose.setContent {
            KipuTheme {
                UnifiedInstrumentFormScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }

        compose.onNodeWithText("Billetera digital").performClick()
        compose.onNodeWithText("Afiliada a una cuenta").performClick()

        compose.onNodeWithText("¿A qué cuenta está afiliada?").assertIsDisplayed()
        compose.onNodeWithText("BCP · Ahorros · •••• 4321").assertIsDisplayed()
        compose.onNodeWithText("Saldo propio").assertIsDisplayed()
        compose.onNodeWithText("Saldo propio (DNI)").assertDoesNotExist()
    }
}
