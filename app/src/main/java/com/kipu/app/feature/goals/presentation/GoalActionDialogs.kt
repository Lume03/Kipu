package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.rememberPlanningColors

@Composable
internal fun GoalDeleteDialog(
    goal: Goal,
    hasReserve: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    reserved: Money? = null,
    replacementCandidates: List<Pair<String, String>> = emptyList(),
    onDeleteWithReplacement: ((String?) -> Unit)? = null,
    errorMessage: String? = null,
) {
    val colors = rememberPlanningColors()
    var replacement by rememberSaveable(goal.id) { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Surface(shape = CircleShape, color = colors.expenseBg) {
                Icon(Icons.Default.DeleteOutline, null, tint = colors.expenseText,
                    modifier = Modifier.padding(18.dp).size(30.dp))
            }
        },
        title = { Text("¿Eliminar esta meta?", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("“${goal.name}”")
                Text("La meta se quitará de tu listado. Sus aportes y movimientos se conservarán en el historial financiero.")
                Surface(color = colors.incomeBg, shape = RoundedCornerShape(16.dp)) {
                    Text(if (hasReserve) "Al confirmar, el dinero reservado volverá a estar disponible en las mismas cuentas. No se crea un ingreso ni una transferencia."
                        else "Si solo quieres dejar de aportar temporalmente, puedes pausarla y retomarla después.",
                        modifier = Modifier.padding(14.dp), color = colors.primaryDeep)
                }
                reserved?.takeIf { it.minorUnits > 0L }?.let { MoneyText(it, fontWeight = FontWeight.Bold, color = colors.primaryDeep) }
                if (replacementCandidates.isNotEmpty()) {
                    Text("Para liberar la reserva, el plan requiere elegir otra meta activa que quedará limitada. El historial se conserva.")
                    GoalPicker(replacementCandidates, replacement, { expanded = it }, expanded, "goal_delete_replacement") { replacement = it }
                }
                errorMessage?.let { Text(it, color = colors.expenseText) }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (onDeleteWithReplacement != null) onDeleteWithReplacement(replacement.takeIf(String::isNotBlank)) else onDelete() },
                    enabled = !isSaving && (replacementCandidates.isEmpty() || replacementCandidates.any { it.first == replacement }),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.expenseText, contentColor = colors.surfaceCard),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goal_delete_confirm")) {
                    Text("Eliminar meta")
                }
                if (goal.status == com.kipu.app.feature.goals.domain.model.GoalStatus.ACTIVE || goal.status == com.kipu.app.feature.goals.domain.model.GoalStatus.LOCKED_BY_PLAN) FilledTonalButton(onClick = onArchive, enabled = !isSaving,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.incomeBg, contentColor = colors.primaryDeep),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goal_delete_archive")) {
                    Icon(Icons.Default.Archive, null, Modifier.size(20.dp))
                    Text("Mejor pausarla", Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancelar", color = colors.secondaryMuted) }
            }
        },
    )
}

@Composable
internal fun GoalCompleteDialog(goal: Goal, reserved: Money, isSaving: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = rememberPlanningColors()
    val canComplete = reserved.minorUnits > 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(28.dp),
        icon = { Surface(shape = CircleShape, color = colors.warningBg) {
            Icon(Icons.Default.EmojiEvents, null, tint = colors.warningText, modifier = Modifier.padding(18.dp).size(30.dp))
        } },
        title = { Text("¿Marcar como lograda?", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(goal.name)
            if (canComplete) {
                Text("El objetivo se ajustará al dinero que ya tienes reservado:")
                MoneyText(reserved, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.primaryDeep)
                Text("Tu dinero seguirá reservado. Se conservarán todos los aportes y podrás retirarlo cuando lo necesites.")
            } else Text("Primero realiza un aporte. Necesitas dinero reservado para ajustar el objetivo y marcar esta meta como lograda.")
        } },
        confirmButton = { Button(onClick = onConfirm, enabled = canComplete && !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
            modifier = Modifier.testTag("goal_complete_confirm")) { Text("Ajustar y marcar como lograda") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = colors.secondaryMuted) } },
    )
}
