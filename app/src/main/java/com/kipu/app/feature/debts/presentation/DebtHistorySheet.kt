package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.MoneyText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun DebtHistorySheet(debt: DebtSummary, activities: List<DebtSettlementActivity>, onDismiss: () -> Unit,
    onOpenMovement: (String) -> Unit = {}) {
    var paymentsOnly by rememberSaveable { mutableStateOf(false) }
    val currency = Currency.fromCode(debt.currencyCode)
    KipuBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("debt_history_sheet"),
        footer = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar historial") } }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Historial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(debt.counterpartyName)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KipuFilterChip(!paymentsOnly, { paymentsOnly = false }, label = { Text("Todos") })
                KipuFilterChip(paymentsOnly, { paymentsOnly = true }, label = { Text("Pagos y cobros") })
            }
            val visible = activities.filter { !paymentsOnly || it.eventType == "PAYMENT" }.sortedByDescending { it.occurredAt }
            if (visible.isEmpty()) Text("No hay operaciones en este filtro.")
            visible.forEach { activity ->
                HorizontalDivider()
                Text(when (activity.eventType) {
                    "PAYMENT" -> "Pago o cobro registrado"
                    "ADJUSTMENT" -> "Ajuste de principal"
                    "FORGIVENESS" -> "Condonación"
                    else -> "Registro inicial"
                }, fontWeight = FontWeight.SemiBold)
                Text(Instant.ofEpochMilli(activity.occurredAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es-PE"))),
                    style = MaterialTheme.typography.bodySmall)
                MoneyText((activity.principalDeltaMinor ?: activity.principalMinor).absoluteValue, currency = currency)
                if (activity.eventType == "ADJUSTMENT") Text(
                    if ((activity.principalDeltaMinor ?: 0L) >= 0L) "Aumenta el saldo" else "Reduce el saldo",
                    style = MaterialTheme.typography.bodySmall)
                if (activity.interestMinor > 0L) {
                    Text("Interés", style = MaterialTheme.typography.labelMedium)
                    MoneyText(activity.interestMinor, currency = currency)
                }
                if (activity.isVoided) Text("Anulado · saldo restaurado", color = MaterialTheme.colorScheme.error)
                activity.transactionId?.let { id ->
                    TextButton(onClick = { onOpenMovement(id) }, modifier = Modifier.testTag("debt_history_movement_$id")) { Text("Ver movimiento") }
                }
            }
            if (!paymentsOnly) {
                HorizontalDivider()
                Text("Registro inicial · ${debt.openedOn}", fontWeight = FontWeight.SemiBold)
                MoneyText(debt.principalMinor, currency = currency)
            }
        }
    }
}
