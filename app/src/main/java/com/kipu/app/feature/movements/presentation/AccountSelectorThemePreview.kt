package com.kipu.app.feature.movements.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.rememberKipuColors

@Composable
private fun AccountSelectorThemePreviewContent() {
    val colors = rememberKipuColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Seleccionar cuenta", color = colors.inkPrimary, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipPreview("Todas (4)", selected = true)
            FilterChipPreview("Cuentas (2)", selected = false)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = colors.surfaceVariant,
        ) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cuenta de sueldo · Principal", color = colors.inkPrimary)
                Text("S/ 3,850.00", color = colors.inkPrimary)
            }
        }
        Surface(shape = RoundedCornerShape(8.dp), color = if (colors.isDark) colors.surfaceVariant else colors.positiveContainer) {
            Text("Deuda S/ 1,420.00", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = colors.debt)
        }
    }
}

@Composable
private fun FilterChipPreview(label: String, selected: Boolean) {
    val colors = rememberKipuColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) colors.primary else colors.surfaceVariant,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) colors.onPrimary else colors.inkSecondary,
        )
    }
}

@Preview(name = "Selector de cuenta · Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun AccountSelectorLightPreview() {
    KipuTheme(darkTheme = false) { AccountSelectorThemePreviewContent() }
}

@Preview(name = "Selector de cuenta · Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AccountSelectorDarkPreview() {
    KipuTheme(darkTheme = true) { AccountSelectorThemePreviewContent() }
}

@com.kipu.app.ui.theme.KipuThemePreviews
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ActualAccountSelectorThemePreview() = KipuTheme {
    val account = com.kipu.app.feature.accounts.domain.model.Account(
        id = com.kipu.app.core.finance.domain.model.AccountId("e3900000-0000-4000-8000-000000000001"),
        userId = com.kipu.app.core.finance.domain.model.UserId("e3900000-0000-4000-8000-000000000002"),
        alias = "Efectivo de ejemplo", type = com.kipu.app.feature.accounts.domain.model.AccountType.CASH,
        currency = com.kipu.app.core.finance.domain.model.Currency.PEN,
        initialBalance = com.kipu.app.core.finance.domain.model.Money(25000, com.kipu.app.core.finance.domain.model.Currency.PEN),
        openedAt = java.time.Instant.parse("2026-10-01T00:00:00Z"),
    )
    AccountSelectorBottomSheet({}, listOf(account), selectedAccountId = account.id.value)
}

/** Brand logos, an affiliated Yape sharing the BCP balance, a card and a balance not available. */
@com.kipu.app.ui.theme.KipuThemePreviews
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BrandAccountSelectorThemePreview() = KipuTheme {
    val pen = com.kipu.app.core.finance.domain.model.Currency.PEN
    val user = com.kipu.app.core.finance.domain.model.UserId("e3900000-0000-4000-8000-000000000002")
    fun account(
        n: Int,
        alias: String,
        type: com.kipu.app.feature.accounts.domain.model.AccountType,
        preset: com.kipu.app.feature.accounts.domain.model.AccountPreset?,
        linked: String? = null,
    ) = com.kipu.app.feature.accounts.domain.model.Account(
        id = com.kipu.app.core.finance.domain.model.AccountId("e3900000-0000-4000-8000-00000000001$n"),
        userId = user, alias = alias, type = type, currency = pen, preset = preset,
        initialBalance = com.kipu.app.core.finance.domain.model.Money(0, pen),
        openedAt = java.time.Instant.parse("2026-10-01T00:00:00Z"),
        linkedAccountId = linked?.let { com.kipu.app.core.finance.domain.model.AccountId(it) },
    )
    val bcp = account(1, "Cuenta BCP", com.kipu.app.feature.accounts.domain.model.AccountType.SAVINGS, com.kipu.app.feature.accounts.domain.model.AccountPreset.BCP)
    val yape = account(2, "Yape", com.kipu.app.feature.accounts.domain.model.AccountType.DIGITAL_WALLET, com.kipu.app.feature.accounts.domain.model.AccountPreset.YAPE, bcp.id.value)
    val bbva = account(3, "BBVA", com.kipu.app.feature.accounts.domain.model.AccountType.SAVINGS, com.kipu.app.feature.accounts.domain.model.AccountPreset.BBVA)
    val cash = account(4, "Efectivo", com.kipu.app.feature.accounts.domain.model.AccountType.CASH, com.kipu.app.feature.accounts.domain.model.AccountPreset.CASH)
    AccountSelectorBottomSheet(
        onDismissRequest = {},
        accounts = listOf(bcp, yape, bbva, cash),
        selectedAccountId = bcp.id.value,
        mostUsedAccountId = bcp.id.value,
        accountBalances = mapOf(
            bcp.id.value to com.kipu.app.core.finance.domain.model.Money(99_770, pen),
            cash.id.value to com.kipu.app.core.finance.domain.model.Money(10_000, pen),
        ),
        balancesLoaded = true,
        purpose = AccountSelectorPurpose.INCOME_DESTINATION,
        title = AccountSelectorDefaults.title(AccountSelectorPurpose.INCOME_DESTINATION),
        subtitle = AccountSelectorDefaults.subtitle(AccountSelectorPurpose.INCOME_DESTINATION),
    )
}
