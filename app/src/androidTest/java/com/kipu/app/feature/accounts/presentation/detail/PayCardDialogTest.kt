package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.instruments.PayCardDialog
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PayCardDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun defaultsToTotalDebtAndTotalChipConfirmsFullPayment() {
        var confirmedAmount: Money? = null
        compose.setContent {
            KipuTheme {
                PayCardDialog(
                    creditCardWithSummary = summary(),
                    eligibleAccounts = listOf(paymentAccount()),
                    onPayCreditCard = { _, _, amount, onSuccess, _ ->
                        confirmedAmount = amount
                        onSuccess()
                    },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Deuda actual: PEN 90.00").assertIsDisplayed()
        compose.onNodeWithText("Importe a Pagar").assertIsDisplayed()
        compose.onNodeWithText("90.00").assertIsDisplayed()

        compose.onNodeWithText("Total").performClick()
        compose.onNodeWithText("90.00").assertIsDisplayed()
        compose.onNodeWithText("Confirmar Pago").performClick()

        compose.runOnIdle { assertEquals(Money(9_000L, Currency.PEN), confirmedAmount) }
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

    private fun paymentAccount() = AccountWithBalance(
        account = Account(
            id = AccountId("8a000000-0000-4000-8000-000000000003"),
            userId = UserId("8a000000-0000-4000-8000-000000000002"),
            alias = "Cuenta de pago",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            preset = AccountPreset.BCP,
            initialBalance = Money(12_000L, Currency.PEN),
            openedAt = Instant.parse("2025-01-01T00:00:00Z"),
        ),
        balance = Money(12_000L, Currency.PEN),
    )
}
