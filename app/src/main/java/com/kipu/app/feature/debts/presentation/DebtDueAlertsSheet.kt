package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.theme.rememberPlanningColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    val colors = rememberPlanningColors()
    val due = debts.filter { it.status == DebtLifecycleStatus.ACTIVE && it.remainingPrincipalMinor > 0L }.flatMap { debt ->
        val schedule = schedules[debt.debtId].orEmpty()
        if (schedule.isNotEmpty()) schedule.filter { it.status == "PENDING" || it.status == "PARTIAL" }
            .map { Triple(debt, it.dueDate, "Cuota ${it.installmentNumber} de ${schedule.size} · programada") }
        else listOfNotNull(debt.dueDate?.let { Triple(debt, it, "Vencimiento de la obligación") })
    }.sortedBy { it.second }
    val overdue = due.count { it.second < today }
    KipuBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("debt_due_alerts"),
        footer = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar vencimientos") } }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Alertas de deudas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todas ${due.size}", "Vencidas $overdue", "Por vencer ${due.size - overdue}").forEachIndexed { index, label ->
                    KipuFilterChip(filter == index, { filter = index }, label = { Text(label) }, modifier = Modifier.testTag("debt_due_filter_$index"))
                }
            }
            val visible = due.filter { when (filter) { 1 -> it.second < today; 2 -> it.second >= today; else -> true } }
            if (visible.isEmpty()) Text("No hay vencimientos en este filtro.")
            visible.forEach { (debt, date, scheduleLabel) ->
                val days = ChronoUnit.DAYS.between(today, date)
                Surface(shape = RoundedCornerShape(16.dp), color = if (days < 0) colors.expenseBg else colors.warningBg) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(debt.counterpartyName, fontWeight = FontWeight.Bold)
                        Text(scheduleLabel, style = MaterialTheme.typography.bodySmall)
                        Text("$date · " + when { days < 0 -> "Vencida hace ${-days} días"; days == 0L -> "Vence hoy"; else -> "Vence en $days días" },
                            color = if (days < 0) colors.expenseText else colors.warningText)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onDismiss(); onSettle(debt.debtId) }) {
                                Text(if (debt.obligationType == DebtObligationType.PAYABLE) "Registrar pago" else "Registrar cobro")
                            }
                            OutlinedButton(onClick = { onDismiss(); onView(debt.debtId) }) { Text("Ver") }
                        }
                    }
                }
            }
            Text("Las fechas y cuotas son planificación: no registran pagos automáticamente.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
