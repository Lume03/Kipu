package com.kipu.app.feature.movements.presentation

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Acceptance test verifying the UI editor behavior for the OCR receipt draft flow (e.g. Yape).
 * Validates prefilled draft rendering, inline corrections, manual fallback for unparsed amounts,
 * and ensures confirmation callbacks only dispatch upon explicit user confirmation.
 */
class ReceiptMovementEditorAcceptanceTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        autoGrantRequiredPermissions()
    }

    private fun autoGrantRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                )
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            } catch (_: Exception) {
                // Self-granting via UiAutomation when running on supported devices
            }
        }
    }

    private fun createTestAccount(): Account {
        return Account(
            id = AccountId.generate(),
            userId = UserId.generate(),
            alias = "Cuenta principal PEN",
            type = AccountType.SAVINGS,
            currency = Currency.PEN,
            initialBalance = Money(100_000L, Currency.PEN),
            openedAt = Instant.parse("2026-01-01T10:00:00Z"),
        )
    }

    private val testCategory = CategoryOption(
        id = "acceptance-food",
        name = "Alimentación",
        icon = "restaurant",
        categoryType = CategoryType.EXPENSE,
    )

    @Composable
    private fun QuickMovementEditorHost(
        initialState: QuickMovementUiState,
        onSaveCallback: (QuickMovementUiState) -> Unit,
    ) {
        val state = remember { mutableStateOf(initialState) }
        QuickMovementContent(
            uiState = state.value,
            onTypeSelected = { type -> state.value = state.value.copy(type = type) },
            onAmountChanged = { amount -> state.value = state.value.copy(amountText = amount) },
            onSourceAccountSelected = { id -> state.value = state.value.copy(selectedSourceAccountId = id) },
            onSourceCardSelected = {},
            onDestinationAccountSelected = { id -> state.value = state.value.copy(selectedDestinationAccountId = id) },
            onCategorySelected = { category ->
                state.value = state.value.copy(
                    selectedCategoryId = category.id,
                    selectedCategoryName = category.displayName,
                )
            },
            onOpenMerchantPicker = {},
            onClearMerchant = { state.value = state.value.copy(merchantName = "", merchantProvisionalText = null) },
            onNoteChanged = { note -> state.value = state.value.copy(note = note) },
            onOpenDatePicker = {},
            onToggleMoreDetails = {
                state.value = state.value.copy(isMoreDetailsExpanded = !state.value.isMoreDetailsExpanded)
            },
            onSave = { onSaveCallback(state.value) },
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

    @Test
    fun prefilledOcrCandidateCanBeCorrectedAndConfirmedWithCallback() {
        val account = createTestAccount()
        val fixedOccurredAt = Instant.parse("2026-03-24T12:00:00Z").toEpochMilli()

        val initialState = QuickMovementUiState(
            type = MovementType.EXPENSE,
            amountText = "12.50",
            currency = "PEN",
            merchantName = "Ana",
            merchantProvisionalText = "Ana",
            occurredAt = fixedOccurredAt,
            selectedSourceAccountId = account.id.value,
            availableAccounts = listOf(account),
            availableCategories = listOf(testCategory),
        )

        var callbackCount = 0
        var savedState: QuickMovementUiState? = null

        compose.setContent {
            KipuTheme {
                QuickMovementEditorHost(
                    initialState = initialState,
                    onSaveCallback = { state ->
                        callbackCount++
                        savedState = state
                    },
                )
            }
        }

        // Verify initial candidate values are prefilled and displayed
        compose.onNodeWithTag("input_amount").assertTextContains("12.50")
        compose.onNodeWithText("Ana").assertIsDisplayed()
        assertEquals("Callback must not occur prior to confirmation", 0, callbackCount)
        assertNull("No data should be dispatched before user confirmation", savedState)

        // User corrects amount from 12.50 to 15.20
        compose.onNodeWithTag("input_amount").performTextReplacement("15.20")
        compose.onNodeWithTag("input_amount").assertTextContains("15.20")

        // User selects category
        compose.onNodeWithTag("category_picker_open").performScrollTo().performClick()
        compose.onNodeWithTag("category_root_acceptance-food").performClick()
        compose.waitForIdle()

        // Ensure callback STILL has not occurred before confirming
        assertEquals("Callback must not occur while editing fields", 0, callbackCount)
        assertNull("Saved state must remain null before clicking confirm", savedState)

        // User confirms transaction
        compose.onNodeWithTag("btn_save_transaction").performClick()
        compose.waitForIdle()

        // Verify callback occurred exactly once with the corrected values
        assertEquals("Callback should execute exactly once upon save", 1, callbackCount)
        val result = requireNotNull(savedState) { "Saved state must be captured on confirmation" }
        assertEquals("15.20", result.amountText)
        assertEquals("acceptance-food", result.selectedCategoryId)
        assertEquals("Ana", result.merchantName)
        assertEquals(account.id.value, result.selectedSourceAccountId)
        assertEquals(fixedOccurredAt, result.occurredAt)
    }

    @Test
    fun illegibleOcrCandidateWithEmptyAmountAllowsManualEntryAndConfirmation() {
        val account = createTestAccount()
        val fixedOccurredAt = Instant.parse("2026-03-24T12:00:00Z").toEpochMilli()

        // Exception candidate: amount was illegible in OCR receipt, so amountText is empty
        val initialState = QuickMovementUiState(
            type = MovementType.EXPENSE,
            amountText = "",
            currency = "PEN",
            merchantName = "Ana",
            merchantProvisionalText = "Ana",
            occurredAt = fixedOccurredAt,
            selectedSourceAccountId = account.id.value,
            availableAccounts = listOf(account),
            availableCategories = listOf(testCategory),
        )

        var callbackCount = 0
        var savedState: QuickMovementUiState? = null

        compose.setContent {
            KipuTheme {
                QuickMovementEditorHost(
                    initialState = initialState,
                    onSaveCallback = { state ->
                        callbackCount++
                        savedState = state
                    },
                )
            }
        }

        // Amount starts empty; callback has not occurred
        assertEquals("Callback must not occur on initial rendering", 0, callbackCount)
        assertNull("No data dispatched initially", savedState)

        // User manually types amount
        compose.onNodeWithTag("input_amount").performTextInput("15.20")
        compose.onNodeWithTag("input_amount").assertTextContains("15.20")

        // User selects category
        compose.onNodeWithTag("category_picker_open").performScrollTo().performClick()
        compose.onNodeWithTag("category_root_acceptance-food").performClick()
        compose.waitForIdle()

        // Ensure callback still did not occur before confirming
        assertEquals("Callback must not trigger before save button is pressed", 0, callbackCount)
        assertNull(savedState)

        // User confirms transaction
        compose.onNodeWithTag("btn_save_transaction").performClick()
        compose.waitForIdle()

        // Verify callback executed with manual input
        assertEquals(1, callbackCount)
        val result = requireNotNull(savedState) { "Manual entry must produce captured state on confirmation" }
        assertEquals("15.20", result.amountText)
        assertEquals("acceptance-food", result.selectedCategoryId)
        assertEquals("Ana", result.merchantName)
        assertEquals(account.id.value, result.selectedSourceAccountId)
        assertEquals(fixedOccurredAt, result.occurredAt)
    }
}
