package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.instruments.PayCardDialog
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Rule
import org.junit.Test

class PayCardDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsCurrentDebtAndAllowsDialogDismissal() {
        compose.setContent {
            KipuTheme {
                PayCardDialog(
                    creditCardWithSummary = summary(),
                    eligibleAccounts = emptyList(),
                    onPayCreditCard = { _, _, _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Deuda actual: PEN 90.00").assertIsDisplayed()
        compose.onNodeWithText("Importe a Pagar").assertIsDisplayed()
        compose.onNodeWithText("No tienes cuentas activas en PEN con saldo suficiente.").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
    }

    @Test
    fun usesTotalDebtWhenNoInstallmentScheduleExists() {
        compose.setContent {
            KipuTheme {
                PayCardDialog(
                    creditCardWithSummary = summary(),
                    eligibleAccounts = emptyList(),
                    onPayCreditCard = { _, _, _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Deuda actual: PEN 90.00").assertIsDisplayed()
        compose.onNodeWithText("Importe a Pagar").assertIsDisplayed()
    }

    private fun summary(): CreditCardWithSummary {
        val card = CreditCard(
            id = CardId("8a000000-0000-4000-8000-000000000001"),
            userId = UserId("8a000000-0000-4000-8000-000000000002"),
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "1234",
            currency = Currency.PEN,
            creditLimitMinorUnits = 100_000L,
            billingDay = 20,
            dueDay = 10,
        )
        return CreditCardWithSummary(
            card = card,
            debt = Money(9_000L, Currency.PEN),
            availableCredit = Money(91_000L, Currency.PEN),
            utilizationPercentage = 9.0,
        )
    }
}
