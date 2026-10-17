package com.kipu.app.feature.movements.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.FinancialTerminology
import com.kipu.app.feature.accounts.presentation.detail.AccountDetailContent
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class Issue48ProductionCreditLabelsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun movementEntryOpensProductionCardSelectorWithVisibleAndAccessibleCreditLabels() {
        val card = creditCard()
        var chosenCardId: String? = null
        val debt = Money(142_000, Currency.PEN)
        val available = Money(258_000, Currency.PEN)

        compose.setContent {
            KipuTheme {
                QuickMovementContent(
                    uiState = QuickMovementUiState(
                        availableCreditCards = listOf(card),
                        isLoadingCategories = false,
                    ),
                    onTypeSelected = {},
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onSourceCardSelected = { chosenCardId = it },
                    onDestinationAccountSelected = {},
                    onCategorySelected = {},
                    onOpenMerchantPicker = {},
                    onClearMerchant = {},
                    onNoteChanged = {},
                    onOpenDatePicker = {},
                    onToggleMoreDetails = {},
                    onSave = {},
                    onClose = {},
                    accountBalances = emptyMap(),
                    creditCardDebts = mapOf(card.id.value to debt),
                    creditCardAvailableCredits = mapOf(card.id.value to available),
                    mostUsedAccountId = null,
                    onNavigateToNewAccount = {},
                    onCreateSubcategory = { _, _, _, _, _ ->
                        Result.failure(UnsupportedOperationException("Not used by this label check"))
                    },
                    isCreatingSubcategory = false,
                    categoryCreationError = null,
                )
            }
        }

        compose.onNodeWithTag("selector_source_account").performClick()

        val cardRow = compose.onNodeWithTag("card_item_${card.id.value}")
        cardRow.assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Créd. disp.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Usado:", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Crédito disponible, no es dinero propio:",
            substring = true,
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Deuda:", substring = true).assertIsDisplayed()

        cardRow.performClick()
        compose.onNodeWithTag("btn_confirm_selection").performClick()

        compose.runOnIdle { assertEquals(card.id.value, chosenCardId) }
    }

    @Test
    fun creditCardDetailShowsAndExposesTheProductionFinancialLabels() {
        val card = creditCard()

        compose.setContent {
            KipuTheme {
                AccountDetailContent(
                    account = null,
                    card = card,
                    currentBalance = null,
                    creditCardSummary = CreditCardWithSummary(
                        card = card,
                        debt = Money(142_000, Currency.PEN),
                        availableCredit = Money(258_000, Currency.PEN),
                        utilizationPercentage = 35.5,
                    ),
                    isLoading = false,
                    onNavigateBack = {},
                    onSaveAppearance = { _, _ -> },
                    onCorrectOpeningBalance = {},
                    onArchive = {},
                    onReactivate = {},
                )
            }
        }

        compose.onNodeWithText("Deuda actual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(FinancialTerminology.AVAILABLE_CREDIT_NOT_OWN_MONEY)
            .performScrollTo()
            .assertIsDisplayed()

        val visibleCreditLine = compose.onAllNodesWithText(
            FinancialTerminology.TOTAL_CREDIT_LINE,
            substring = true,
            ignoreCase = true,
        )
        visibleCreditLine[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "línea total de crédito",
            substring = true,
        ).assertIsDisplayed()
    }

    private fun creditCard() = CreditCard(
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
}
