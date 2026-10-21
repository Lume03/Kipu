package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.ui.component.MoneyText

@Composable
fun GoalBackingStatus(
    accounts: List<GoalBackingAccount>,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Respaldo real", style = MaterialTheme.typography.titleMedium)
        if (accounts.isEmpty()) {
            Text("No hay cuentas activas compatibles para respaldar esta meta.", style = MaterialTheme.typography.bodySmall)
        }
        accounts.forEach { account ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                border = BorderStroke(1.dp, colors.borderSubtle),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(account.name, style = MaterialTheme.typography.titleSmall)
                    AmountRow("Saldo confirmado", account.confirmedBalance)
                    AmountRow("Reservado para metas", account.reserved)
                    AmountRow("Libre para reservar", account.available)
                    if (account.shortfall.minorUnits > 0L) {
                        AmountRow("Faltante de respaldo", account.shortfall, MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Text(
            "Las reservas son virtuales: no duplican el patrimonio ni cambian el saldo real.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AmountRow(label: String, money: Money, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
