package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.runtime.Composable
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews
import java.time.Instant

@KipuThemePreviews
@Composable
private fun AccountDetailThemePreview() = KipuTheme {
    AccountDetailContent(
        account = Account(AccountId("e3900000-0000-4000-8000-000000000001"), UserId("e3900000-0000-4000-8000-000000000002"), alias = "Ahorro de ejemplo",
            type = AccountType.SAVINGS, currency = Currency.PEN, preset = AccountPreset.YAPE,
            initialBalance = Money(25000, Currency.PEN), openedAt = Instant.parse("2026-10-01T00:00:00Z")),
        card = null, currentBalance = Money(25000, Currency.PEN), creditCardSummary = null,
        isLoading = false, onNavigateBack = {}, onSaveAppearance = { _, _ -> },
        onCorrectOpeningBalance = {}, onArchive = {}, onReactivate = {},
    )
}
