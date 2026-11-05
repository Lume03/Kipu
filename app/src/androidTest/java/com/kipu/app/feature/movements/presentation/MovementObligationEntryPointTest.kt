package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Rule
import org.junit.Test

/** Entry-point coverage for HU-24; financial effects are delegated to debts and goals. */
class MovementObligationEntryPointTest {
    @get:Rule val compose = createComposeRule()

    private fun show(type: MovementType) {
        compose.setContent { KipuTheme {
            QuickMovementContent(uiState = QuickMovementUiState(type = type), onTypeSelected = {},
                onAmountChanged = {}, onSourceAccountSelected = {}, onSourceCardSelected = {},
                onDestinationAccountSelected = {}, onCategorySelected = {}, onOpenMerchantPicker = {},
                onClearMerchant = {}, onNoteChanged = {}, onOpenDatePicker = {}, onToggleMoreDetails = {},
                onSave = {}, onClose = {}, accountBalances = emptyMap(), creditCardDebts = emptyMap(),
                creditCardAvailableCredits = emptyMap(), mostUsedAccountId = null, onNavigateToNewAccount = {},
                onCreateSubcategory = { _, _, _, _, _ -> error("unused") }, isCreatingSubcategory = false,
                categoryCreationError = null)
        } }
    }

    @Test fun expenseOffersDebtPrincipalApplication() {
        show(MovementType.EXPENSE)
        compose.onNode(hasText("deuda", substring = true, ignoreCase = true) and hasClickAction()).assertExists()
    }

    @Test fun incomeOffersLoanPrincipalCollection() {
        show(MovementType.INCOME)
        compose.onNode(hasText("préstamo", substring = true, ignoreCase = true) and hasClickAction()).assertExists()
    }

    @Test fun transferOffersGoalSelectionBeforeQuotaValidation() {
        show(MovementType.TRANSFER)
        compose.onNode(hasText("meta", substring = true, ignoreCase = true) and hasClickAction()).assertExists()
    }
}
