package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@Composable
internal fun BudgetDeleteDialog(content: BudgetDetailContent, onDelete: () -> Unit, onCancel: () -> Unit,
    onKeepHistory: (() -> Unit)?) {
    val colors = rememberCalmEmeraldColors()
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceCard, modifier = Modifier.fillMaxWidth().testTag("budget_delete_dialog")) {
            Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape, color = colors.expenseBg) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.padding(18.dp).size(28.dp), tint = colors.expenseText)
                }
                Text("¿Eliminar «${content.budget.name}»?", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Dejarás de ver su progreso y de recibir alertas.", style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryMuted, textAlign = TextAlign.Center)
                Text("NO se borra ningún movimiento, gasto, cuenta ni categoría. Tus saldos no cambian.",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                content.current?.let { current ->
                    Surface(shape = RoundedCornerShape(12.dp), color = colors.pillTrack, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Llevabas", style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                BudgetMoney(current.consumption.consumedMinor, content.budget.currency, fontWeight = FontWeight.Bold)
                                Text("de", style = MaterialTheme.typography.bodyMedium)
                                BudgetMoney(current.consumption.limitMinor, content.budget.currency, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text("Esta acción es irreversible: se elimina el presupuesto y su historial de periodos.",
                    style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, textAlign = TextAlign.Center)
                Button(onDelete, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_delete_confirm"), enabled = !content.isWorking,
                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.expenseText, contentColor = colors.surfaceCard)) {
                    Text("Eliminar presupuesto", fontWeight = FontWeight.Bold)
                }
                onKeepHistory?.let { keep ->
                    OutlinedButton(keep, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Text("Mejor finalizarlo", fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onCancel, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("budget_delete_cancel")) { Text("Cancelar") }
            }
        }
    }
}
