package com.kipu.app.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.home.domain.CurrencyNetWorth
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.MoneyText

internal const val NET_WORTH_CARD_TAG = "home_net_worth_card"

/**
 * Resumen patrimonial de Inicio. Los importes pasan por [MoneyText], así que la máscara de
 * privacidad solo oculta la presentación sin alterar cálculos.
 */
@Composable
fun NetWorthCard(
    state: NetWorthUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KipuCard(modifier = modifier.fillMaxWidth().testTag(NET_WORTH_CARD_TAG)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Patrimonio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            when (state) {
                NetWorthUiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.semantics { contentDescription = "Calculando patrimonio" },
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Calculando…", style = MaterialTheme.typography.bodyMedium)
                }
                NetWorthUiState.Empty -> Text(
                    text = "Sin datos todavía. Registra una cuenta o una deuda para ver tu patrimonio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is NetWorthUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Reintentar") }
                }
                is NetWorthUiState.Content -> {
                    state.byCurrency.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(4.dp))
                        CurrencyNetWorthRow(item, showCurrencyLabel = state.byCurrency.size > 1)
                    }
                    Text(
                        text = "Activos reales + por cobrar − pasivos. La línea de crédito disponible y las reservas no se suman.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyNetWorthRow(item: CurrencyNetWorth, showCurrencyLabel: Boolean) {
    val label = when (item.currency) {
        Currency.PEN -> "Soles"
        Currency.USD -> "Dólares"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        if (showCurrencyLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyText(
            money = item.netWorth,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        ComponentLine("Activos reales", item.realAssets)
        ComponentLine("Por cobrar", item.receivables)
        ComponentLine("Pasivos pendientes", item.liabilities)
    }
}

@Composable
private fun ComponentLine(label: String, money: com.kipu.app.core.finance.domain.model.Money) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money = money, style = MaterialTheme.typography.bodySmall)
    }
}
