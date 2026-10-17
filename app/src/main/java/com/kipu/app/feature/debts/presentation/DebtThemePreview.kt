package com.kipu.app.feature.debts.presentation

import androidx.compose.runtime.Composable
import com.kipu.app.feature.debts.domain.model.*
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews
import java.time.LocalDate

private fun debtPreview() = DebtSummary(
    debtId = "preview-debt", userId = "preview-owner", obligationType = DebtObligationType.PAYABLE,
    counterpartyName = "Préstamo de ejemplo", principalMinor = 30000, remainingPrincipalMinor = 20000,
    currencyCode = "PEN", openedOn = LocalDate.of(2026, 10, 1), dueDate = LocalDate.of(2026, 11, 1),
    reminderLeadDays = 3, notes = "Datos ficticios", status = DebtLifecycleStatus.ACTIVE,
    syncState = "SYNCED", revision = 1,
)

@KipuThemePreviews
@Composable
private fun DebtListThemePreview() = KipuTheme {
    DebtListScreen(listOf(debtPreview()), null, {}, {}, {}, {})
}

@KipuThemePreviews
@Composable
private fun DebtDetailThemePreview() = KipuTheme {
    DebtDetailScreen(debtPreview(), hasFinancialHistory = true, onNavigateBack = {}, onEdit = {}, onDelete = {})
}

@KipuThemePreviews
@Composable
private fun DebtScheduleThemePreview() = KipuTheme {
    DebtScheduleScreen(DebtScheduleUiState(debt = debtPreview(), isLoading = false),
        onInstallmentCountChange = {}, onFirstDueDateChange = {}, onReminderLeadDaysChange = {},
        onSchedule = {}, onCancelSchedule = {}, onCloseDebt = { _, _ -> }, onNavigateBack = {})
}

@KipuThemePreviews
@Composable
private fun DebtSettlementThemePreview() = KipuTheme {
    DebtSettlementSheet(debtPreview(), DebtSettlementUiState(principalAmount = "100", isLoading = false),
        onPrincipalAmountChange = {}, onInterestAmountChange = {}, onAccountSelected = {},
        onInterestCategorySelected = {}, onSave = {}, onDismiss = {})
}
