package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kipu.app.ui.theme.rememberPlanningColors

@Composable
internal fun BudgetFinishDialog(content: BudgetDetailContent, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = rememberPlanningColors()
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceCard,
            modifier = Modifier.fillMaxWidth().testTag("budget_finish_dialog")) {
            Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = colors.incomeBg) {
                    Icon(Icons.Rounded.Archive, null, Modifier.padding(18.dp).size(28.dp), tint = colors.primaryDeep)
                }
                Text("¿Finalizar «${content.budget.name}»?", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Se conserva en Finalizados y libera tu cupo.", style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryMuted, textAlign = TextAlign.Center)
                content.current?.let { current ->
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.pillTrack, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${BudgetTexts.periodTypeShort(content.budget.schedule.type)} · ${BudgetTexts.compactInterval(current.interval)}",
                                style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                            BudgetAmountsRow(current.consumption, content.budget.currency, thresholdsBps = content.budget.thresholdsBps)
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(14.dp), color = colors.warningBg, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.NotificationsOff, null, tint = colors.warningText, modifier = Modifier.size(22.dp))
                        Text("Dejará de renovarse y de enviar alertas.", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = colors.warningText)
                    }
                }
                Text("Tus movimientos y saldos no cambian.", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Button(onConfirm, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_finish_confirm"),
                    enabled = !content.isWorking, shape = RoundedCornerShape(14.dp)) {
                    Text("Finalizar presupuesto", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onCancel, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("budget_finish_cancel"),
                    shape = RoundedCornerShape(14.dp)) { Text("Seguir con mi presupuesto", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
