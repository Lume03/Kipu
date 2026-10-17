package com.kipu.app.feature.movements.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class AccountSelectorBottomSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun creditCardRowShowsAvailableCreditCopyAndAccessibleMeaning() {
        val card = CreditCard(
            id = CardId.generate(),
            userId = UserId.generate(),
            alias = "Principal",
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "4821",
            currency = Currency.PEN,
            creditLimitMinorUnits = 400_000,
            billingDay = 15,
            dueDay = 5,
        )

        compose.setContent {
            KipuTheme {
                AccountSelectorBottomSheet(
                    onDismissRequest = {},
                    accounts = emptyList(),
                    creditCards = listOf(card),
                    cardDebts = mapOf(card.id.value to Money(142_000, Currency.PEN)),
                    cardAvailableCredits = mapOf(card.id.value to Money(258_000, Currency.PEN)),
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                )
            }
        }

        compose.onNodeWithText("Créd. disp.:", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Crédito disponible, no es dinero propio:",
            substring = true,
        ).assertIsDisplayed()
    }
}
