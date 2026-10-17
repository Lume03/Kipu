package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.runtime.Composable
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun CreditCardSummaryThemePreview() {
    KipuTheme {
        CreditCardSummaryCard(
            creditCardWithSummary = CreditCardWithSummary(
                card = CreditCard(
                    id = CardId("00000000-0000-0000-0000-000000000055"),
                    userId = UserId("00000000-0000-0000-0000-000000000039"),
                    alias = "Visa Oro",
                    issuer = "Banco de ejemplo",
                    network = CardNetwork.VISA,
                    lastFourDigits = "1234",
                    currency = Currency.PEN,
                    creditLimitMinorUnits = 1_000_000L,
                    billingDay = 10,
                    dueDay = 20,
                ),
                debt = Money(640_000L, Currency.PEN),
                availableCredit = Money(360_000L, Currency.PEN),
                utilizationPercentage = 64.0,
            ),
        )
    }
}
