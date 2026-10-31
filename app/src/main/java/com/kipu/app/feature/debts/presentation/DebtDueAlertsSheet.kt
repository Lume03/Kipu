package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.rememberPlanningColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dueDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun DebtDueAlertsSheet(
    debts: List<DebtSummary>,
    schedules: Map<String, List<DebtScheduleItem>>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onView: (String) -> Unit,
    onSettle: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(0) }
    val overview = buildDebtDueOverview(debts, schedules, today)
    val due = overview.alerts
    val showUpcoming = filter == 0 && overview.upcoming.isNotEmpty()
    val overdue = due.count { it.dueDate < today }
    val upcoming = due.size - overdue
    val visible = due.filter { when (filter) { 1 -> it.dueDate < today; 2 -> it.dueDate >= today; else -> true } }
    KipuBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.statusBarsPadding().padding(top = 12.dp).testTag("debt_due_alerts"),
        header = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alertas de deudas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        due.isEmpty() -> "Sin alertas en tu plazo de aviso"
                        overdue == 0 -> "Sin vencidas · $upcoming por vencer"
                        else -> "$overdue ${if (overdue == 1) "vencida" else "vencidas"} · $upcoming por vencer"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                due.groupBy { it.debt.obligationType to it.debt.currencyCode }.forEach { (group, alerts) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (group.first == DebtObligationType.PAYABLE) "Programado por pagar" else "Programado por cobrar",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        MoneyText(alerts.sumOf { it.amountMinor }, currency = Currency.fromCode(group.second),
                            style = MaterialTheme.typography.titleSmall)
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todas ${due.size}", "Vencidas $overdue", "Por vencer $upcoming").forEachIndexed { index, label ->
                        KipuFilterChip(filter == index, { filter = index }, label = { Text(label) }, modifier = Modifier.testTag("debt_due_filter_$index"))
                    }
                }
            }
        },
        footer = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar vencimientos") } },
    ) {
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            if (visible.isEmpty()) item {
                if (showUpcoming) {
                    Text("Nada vencido ni por vencer en tu plazo de aviso", modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Text("Todo al día en este filtro", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text("Aquí verás las deudas vencidas y las que estén dentro de su plazo de aviso.",
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(visible, key = { it.debt.debtId }) { alert ->
                DebtDueAlertCard(alert, today, onView = { onDismiss(); onView(alert.debt.debtId) },
                    onSettle = { onDismiss(); onSettle(alert.debt.debtId) })
            }
            if (showUpcoming) {
                item {
                    Text("Próximos vencimientos", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
                items(overview.upcoming, key = { "upcoming_${it.debt.debtId}" }) { upcomingDue ->
                    DebtUpcomingDueRow(upcomingDue, today, onView = { onDismiss(); onView(upcomingDue.debt.debtId) })
                }
            }
            item {
                Text("Avisos según el plazo de cada deuda (7 días por defecto). No registra pagos automáticamente.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DebtDueAlertCard(alert: DebtDueAlert, today: LocalDate, onView: () -> Unit, onSettle: () -> Unit) {
    val colors = rememberPlanningColors()
    val isOverdue = alert.dueDate < today
    val accent = if (isOverdue) colors.expenseText else colors.warningText
    val badge = if (isOverdue) colors.expenseBg else colors.warningBg
    val payable = alert.debt.obligationType == DebtObligationType.PAYABLE
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("debt_due_card_${alert.debt.debtId}")) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebtDueAvatar(alert.debt.counterpartyName)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(alert.debt.counterpartyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            alert.overdueInstallmentCount > 1 -> "${alert.overdueInstallmentCount} cuotas vencidas"
                            alert.installmentNumber != null -> "Cuota ${alert.installmentNumber} de ${alert.installmentCount}" + if (alert.hasPartialInstallment) " · pago parcial" else ""
                            else -> "Vencimiento de la obligación"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = badge,
                        modifier = Modifier.widthIn(max = 116.dp)) {
                        Text(debtDueDayLabel(alert.dueDate, today), modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center, color = accent, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyText(alert.amountMinor, currency = Currency.fromCode(alert.debt.currencyCode),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (alert.installmentNumber != null) "Programado" else "Saldo",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text((if (alert.overdueInstallmentCount > 1) "Primera vencida: " else "") + alert.dueDate.format(dueDateFormatter),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onSettle) { Text(if (payable) "Registrar pago" else "Registrar cobro") }
                    TextButton(onClick = onView) { Text("Ver") }
                }
            }
        }
    }
}

@Composable
private fun DebtUpcomingDueRow(due: DebtDueAlert, today: LocalDate, onView: () -> Unit) {
    Surface(onClick = onView, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("debt_upcoming_row_${due.debt.debtId}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DebtDueAvatar(due.debt.counterpartyName, compact = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(due.debt.counterpartyName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text((due.installmentNumber?.let { "Cuota $it de ${due.installmentCount} · " } ?: "") + due.dueDate.format(dueDateFormatter),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MoneyText(due.amountMinor, currency = Currency.fromCode(due.debt.currencyCode), style = MaterialTheme.typography.titleSmall)
                Text("En ${ChronoUnit.DAYS.between(today, due.dueDate)} ${if (ChronoUnit.DAYS.between(today, due.dueDate) == 1L) "día" else "días"}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (due.hasPartialInstallment) Text("Programado · parcial", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DebtDueAvatar(name: String, compact: Boolean = false) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.size(if (compact) 32.dp else 36.dp), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(Locale.forLanguageTag("es-PE")), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
