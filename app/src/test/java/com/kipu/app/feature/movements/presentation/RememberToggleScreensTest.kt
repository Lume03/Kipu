package com.kipu.app.feature.movements.presentation

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.capture.domain.CaptureCandidateRecord
import com.kipu.app.feature.capture.domain.CaptureCandidateStatus
import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.feature.capture.domain.CaptureProvider
import com.kipu.app.feature.capture.presentation.CaptureAutomationStatus
import com.kipu.app.feature.capture.presentation.CaptureRememberAliasOption
import com.kipu.app.feature.capture.presentation.CaptureReviewContent
import com.kipu.app.feature.capture.presentation.CaptureReviewUiState
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Recordar…" switches under the category (and merchant) fields of the three movement forms. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w375dp-h812dp")
class RememberToggleScreensTest {
    @get:Rule val compose = createComposeRule()
    private val option = RememberOption("Recordar Grasa para KFC", checked = true, merchantId = "kfc", categoryId = "grasa")

    @Test fun quickMovementShowsRememberCategoryUnderCategoryAndReportsChanges() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            KipuTheme {
                QuickMovementContent(
                    uiState = QuickMovementUiState(type = MovementType.EXPENSE, isLoadingCategories = false,
                        selectedCategoryId = "grasa", selectedCategoryName = "Grasa", rememberCategory = option),
                    onTypeSelected = {}, onAmountChanged = {}, onSourceAccountSelected = {}, onSourceCardSelected = {},
                    onDestinationAccountSelected = {}, onCategorySelected = {}, onOpenMerchantPicker = {}, onClearMerchant = {},
                    onNoteChanged = {}, onOpenDatePicker = {}, onToggleMoreDetails = {}, onSave = {}, onClose = {},
                    accountBalances = emptyMap(), creditCardDebts = emptyMap(), creditCardAvailableCredits = emptyMap(),
                    mostUsedAccountId = null, onNavigateToNewAccount = {},
                    onCreateSubcategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                    isCreatingSubcategory = false, categoryCreationError = null,
                    onRememberCategoryChanged = { changes += it },
                )
            }
        }
        compose.onNodeWithTag("quick_remember_category").performScrollTo().assertIsOn()
        compose.onNodeWithText("Recordar Grasa para KFC").assertExists()
        compose.onNodeWithText("La próxima vez se elegirá automáticamente").assertExists()
        compose.onNodeWithTag("quick_remember_category").performClick()
        assertEquals(listOf(false), changes)
    }

    @Test fun quickMovementWithoutOptionShowsNoSwitch() {
        compose.setContent {
            KipuTheme {
                QuickMovementContent(
                    uiState = QuickMovementUiState(type = MovementType.EXPENSE, isLoadingCategories = false),
                    onTypeSelected = {}, onAmountChanged = {}, onSourceAccountSelected = {}, onSourceCardSelected = {},
                    onDestinationAccountSelected = {}, onCategorySelected = {}, onOpenMerchantPicker = {}, onClearMerchant = {},
                    onNoteChanged = {}, onOpenDatePicker = {}, onToggleMoreDetails = {}, onSave = {}, onClose = {},
                    accountBalances = emptyMap(), creditCardDebts = emptyMap(), creditCardAvailableCredits = emptyMap(),
                    mostUsedAccountId = null, onNavigateToNewAccount = {},
                    onCreateSubcategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                    isCreatingSubcategory = false, categoryCreationError = null,
                )
            }
        }
        compose.onNodeWithTag("quick_remember_category").assertDoesNotExist()
        compose.onAllNodesWithText("Recordar comercio frecuente").assertCountEquals(0)
    }

    @Test fun editorShowsRememberCategoryAfterCategoryAndMerchant() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            KipuTheme {
                MovementEditorContent(
                    uiState = MovementEditorUiState(isLoading = false, movementType = MovementType.EXPENSE, amountText = "5.00",
                        initialAmountMinor = 500L, selectedCategoryId = "grasa", selectedCategoryName = "Grasa",
                        merchantName = "KFC", selectedMerchantId = "kfc", rememberCategory = option.copy(checked = false),
                        availableCategories = listOf(CategoryOption("grasa", "Grasa", "restaurant", CategoryType.EXPENSE))),
                    onAmountChanged = {}, onSourceAccountSelected = {}, onDestinationAccountSelected = {}, onCategorySelected = {},
                    onMerchantChanged = {}, onDateChanged = {}, onNoteChanged = {}, onSave = {}, onClose = {},
                    onConfirmDiscard = {}, onDismissDiscardDialog = {}, onDiscardConflict = {}, onRedoConflict = {},
                    onRememberCategoryChanged = { changes += it },
                )
            }
        }
        compose.onNodeWithTag("editor_remember_category").performScrollTo().assertIsOff().performClick()
        assertEquals(listOf(true), changes)
    }

    @Test fun captureReviewShowsMerchantAndCategorySwitches() {
        val merchantChanges = mutableListOf<Boolean>()
        val categoryChanges = mutableListOf<Boolean>()
        val candidate = CaptureCandidateRecord(
            id = "c1", ownerId = "owner", sourcePackage = "com.bcp.banking", provider = CaptureProvider.BCP,
            sourceEventFingerprint = "fp", status = CaptureCandidateStatus.PENDING, direction = CaptureDirection.EXPENSE,
            amountMinor = 1725, currency = "PEN", occurredAt = 0L, occurredAtInferred = false,
            merchantSourceText = "Colonial JC 33", merchantId = null, categoryId = null, cardLast4 = null,
            instrumentKind = null, accountId = null, cardId = null, instrumentAmbiguous = false,
            confidence = CaptureConfidenceLevel.MEDIUM, confidenceReasons = emptyList(), creditMode = null,
            installmentCount = null, resolvedTransactionId = null, resolutionKey = null, evidenceSnippet = null,
            evidenceExpiresAt = null, userCorrectedFields = emptyList(), createdAt = 0L, updatedAt = 0L, resolvedAt = null,
        )
        compose.setContent {
            KipuTheme {
                CaptureReviewContent(
                    state = CaptureReviewUiState(isLoading = false, candidate = candidate, automation = CaptureAutomationStatus.ACTIVE,
                        amountText = "17.25", currency = "PEN", occurredAt = 0L, merchantId = "tambo", categoryId = "grasa",
                        rememberMerchant = CaptureRememberAliasOption("Recordar Tambo para 'Colonial JC 33'", true, "tambo", "Colonial JC 33"),
                        rememberCategory = option.copy(label = "Recordar Grasa para Tambo", merchantId = "tambo")),
                    onBack = {}, onDirection = {}, onAmount = {}, onCurrency = {}, onDate = {}, onMerchant = {}, onCategory = {},
                    onInstrument = {}, onCreditMode = {}, onInstallments = {}, onConfirm = {}, onConfirmDespiteSimilar = {},
                    onDismissSimilar = {}, onConvertToManual = {}, onDiscard = {}, onOpenPermissions = {},
                    merchantPickerState = MerchantPickerState(), onMerchantQueryChanged = {},
                    onRememberMerchant = { merchantChanges += it }, onRememberCategory = { categoryChanges += it },
                )
            }
        }
        compose.onNodeWithText("Recordar Tambo para 'Colonial JC 33'").assertExists()
        compose.onNodeWithTag("capture_remember_merchant").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("capture_remember_category").performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(false), merchantChanges)
        assertEquals(listOf(false), categoryChanges)
    }
}
