package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.model.GoalSyncState
import com.kipu.app.ui.component.MoneyText
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(
    state: GoalManagementUiState,
    onNavigateBack: () -> Unit,
    onCreateGoal: () -> Unit,
    onOpenGoal: (String) -> Unit,
    onEditGoal: (String) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onNavigateToPlans: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<GoalListItemState?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metas") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Organiza tus objetivos y reserva dinero sin duplicar el saldo de tus cuentas.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (!state.hasPremiumAccess) {
                            Text(
                                "Plan Free: ${state.activeGoalCount.coerceAtMost(2)} de 2 metas activas.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (state.freeGoalLimitReached) {
                                OutlinedButton(onClick = onNavigateToPlans, modifier = Modifier.fillMaxWidth()) {
                                    Text("Ver planes para crear más metas")
                                }
                            }
                        }
                        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        if (!state.freeGoalLimitReached || state.hasPremiumAccess) {
                            Button(onClick = onCreateGoal, modifier = Modifier.fillMaxWidth().testTag("goal_create_button")) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("  Crear meta")
                            }
                        }
                    }
                }
                if (state.goals.isEmpty()) {
                    item {
                        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Todavía no tienes metas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Puedes empezar con una meta virtual. Vincular una cuenta real es opcional.")
                            }
                        }
                    }
                }
                items(state.goals, key = { it.goal.id }) { item ->
                    GoalSummaryCard(
                        item = item,
                        linkedAccountName = state.backingAccounts.firstOrNull { it.id == item.goal.linkedAccountId }?.name,
                        onOpen = { onOpenGoal(item.goal.id) },
                        onEdit = { onEditGoal(item.goal.id) },
                        onDelete = { pendingDelete = item },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar meta vacía") },
            text = { Text("Solo se puede eliminar una meta sin aportes ni movimientos relacionados. ¿Continuar?") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteGoal(item.goal.id) }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun GoalSummaryCard(
    item: GoalListItemState,
    linkedAccountName: String?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val goal = item.goal
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.clickable(onClick = onOpen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(goal.status.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onEdit, enabled = goal.status != GoalStatus.ARCHIVED) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar ${goal.name}")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más acciones de ${goal.name}")
                }
            }
            goal.syncState.message()?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (goal.syncState == GoalSyncState.CONFLICT || goal.syncState == GoalSyncState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            LinearProgressIndicator(
                progress = {
                    item.progress.progressPercent.divide(BigDecimal.valueOf(100)).toFloat().coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Reservado", style = MaterialTheme.typography.labelSmall)
                    MoneyText(item.progress.reserved, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Objetivo", style = MaterialTheme.typography.labelSmall)
                    MoneyText(goal.target, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                linkedAccountName?.let { "Cuenta vinculada: $it" } ?: "Meta virtual · sin cuenta vinculada",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun GoalStatus.label(): String = when (this) {
    GoalStatus.ACTIVE -> "Activa"
    GoalStatus.COMPLETED -> "Completada · la reserva sigue disponible"
    GoalStatus.ARCHIVED -> "Archivada"
    GoalStatus.LOCKED_BY_PLAN -> "Pausada por el plan · se conserva la historia"
}

private fun GoalSyncState.message(): String? = when (this) {
    GoalSyncState.PENDING -> "Guardada en este dispositivo · pendiente de sincronización"
    GoalSyncState.SYNCED -> null
    GoalSyncState.CONFLICT -> "Conflicto de sincronización · requiere revisión"
    GoalSyncState.FAILED -> "No se pudo sincronizar · requiere acción"
    GoalSyncState.DELETE_PENDING -> "Eliminación pendiente de sincronización"
}
