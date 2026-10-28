package com.kipu.app.feature.goals.presentation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.material3.VerticalDivider
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
import com.kipu.app.ui.theme.rememberPlanningColors
import androidx.compose.material3.ButtonDefaults
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.core.finance.domain.model.Currency
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
    onCompleteGoal: (String) -> Unit = onOpenGoal,
    onManageDeleteGoal: ((String) -> Unit)? = null,
) {
    val colors = rememberPlanningColors()
    val sections = state.displaySections()
    val visibleGoals = sections.active
    val archivedGoals = sections.paused
    val completedGoals = sections.completed
    val allVisibleGoals = visibleGoals + archivedGoals + completedGoals
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
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
                if (allVisibleGoals.isNotEmpty()) item {
                    GoalTotalsCard(visibleGoals)
                }
                if (allVisibleGoals.isEmpty()) item {
                    if (state.freeGoalLimitReached) {
                        Button(
                            onClick = { showPlanLimit = true },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.warningBg, contentColor = colors.warningText),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                            Text("  Crear meta")
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
                    item { com.kipu.app.ui.component.PlanningNotice("No se pudo completar la acción", message, isError = true) }
                }
                if (allVisibleGoals.isEmpty()) {
                    item { EmptyGoalsCard() }
                } else {
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
                                Text("Metas pausadas (${archivedGoals.size})", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Icon(if (showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showArchived) "Ocultar pausadas" else "Mostrar pausadas")
                            }
                        }
                    }
                    if (showArchived) items(archivedGoals, key = { it.goal.id }) { item ->
                        GoalSummaryCard(item,
                            state.backingAccounts.firstOrNull { it.id == item.goal.linkedAccountId }?.name,
                            onOpen = { onOpenGoal(item.goal.id) },
                            onOptions = { selectedForOptions = item },
                            onContribute = {}, isPaused = true)
                    }
                }
                if (completedGoals.isNotEmpty()) {
                    item {
                        Surface(onClick = { showCompleted = !showCompleted }, shape = RoundedCornerShape(18.dp),
                            color = colors.surfaceCard, modifier = Modifier.fillMaxWidth().testTag("goal_completed_toggle")) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.EmojiEvents, null, tint = colors.incomeText)
                                Text("Metas finalizadas (${completedGoals.size})", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Icon(if (showCompleted) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    if (showCompleted) "Ocultar finalizadas" else "Mostrar finalizadas")
                            }
                        }
                    }
                    if (showCompleted) items(completedGoals, key = { it.goal.id }) { item ->
                        GoalSummaryCard(item, state.backingAccounts.firstOrNull { it.id == item.goal.linkedAccountId }?.name,
                            onOpen = { onOpenGoal(item.goal.id) }, onOptions = { selectedForOptions = item }, onContribute = {})
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
            onDelete = { selectedForOptions = null; if (onManageDeleteGoal != null) onManageDeleteGoal(item.goal.id) else pendingDelete = item },
            onComplete = { selectedForOptions = null; onCompleteGoal(item.goal.id) },
            isPaused = item.goal.id in state.pausedGoalIds || item.goal.status == GoalStatus.ARCHIVED,
        )
    }

    pendingDelete?.let { item ->
        GoalDeleteDialog(
            goal = item.goal,
            hasReserve = item.progress.reserved.minorUnits > 0L,
            isSaving = state.isSaving,
            onDismiss = { pendingDelete = null },
            onDelete = { pendingDelete = null; (onManageDeleteGoal ?: onDeleteGoal)(item.goal.id) },
            onArchive = { pendingDelete = null; onArchiveGoal(item.goal.id) },
        )
    }

    if (showPlanLimit) {
        GoalPlanLimitSheet(onDismiss = { showPlanLimit = false }, onSeePlans = { showPlanLimit = false; onNavigateToPlans() })
    }
}

internal data class GoalCurrencyTotals(val target: Long, val reserved: Long)

internal fun goalTotals(goals: List<GoalListItemState>): Map<Currency, GoalCurrencyTotals> =
    goals.filter { it.goal.status == GoalStatus.ACTIVE }.groupBy { it.goal.currency }.mapValues { (_, entries) ->
        GoalCurrencyTotals(
            target = entries.fold(0L) { total, item -> Math.addExact(total, item.goal.target.minorUnits) },
            reserved = entries.fold(0L) { total, item -> Math.addExact(total, item.progress.reserved.minorUnits) },
        )
    }

