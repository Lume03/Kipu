package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class QuickMovementScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun typeTabsShowConditionalFieldsAndAccessibleSaveAction() {
        val initialState = QuickMovementUiState(
            availableCategories = listOf(
                CategoryOption("expense-food", "Alimentación", "restaurant", CategoryType.EXPENSE),
                CategoryOption("expense-home", "Hogar", "home", CategoryType.EXPENSE),
                CategoryOption("expense-home-2", "Hogar", "home", CategoryType.EXPENSE),
                CategoryOption(
                    "expense-rent",
                    "Alquiler",
                    "home",
                    CategoryType.EXPENSE,
                    parentCategoryId = "expense-home",
                    parentName = "Hogar",
                ),
                CategoryOption(
                    "expense-rent-2",
                    "Hipoteca",
                    "home",
                    CategoryType.EXPENSE,
                    parentCategoryId = "expense-home-2",
                    parentName = "Hogar",
                ),
                CategoryOption("income-salary", "Salario", "work", CategoryType.INCOME),
            ),
        )

        compose.setContent {
            KipuTheme {
                val state = remember { mutableStateOf(initialState) }
                QuickMovementContent(
                    uiState = state.value,
                    onTypeSelected = { type -> state.value = state.value.copy(type = type) },
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onSourceCardSelected = {},
                    onDestinationAccountSelected = {},
                    onCategorySelected = { category ->
                        state.value = state.value.copy(
                            selectedCategoryId = category.id,
                            selectedCategoryName = category.displayName,
                        )
                    },
                    onOpenMerchantPicker = {},
                    onClearMerchant = {},
                    onNoteChanged = {},
                    onOpenDatePicker = {},
                    onToggleMoreDetails = {},
                    onSave = {},
                    onClose = {},
                    accountBalances = emptyMap(),
                    creditCardDebts = emptyMap(),
                    creditCardAvailableCredits = emptyMap(),
                    mostUsedAccountId = null,
                    onNavigateToNewAccount = {},
                    onCreateSubcategory = { _, _, _, _, _ ->
                        Result.failure(UnsupportedOperationException("Subcategory creation is not part of this test"))
                    },
                    isCreatingSubcategory = false,
                    categoryCreationError = null,
                )
            }
        }

        compose.onNodeWithTag("selector_source_account").assertIsDisplayed()
        compose.onNodeWithTag("selector_destination_account").assertDoesNotExist()
        compose.onNodeWithText("Categoría *").assertIsDisplayed()
        compose.onNodeWithTag("category_picker_open").assertIsDisplayed().performClick()
        compose.onNodeWithTag("category_root_expense-home").assertIsDisplayed()
        compose.onNodeWithTag("category_subcategory_expense-rent").assertDoesNotExist()
        compose.onNodeWithTag("category_expand_expense-home").assertHasClickAction().performClick()
        compose.onNodeWithTag("category_subcategory_expense-rent").assertIsDisplayed().performClick()
        compose.onNodeWithTag("category_subcategory_expense-rent-2").assertDoesNotExist()
        compose.onNodeWithText("Hogar > Alquiler").assertIsDisplayed()

        compose.onNodeWithTag("tab_income").performClick()
        compose.onNodeWithText("Categoría (opcional)").assertIsDisplayed()
        compose.onNodeWithTag("selector_destination_account").assertDoesNotExist()

        compose.onNodeWithTag("tab_transfer").performClick()
        compose.onNodeWithTag("selector_source_account").assertIsDisplayed()
        compose.onNodeWithTag("selector_destination_account").assertIsDisplayed()
        compose.onNodeWithText("Categoría *").assertDoesNotExist()
        compose.onNodeWithTag("category_picker_open").assertDoesNotExist()

        val saveBounds = compose.onNodeWithTag("btn_save_transaction")
            .assertHasClickAction()
            .fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            assertTrue("Save action height must meet the 48dp touch target", saveBounds.height.toDp() >= 48.dp)
        }
    }

    @Test
    fun eachMovementTabHasAnAccessibleClickTarget() {
        compose.setContent {
            KipuTheme {
                QuickMovementContent(
                    uiState = QuickMovementUiState(),
                    onTypeSelected = {},
                    onAmountChanged = {},
                    onSourceAccountSelected = {},
                    onSourceCardSelected = {},
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
                    creditCardDebts = emptyMap(),
                    creditCardAvailableCredits = emptyMap(),
                    mostUsedAccountId = null,
                    onNavigateToNewAccount = {},
                    onCreateSubcategory = { _, _, _, _, _ ->
                        Result.failure(UnsupportedOperationException("Subcategory creation is not part of this test"))
                    },
                    isCreatingSubcategory = false,
                    categoryCreationError = null,
                )
            }
        }

        listOf(MovementType.EXPENSE, MovementType.INCOME, MovementType.TRANSFER).forEach { type ->
            compose.onNodeWithTag("tab_${type.name.lowercase()}").assertHasClickAction()
        }
    }

    @Test
    fun currencyControlsOnlyShowAvailableCurrenciesAndFilterInstrumentPicker() {
        val penAccount = account("Cuenta PEN", Currency.PEN)
        val stateHolder = mutableStateOf(
            QuickMovementUiState(
                availableAccounts = listOf(penAccount),
                selectedSourceAccountId = penAccount.id.value,
            ),
        )
        compose.setContent {
            KipuTheme {
                val state = remember { stateHolder }
                QuickMovementContentForTest(
                    state = state.value,
                )
            }
        }

        compose.onNodeWithTag("currency_single_option").assertIsDisplayed()
        compose.onNodeWithTag("currency_usd").assertDoesNotExist()

        val usdAccount = account("Cuenta USD", Currency.USD)
        stateHolder.value = stateHolder.value.copy(availableAccounts = listOf(penAccount, usdAccount))

        compose.onNodeWithTag("currency_pen").assertIsDisplayed()
        compose.onNodeWithTag("currency_usd").assertIsDisplayed().performClick()
        compose.onNodeWithText("Elige un instrumento en USD").assertIsDisplayed()
        compose.onNodeWithTag("account_item_${usdAccount.id.value}").assertIsDisplayed()
        compose.onNodeWithTag("account_item_${penAccount.id.value}").assertDoesNotExist()
        compose.onNodeWithTag("btn_confirm_selection").assertIsNotEnabled()
    }

    @Test
    fun transferWithoutEligibleDestinationExplainsRecoveryAction() {
        val onlyAccount = account("Cuenta origen", Currency.PEN)
        var recoveryRequested = false
        compose.setContent {
            KipuTheme {
                QuickMovementContentForTest(
                    state = QuickMovementUiState(
                        type = MovementType.TRANSFER,
                        availableAccounts = listOf(onlyAccount),
                        selectedSourceAccountId = onlyAccount.id.value,
                    ),
                    onNavigateToNewAccount = { recoveryRequested = true },
                )
            }
        }

        compose.onNodeWithText("Sin cuenta destino disponible").assertIsDisplayed()
        compose.onNodeWithText("Se requiere otra cuenta distinta en PEN").assertIsDisplayed()
        compose.onNodeWithTag("btn_create_destination_account").assertIsDisplayed().performClick()
        assertTrue("Recovery action should open account creation", recoveryRequested)
    }

    @Test
    fun merchantAndDateAreProgressivelyDisclosedAsOptionalDetails() {
        compose.setContent {
            KipuTheme {
                QuickMovementContentForTest(state = QuickMovementUiState())
            }
        }

        compose.onNodeWithText("Más detalles (opcional)").assertIsDisplayed()
        compose.onNodeWithTag("input_merchant").assertDoesNotExist()
        compose.onNodeWithTag("selector_occurred_at").assertDoesNotExist()
        compose.onNodeWithText("+ Nota (opcional)").assertIsDisplayed()
        compose.onNodeWithTag("input_note").assertDoesNotExist()

        compose.onNodeWithText("Más detalles (opcional)").performClick()
        compose.onNodeWithText("Agregar comercio (opcional)").assertIsDisplayed()
        compose.onNodeWithTag("selector_occurred_at").assertIsDisplayed()
    }

    private fun account(alias: String, currency: Currency): Account = Account(
        id = AccountId.generate(),
        userId = UserId.generate(),
        alias = alias,
        type = AccountType.SAVINGS,
        currency = currency,
        initialBalance = Money(100_000L, currency),
        openedAt = Instant.parse("2026-10-01T12:00:00Z"),
    )

    @androidx.compose.runtime.Composable
    private fun QuickMovementContentForTest(
        state: QuickMovementUiState,
        onNavigateToNewAccount: () -> Unit = {},
    ) {
        QuickMovementContent(
            uiState = state,
            onTypeSelected = {},
            onAmountChanged = {},
            onSourceAccountSelected = {},
            onSourceCardSelected = {},
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
            creditCardDebts = emptyMap(),
            creditCardAvailableCredits = emptyMap(),
            mostUsedAccountId = null,
            onNavigateToNewAccount = onNavigateToNewAccount,
            onCreateSubcategory = { _, _, _, _, _ ->
                Result.failure(UnsupportedOperationException("Subcategory creation is not part of this test"))
            },
            isCreatingSubcategory = false,
            categoryCreationError = null,
        )
    }
}
