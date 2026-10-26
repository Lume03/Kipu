package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits

@Composable
fun MovementAppliedFilters(state: MovementHistoryUiState, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    val filters = state.appliedFilters
    val parked = state.accessStatus != MovementHistoryAccessDecision.Allowed
    val rows = state.allTransactions
    val chips = buildList<Pair<String, String>> {
        state.selectedFilterType?.let { type ->
            add("type" to when (type) {
                MovementType.EXPENSE -> "Gastos"
                MovementType.INCOME -> "Ingresos"
                MovementType.TRANSFER -> "Transferencias"
            })
        }
        if (filters.fromDate != null || filters.toDate != null) add("dates" to
            "${MovementFilterDraft.fromApplied(filters).fromDate.ifBlank { "…" }} — ${MovementFilterDraft.fromApplied(filters).toDate.ifBlank { "…" }}")
        filters.accountIds.forEach { id -> add("account:$id" to (rows.firstOrNull { it.transaction.sourceAccountId == id }?.sourceAccountAlias
            ?: rows.firstOrNull { it.transaction.destinationAccountId == id }?.destinationAccountAlias ?: "Cuenta histórica")) }
        filters.categoryIds.forEach { id -> add("category:$id" to (rows.firstOrNull { it.transaction.categoryId == id }?.categoryName ?: "Categoría histórica")) }
        filters.cardIds.forEach { id -> add("card:$id" to (rows.firstOrNull { it.transaction.cardId == id }?.cardAlias ?: "Tarjeta histórica")) }
        filters.merchantIds.forEach { id -> add("merchant:$id" to (rows.firstOrNull { it.transaction.merchantId == id }?.merchantName ?: "Comercio histórico")) }
        filters.paymentChannels.forEach { channel -> add("channel:${channel.name}" to "Billetera ${channel.label}") }
        if (filters.minAmountMinor != null || filters.maxAmountMinor != null) add("amount" to "Importe")
        filters.financialStates.forEach { add("state:${it.name}" to it.uiLabel()) }
        filters.syncStatuses.forEach { add("sync:${it.name}" to it.uiLabel()) }
    }
    if (chips.isEmpty()) return
    // Cabecera de filtros activos: "Filtrando por …" + un chip quitable por criterio
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Filtrando por",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { it.first }) { (key, label) ->
            InputChip(selected = true, onClick = { onRemove(key) }, label = {
                Column {
                    Text(if (parked && key != "dates") stringResource(R.string.history_filter_saved, label) else label)
                    if (key == "amount") MoneyText("${filters.minAmountMinor?.let(::formatMinorUnits) ?: "…"} — ${filters.maxAmountMinor?.let(::formatMinorUnits) ?: "…"}",
                        currencySymbol = filters.currency, style = MaterialTheme.typography.labelMedium)
                }
            }, trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.history_filter_remove, label), Modifier.size(18.dp)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    }
}
