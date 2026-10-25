package com.kipu.app.feature.goals.presentation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.model.GoalSyncState
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import androidx.compose.material3.ButtonDefaults
import com.kipu.app.ui.component.MoneyText
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

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
    onContributeGoal: ((String) -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    showBackButton: Boolean = false,
    onOpenBudgets: (() -> Unit)? = null,
    onWithdrawGoal: ((String) -> Unit)? = null,
    embedded: Boolean = false,
    onArchiveGoal: (String) -> Unit = onOpenGoal,
    onGoalHistory: (String) -> Unit = onOpenGoal,
    createRequested: Boolean = false,
    onCreateRequestConsumed: () -> Unit = {},
) {
    val colors = rememberCalmEmeraldColors()
    val visibleGoals = state.goals.filter { it.goal.status != GoalStatus.ARCHIVED }
    val archivedGoals = state.goals.filter { it.goal.status == GoalStatus.ARCHIVED }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GoalListItemState?>(null) }
    var selectedForOptions by remember { mutableStateOf<GoalListItemState?>(null) }
    var showPlanLimit by rememberSaveable { mutableStateOf(false) }
    val requestCreate = { if (state.freeGoalLimitReached) showPlanLimit = true else onCreateGoal() }
    LaunchedEffect(createRequested, state.isLoading) {
        if (createRequested && !state.isLoading) {
            onCreateRequestConsumed()
            requestCreate()
        }
    }

    Scaffold(
        // Embedded in the Planning host, which owns the title, actions and tab selector.
        contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) TopAppBar(
                title = { Text("Planificación", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    onNotifications?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
                        }
                    }
                    onSettings?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Settings, contentDescription = "Configuración")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!embedded) item { PlanningSelector(onOpenBudgets) }
                if (visibleGoals.isNotEmpty()) item {
                    Surface(shape = RoundedCornerShape(18.dp), color = colors.primaryDeep, contentColor = colors.onPrimaryDeep) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("TOTAL RESERVADO EN METAS", style = MaterialTheme.typography.labelSmall)
                            visibleGoals.groupBy { it.goal.currency }.forEach { (currency, entries) ->
                                MoneyText(com.kipu.app.core.finance.domain.model.Money(entries.sumOf { it.progress.reserved.minorUnits }, currency),
                                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onPrimaryDeep)
                            }
                            Text("Dinero ya apartado: no cuenta como disponible para gastar.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(if (state.hasPremiumAccess) "PRO · metas ilimitadas" else "${state.activeGoalCount.coerceAtMost(2)} de 2 activas",
                                    style = MaterialTheme.typography.labelMedium)
                                if (!state.hasPremiumAccess) Text(if (state.freeGoalLimitReached) "Plan Básico: límite alcanzado" else "Plan Básico",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                if (visibleGoals.isEmpty()) item {
                    SavingsCapacityCard(
                        activeGoalCount = state.activeGoalCount,
                        hasPremiumAccess = state.hasPremiumAccess,
                        atLimit = state.freeGoalLimitReached,
                    )
                }
                item {
                    if (state.freeGoalLimitReached) {
                        Button(
                            onClick = { showPlanLimit = true },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.warningBg, contentColor = colors.warningText),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                            Text("  Crear más metas con Pro")
                        }
                    } else {
                        Button(
                            onClick = requestCreate,
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("goal_create_button"),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("  Crear meta", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                state.errorMessage?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (visibleGoals.isEmpty()) {
                    item { EmptyGoalsCard() }
                } else {
                    item {
                        Text("Tus metas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(visibleGoals, key = { it.goal.id }) { item ->
                        GoalSummaryCard(
                            item = item,
                            linkedAccountName = state.backingAccounts.firstOrNull { it.id == item.goal.linkedAccountId }?.name,
                            onOpen = { onOpenGoal(item.goal.id) },
                            onOptions = { selectedForOptions = item },
                            onContribute = {
                                val goalId = item.goal.id
                                if (onContributeGoal != null) onContributeGoal(goalId) else onOpenGoal(goalId)
                            },
                        )
                    }
                }
                if (archivedGoals.isNotEmpty()) {
                    item {
                        Surface(
                            onClick = { showArchived = !showArchived },
                            modifier = Modifier.fillMaxWidth().testTag("goal_archived_toggle"),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Archive, contentDescription = null)
                                Text("Metas archivadas (${archivedGoals.size})", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Icon(if (showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showArchived) "Ocultar archivadas" else "Mostrar archivadas")
                            }
                        }
                    }
                    if (showArchived) items(archivedGoals, key = { it.goal.id }) { item ->
                        GoalSummaryCard(item,
                            state.backingAccounts.firstOrNull { it.id == item.goal.linkedAccountId }?.name,
                            onOpen = { onOpenGoal(item.goal.id) },
                            onOptions = { selectedForOptions = item },
                            onContribute = {})
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    selectedForOptions?.let { item ->
        GoalOptionsSheet(
            item = item,
            onDismiss = { selectedForOptions = null },
            onContribute = {
                selectedForOptions = null
                val goalId = item.goal.id
                if (onContributeGoal != null) onContributeGoal(goalId) else onOpenGoal(goalId)
            },
            onHistory = { selectedForOptions = null; onGoalHistory(item.goal.id) },
            onWithdraw = { selectedForOptions = null; (onWithdrawGoal ?: onOpenGoal)(item.goal.id) },
            onEdit = { selectedForOptions = null; onEditGoal(item.goal.id) },
            onArchive = { selectedForOptions = null; onArchiveGoal(item.goal.id) },
            onDelete = { selectedForOptions = null; pendingDelete = item },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(18.dp).size(28.dp))
                }
            },
            title = { Text("¿Eliminar “${item.goal.name}”?") },
            text = {
                Text("Solo se elimina físicamente una meta sin aportes, movimientos relacionados ni operaciones pendientes. Las metas con historial deben conservarse y archivarse desde su detalle.")
            },
            confirmButton = {
                Button(onClick = { pendingDelete = null; onDeleteGoal(item.goal.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("Eliminar meta") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
    if (showPlanLimit) {
        GoalPlanLimitSheet(onDismiss = { showPlanLimit = false }, onSeePlans = { showPlanLimit = false; onNavigateToPlans() })
    }
}

@Composable
private fun PlanningSelector(onOpenBudgets: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)
            .testTag("planning_selector"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp)
                .testTag("planning_goals_selector")
                .semantics { selected = true },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("  Metas", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                .then(
                    if (onOpenBudgets != null) {
                        Modifier.clickable(role = Role.Tab, onClickLabel = "Abrir presupuestos", onClick = onOpenBudgets)
                    } else {
                        Modifier.clickable(enabled = false) {}.semantics { disabled() }
                    },
                )
                .padding(vertical = 12.dp)
                .testTag("planning_budgets_selector"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text("  Presupuestos", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavingsCapacityCard(
    activeGoalCount: Int,
    hasPremiumAccess: Boolean,
    atLimit: Boolean,
) {
    val colors = rememberCalmEmeraldColors()
    val slots = activeGoalCount.coerceAtMost(GoalManagementUiState.FREE_GOAL_LIMIT)
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Metas de ahorro", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(50),
                    color = if (atLimit) colors.warningBg else MaterialTheme.colorScheme.secondaryContainer) {
                    Text(if (hasPremiumAccess) "Pro" else "Plan Básico",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                        color = if (atLimit) colors.warningText else MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            if (!hasPremiumAccess) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$slots de 2 activas", style = MaterialTheme.typography.bodyMedium)
                    Text(if (atLimit) "Límite alcanzado" else if (slots == 1) "1 disponible" else "2 disponibles",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (atLimit) colors.warningText else MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    repeat(GoalManagementUiState.FREE_GOAL_LIMIT) { index ->
                        LinearProgressIndicator(progress = { if (index < slots) 1f else 0f }, drawStopIndicator = {},
                            modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape))
                    }
                }
            }
            Text(if (hasPremiumAccess) "Organiza tu ahorro con metas ilimitadas." else "Separa dinero para lo que quieres lograr.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyGoalsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
            }
            Text("Todavía no tienes metas de ahorro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Crea tu primera meta para comenzar a organizar tus ahorros y monitorear tu progreso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalSummaryCard(
    item: GoalListItemState,
    linkedAccountName: String?,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
    onContribute: () -> Unit,
) {
    val goal = item.goal
    val progress = item.progress.progressPercent
    val progressFraction = progress.divide(BigDecimal.valueOf(100)).toFloat().coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("goal_card_${goal.id}").clickable(role = Role.Button, onClickLabel = "Ver detalle", onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalIconBadge(goal.iconKey)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(goal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            goal.status.label(),
                            linkedAccountName?.let { "Cuenta $it" } ?: "Meta virtual",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOptions, modifier = Modifier.testTag("goal_options_${goal.id}")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${goal.name}")
                }
            }
            goal.syncState.message()?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (goal.syncState == GoalSyncState.CONFLICT || goal.syncState == GoalSyncState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyText(item.progress.reserved, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("  de ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(goal.target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${progress.stripTrailingZeros().toPlainString()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reservado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                goal.targetDate?.let { date ->
                    Text("Fecha estimada · ${date.format(DateTimeFormatter.ofPattern("MMM yyyy"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (goal.status == GoalStatus.ACTIVE) OutlinedButton(
                onClick = onContribute,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Aportar a meta")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalOptionsSheet(
    item: GoalListItemState,
    onDismiss: () -> Unit,
    onContribute: () -> Unit,
    onHistory: () -> Unit,
    onWithdraw: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    com.kipu.app.ui.component.PlanningActionsSheet(
        title = item.goal.name,
        onDismiss = onDismiss,
        actions = buildList {
            if (item.goal.status == GoalStatus.ACTIVE) add(com.kipu.app.ui.component.PlanningAction(
                "Aportar", "Aparta dinero de una cuenta", Icons.Default.Add, onContribute,
                com.kipu.app.ui.component.PlanningActionTone.PRIMARY))
            if (item.progress.reserved.minorUnits > 0L) add(com.kipu.app.ui.component.PlanningAction(
                "Retirar", "Devuelve dinero a tu disponible", Icons.Default.Remove, onWithdraw))
            if (item.goal.status != GoalStatus.ARCHIVED) add(com.kipu.app.ui.component.PlanningAction(
                "Editar", "Nombre, monto, fecha e icono", Icons.Default.Edit, onEdit))
            add(com.kipu.app.ui.component.PlanningAction("Ver historial de aportes", "Consulta los aportes y retiros", Icons.Default.History, onHistory))
            if (item.goal.status != GoalStatus.ARCHIVED) add(com.kipu.app.ui.component.PlanningAction(
                "Archivar meta", "Resuelve la reserva y conserva su historial", Icons.Default.Archive, onArchive,
                com.kipu.app.ui.component.PlanningActionTone.WARNING))
            if (item.progress.reserved.minorUnits == 0L && item.goal.status != GoalStatus.ARCHIVED) add(com.kipu.app.ui.component.PlanningAction(
                "Eliminar meta vacía", "Solo si no tiene historia ni operaciones pendientes", Icons.Default.Delete, onDelete,
                com.kipu.app.ui.component.PlanningActionTone.DESTRUCTIVE))
        },
        summary = {
            Row {
                MoneyText(item.progress.reserved, style = MaterialTheme.typography.bodySmall)
                Text(" de ", style = MaterialTheme.typography.bodySmall)
                MoneyText(item.goal.target, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

private fun GoalStatus.label(): String = when (this) {
    GoalStatus.ACTIVE -> "Activa"
    GoalStatus.COMPLETED -> "Completada · reserva disponible"
    GoalStatus.ARCHIVED -> "Archivada"
    GoalStatus.LOCKED_BY_PLAN -> "Limitada por Plan Free · historia conservada"
}

private fun GoalSyncState.message(): String? = when (this) {
    GoalSyncState.PENDING -> null
    GoalSyncState.SYNCED -> null
    GoalSyncState.CONFLICT -> "Revisa los cambios de esta meta antes de continuar"
    GoalSyncState.FAILED -> "No se pudieron guardar los cambios. Revisa esta meta"
    GoalSyncState.DELETE_PENDING -> "Eliminación en curso"
}