@Composable
private fun GoalTotalsCard(goals: List<GoalListItemState>) {
    val colors = rememberPlanningColors()
    val totals = goalTotals(goals)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("goal_totals"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                GoalTotal("Total en metas", totals.mapValues { it.value.target }, Modifier.weight(1f))
                VerticalDivider(Modifier.fillMaxHeight().padding(horizontal = 14.dp), color = colors.borderSubtle)
                GoalTotal("Reservado", totals.mapValues { it.value.reserved }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GoalTotal(label: String, totals: Map<Currency, Long>, modifier: Modifier = Modifier) {
    val colors = rememberPlanningColors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        MoneyText(minorUnits = totals[Currency.PEN] ?: 0L, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold, color = colors.primaryDeep)
        totals.filterKeys { it != Currency.PEN }.forEach { (currency, minor) ->
            MoneyText(minorUnits = minor, currency = currency, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = colors.primaryDeep)
        }
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
            Text("  Metas", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
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
            Text("  Presupuestos", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
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
            Surface(shape = CircleShape, color = com.kipu.app.ui.theme.rememberPlanningColors().incomeBg, modifier = Modifier.size(64.dp)) {
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
    isPaused: Boolean = false,
) {
    val goal = item.goal
    val colors = rememberPlanningColors()
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
                            if (isPaused) "Pausada" else if (goal.status == GoalStatus.ACTIVE) goal.targetDate?.let { "Meta ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("es", "PE")))}" } else goal.status.label(),
                            linkedAccountName ?: "Sin cuenta",
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
            val remainingMinor = (goal.target.minorUnits - item.progress.reserved.minorUnits).coerceAtLeast(0L)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Reservado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        MoneyText(item.progress.reserved, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(goal.target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Faltan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(
                        com.kipu.app.core.finance.domain.model.Money(remainingMinor, goal.target.currency),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                color = colors.incomeEmerald,
                trackColor = colors.pillTrack,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${progress.stripTrailingZeros().toPlainString()} %", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = colors.primaryDeep,
                    modifier = Modifier.background(colors.incomeBg, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                Spacer(Modifier.weight(1f))
                val months = goal.targetDate?.takeIf { goal.status == GoalStatus.ACTIVE && remainingMinor > 0L }?.let {
                    java.time.temporal.ChronoUnit.MONTHS.between(java.time.LocalDate.now().withDayOfMonth(1), it.withDayOfMonth(1)).coerceAtLeast(1L)
                }
                if (months != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ahorra ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(
                            com.kipu.app.core.finance.domain.model.Money((remainingMinor + months - 1) / months, goal.target.currency),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(" al mes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (goal.status == GoalStatus.ACTIVE && !isPaused) Button(
                onClick = onContribute,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Aportar", fontWeight = FontWeight.SemiBold)
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
    onComplete: () -> Unit,
    isPaused: Boolean = false,
) {
    com.kipu.app.ui.component.PlanningActionsSheet(
        title = item.goal.name,
        onDismiss = onDismiss,
        actions = buildList {
            if (item.goal.status == GoalStatus.ACTIVE && !isPaused) add(com.kipu.app.ui.component.PlanningAction(
                "Aportar", "Aparta dinero de una cuenta", Icons.Default.Add, onContribute,
                com.kipu.app.ui.component.PlanningActionTone.PRIMARY))
            if (item.progress.reserved.minorUnits > 0L) add(com.kipu.app.ui.component.PlanningAction(
                "Retirar", "Devuelve dinero a tu disponible", Icons.Default.Remove, onWithdraw))
            if (item.goal.status != GoalStatus.ARCHIVED) add(com.kipu.app.ui.component.PlanningAction(
                "Editar", "Nombre, monto, fecha e icono", Icons.Default.Edit, onEdit))
            add(com.kipu.app.ui.component.PlanningAction("Ver historial de aportes", "Consulta los aportes y retiros", Icons.Default.History, onHistory))
            if (item.goal.status == GoalStatus.ACTIVE) add(com.kipu.app.ui.component.PlanningAction(
                "Marcar como lograda", "Ajusta el objetivo al dinero reservado", Icons.Default.EmojiEvents, onComplete,
                com.kipu.app.ui.component.PlanningActionTone.WARNING, tag = "goal_complete"))
            if (item.goal.status == GoalStatus.ACTIVE || item.goal.status == GoalStatus.LOCKED_BY_PLAN) add(com.kipu.app.ui.component.PlanningAction(
                if (isPaused) "Reanudar meta" else "Pausar meta", "Conserva el dinero reservado y su historial", Icons.Default.Archive, onArchive,
                com.kipu.app.ui.component.PlanningActionTone.WARNING))
            add(com.kipu.app.ui.component.PlanningAction(
                "Eliminar meta", "Revisa las condiciones antes de eliminar", Icons.Default.Delete, onDelete,
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
    GoalStatus.ARCHIVED -> "Pausada"
    GoalStatus.LOCKED_BY_PLAN -> "Limitada por Plan Free · historia conservada"
}

private fun GoalSyncState.message(): String? = when (this) {
    GoalSyncState.PENDING -> null
    GoalSyncState.SYNCED -> null
    GoalSyncState.CONFLICT -> "Revisa los cambios de esta meta antes de continuar"
    GoalSyncState.FAILED -> "No se pudieron guardar los cambios. Revisa esta meta"
    GoalSyncState.DELETE_PENDING -> "Eliminación en curso"
}
