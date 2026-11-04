package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickMovementRecurrenceOptionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expenseOptionIsInsideMoreDetailsAndTogglesWithoutSaving() = checkExpenseOption(false)
    @Test fun expenseOptionWorksInDarkTheme() = checkExpenseOption(true)

    private fun checkExpenseOption(dark: Boolean) {
        var optedIn = false
        var saved = false
        compose.setContent {
            KipuTheme(darkTheme = dark) {
                val state = remember { mutableStateOf(QuickMovementUiState(isLoadingCategories = false)) }
                QuickMovementContent(
                    uiState = state.value, onTypeSelected = { state.value = state.value.copy(type = it) },
                    onAmountChanged = {}, onSourceAccountSelected = {}, onSourceCardSelected = {},
                    onDestinationAccountSelected = {}, onCategorySelected = {}, onOpenMerchantPicker = {}, onClearMerchant = {},
                    onNoteChanged = {}, onOpenDatePicker = {}, onToggleMoreDetails = {}, onSave = { saved = true }, onClose = {},
                    accountBalances = emptyMap(), creditCardDebts = emptyMap(), creditCardAvailableCredits = emptyMap(),
                    mostUsedAccountId = null, onNavigateToNewAccount = {}, onCreateSubcategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                    isCreatingSubcategory = false, categoryCreationError = null,
                    onRegisterRecurrenceChanged = { optedIn = it; state.value = state.value.copy(registerRecurrenceAfterSave = it) },
                )
            }
        }
        compose.onNodeWithTag("quick_register_recurrence").assertDoesNotExist()
        compose.onNodeWithText("M\u00e1s detalles").performScrollTo().performClick()
        compose.onNodeWithTag("quick_register_recurrence").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(optedIn); assertTrue(!saved) }
        compose.onNodeWithTag("tab_income").performScrollTo().performClick()
        compose.onNodeWithTag("quick_register_recurrence").assertDoesNotExist()
    }
}
