package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.Composable
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun VoidMovementDialogThemePreview() {
    KipuTheme {
        VoidMovementDialog(
            item = TransactionItem(
                transaction = Transaction(
                    id = "preview-movement",
                    userId = "preview-user",
                    type = MovementType.EXPENSE,
                    amountMinor = 42_500L,
                    currency = "PEN",
                    sourceAccountId = "preview-account",
                    categoryId = "preview-category",
                    occurredAt = 1_760_000_000_000L,
                    status = TransactionStatus.CONFIRMED,
                    syncStatus = MovementSyncStatus.SYNCED,
                ),
                sourceAccountAlias = "Cuenta principal",
                categoryName = "Alimentación",
                merchantName = "Bembos",
            ),
            onDismissRequest = {},
            onConfirmVoid = {},
            currentSourceBalanceMinor = 250_000L,
        )
    }
}
