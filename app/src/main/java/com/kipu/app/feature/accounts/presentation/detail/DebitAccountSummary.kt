package com.kipu.app.feature.accounts.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.DebitCard
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.components.CardStyleType
import com.kipu.app.feature.accounts.presentation.components.InstrumentCardPreview
import com.kipu.app.feature.accounts.presentation.components.accountSurfaceColor
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Debit is a reference to the linked account's money, never a separate balance. */
@Composable
internal fun DebitAccountSummary(
    card: DebitCard,
    account: Account?,
    balance: Money?,
    onOpenAccount: () -> Unit,
    onMovements: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val style = CardStylePresets.byId(card.stylePresetId)?.takeIf { it.cardType == CardStyleType.DEBIT }
        ?: CardStylePresets.forInstitution(card.issuer, CardStyleType.DEBIT).firstOrNull()
        ?: CardStylePresets.genericDebitStyle
    InstrumentCardPreview(
        title = card.alias ?: card.issuer,
        instrumentType = "Débito",
        subtitle = "${card.issuer} · ${card.currency.name}",
        lastFourDigits = card.lastFourDigits,
        network = card.network.name,
        stylePreset = style,
    )
    if (account != null) {
        Surface(color = accountSurfaceColor(account), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Saldo de cuenta vinculada", style = MaterialTheme.typography.labelLarge)
                if (balance != null) MoneyText(balance, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(account.alias, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenAccount,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryText),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Ver y editar cuenta")
                }
            }
        }
        AccountQuickActions(null, null, onMovements)
    }
}
