package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.Composable
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

/** Presentation fixtures only: callbacks never persist or authorize an operation. */
private fun filtersPreviewState(advanced: Boolean = false, invalidAmount: Boolean = false) = MovementHistoryUiState(
    ownerId = "theme-preview",
    isLoading = false,
    accessStatus = if (advanced) MovementHistoryAccessDecision.Allowed
        else MovementHistoryAccessDecision.PremiumRequired(MovementHistoryQuery()),
    allTransactions = listOf(TransactionItem(Transaction(
        id = "preview-expense", userId = "theme-preview", type = MovementType.EXPENSE,
        amountMinor = 1500, currency = "PEN", sourceAccountId = "preview-account",
        categoryId = "preview-category", occurredAt = 1_758_000_000_000,
        status = TransactionStatus.ACTIVE,
    ), sourceAccountAlias = "Efectivo", categoryName = "Alimentación")),
    appliedFilters = if (invalidAmount) AdvancedFiltersState(
        minAmountMinor = 10000, maxAmountMinor = 5000, accountIds = setOf("preview-account"),
    ) else AdvancedFiltersState(),
)

@KipuThemePreviews
@Composable
private fun FreeFiltersPreview() = KipuTheme {
    MovementFiltersSheet(filtersPreviewState(), {}, { it.validate() })
}

@KipuThemePreviews
@Composable
private fun AdvancedFiltersPreview() = KipuTheme {
    MovementFiltersSheet(filtersPreviewState(advanced = true), {}, { it.validate() })
}

@KipuThemePreviews
@Composable
private fun InvalidAmountFiltersPreview() = KipuTheme {
    // Use interactive Preview's Apply action to display the real inline validation.
    MovementFiltersSheet(filtersPreviewState(advanced = true, invalidAmount = true), {}, { it.validate() })
}

@KipuThemePreviews
@Composable
private fun ReferenceSelectorPreview() = KipuTheme {
    ReferenceSelector("Cuentas", listOf(MovementReferenceOption("cash", "Efectivo")),
        selected = setOf("cash"), enabled = true, onChange = {})
}

@KipuThemePreviews
@Composable
private fun MovementDetailThemePreview() = KipuTheme {
    MovementDetailSheet(filtersPreviewState().allTransactions.first(), emptyList(),
        loading = false, error = false, onDismiss = {}, onEdit = {}, onVoid = {})
}

@KipuThemePreviews
@Composable
private fun MovementEditorThemePreview() = KipuTheme {
    MovementEditorContent(MovementEditorUiState(isLoading = false, amountText = "15.00"),
        onAmountChanged = {}, onSourceAccountSelected = {}, onDestinationAccountSelected = {},
        onCategorySelected = {}, onMerchantChanged = {}, onDateChanged = {}, onNoteChanged = {},
        onSave = {}, onClose = {}, onConfirmDiscard = {}, onDismissDiscardDialog = {},
        onDiscardConflict = {}, onRedoConflict = {})
}

@KipuThemePreviews
@Composable
private fun QuickMovementThemePreview() = KipuTheme {
    QuickMovementContent(QuickMovementUiState(amountText = "15.00", isLoadingCategories = false),
        onTypeSelected = {}, onAmountChanged = {}, onSourceAccountSelected = {},
        onSourceCardSelected = {}, onDestinationAccountSelected = {}, onCategorySelected = {},
        onOpenMerchantPicker = {}, onClearMerchant = {}, onNoteChanged = {}, onOpenDatePicker = {},
        onToggleMoreDetails = {}, onSave = {}, onClose = {}, accountBalances = emptyMap(),
        creditCardDebts = emptyMap(), creditCardAvailableCredits = emptyMap(), mostUsedAccountId = null,
        onNavigateToNewAccount = {}, onCreateSubcategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException("Preview")) },
        isCreatingSubcategory = false, categoryCreationError = null)
}
