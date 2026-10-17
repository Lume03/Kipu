package com.kipu.app.feature.accounts.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.accounts.presentation.components.CreditCardSummaryCard
import com.kipu.app.feature.accounts.presentation.dashboard.DashboardScreen
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Issue48ProductionLabelsTest {
    @get:Rule
    val compose = createComposeRule()

    private val userId = UserId.generate()
    private val account = Account(
        id = AccountId.generate(),
        userId = userId,
        alias = "Ahorros principal",
        type = AccountType.SAVINGS,
        currency = Currency.PEN,
        initialBalance = Money(230_000, Currency.PEN),
        openedAt = Instant.parse("2026-10-01T12:00:00Z"),
    )
    private val creditCard = CreditCard(
        id = CardId.generate(),
        userId = userId,
        alias = "Visa principal",
        issuer = "BCP",
        network = CardNetwork.VISA,
        lastFourDigits = "4821",
        currency = Currency.PEN,
        creditLimitMinorUnits = 600_000,
        billingDay = 15,
        dueDay = 5,
    )
    private val cardSummary = CreditCardWithSummary(
        card = creditCard,
        debt = Money(130_000, Currency.PEN),
        availableCredit = Money(470_000, Currency.PEN),
        utilizationPercentage = 21.67,
    )

    @Test
    fun populatedDashboardRouteUsesEpCtaFinancialLabels() {
        val dashboardData = FinancialDashboardData(
            totalPen = Money(230_000, Currency.PEN),
            totalUsd = Money(0, Currency.USD),
            liquidAccounts = listOf(AccountWithBalance(account, Money(230_000, Currency.PEN))),
            creditCards = listOf(cardSummary),
            activeComputableCount = 2,
        )
        val viewModel = mockk<AccountsViewModel>(relaxed = true)
        every { viewModel.dashboardUiState } returns MutableStateFlow(
            DashboardUiState(isLoading = false, dashboardData = dashboardData),
        )
        every { viewModel.instrumentsUiState } returns MutableStateFlow(
            InstrumentsUiState(
                isLoading = false,
                activeAccounts = listOf(account),
                activeCards = listOf(creditCard),
                activeComputableCount = 2,
                maxFreeQuota = 4,
            ),
        )
        every { viewModel.creditNotifications } returns MutableStateFlow(emptyList())
        every { viewModel.isMasked } returns MutableStateFlow(false)

        compose.setContent {
            KipuTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToNewAccount = {},
                    onNavigateToNewCard = {},
                )
            }
        }

        compose.onNodeWithText(FinancialTerminology.REAL_MONEY_AVAILABLE).assertIsDisplayed()
        compose.onNodeWithText(
            "Dinero propio en cuentas y efectivo \u00B7 No incluye cr\u00E9dito",
        ).assertIsDisplayed()
        compose.onNodeWithText("Cupo Free de instrumentos \u00B7 2 de 4")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("2 espacios para cuentas y tarjetas")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Tarjetas de cr\u00E9dito")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            compose.onAllNodesWithText(FinancialTerminology.AVAILABLE_CREDIT_NOT_OWN_MONEY)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )

        compose.onNodeWithText("TOTAL DISPONIBLE").assertDoesNotExist()
        compose.onNodeWithText("L\u00EDneas de cr\u00E9dito (no suman al saldo disponible)")
            .assertDoesNotExist()
    }

    @Test
    fun productionCreditCardSummarySeparatesDebtAvailableCreditAndTotalLine() {
        compose.setContent {
            KipuTheme {
                CreditCardSummaryCard(creditCardWithSummary = cardSummary)
            }
        }

        compose.onNodeWithText("Deuda actual").assertIsDisplayed()
        compose.onNodeWithText(FinancialTerminology.AVAILABLE_CREDIT_NOT_OWN_MONEY)
            .assertIsDisplayed()
        compose.onNodeWithText("Uso de l\u00EDnea total de cr\u00E9dito:", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Disponible en l\u00EDnea").assertDoesNotExist()
    }
}
