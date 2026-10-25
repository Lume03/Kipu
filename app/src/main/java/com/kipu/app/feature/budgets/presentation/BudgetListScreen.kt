package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.budgets.domain.BudgetStatus
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetListScreen(
    state: BudgetListUiState,
    onNavigateBack: () -> Unit,
    onCreateBudget: () -> Unit,
    onOpenBudget: (String) -> Unit,
    onRetry: () -> Unit,
    onSeePlans: () -> Unit,
    embedded: Boolean = false,
    createRequested: Boolean = false,
    onCreateRequestConsumed: () -> Unit = {},
    onEditBudget: (String) -> Unit = onOpenBudget,
    onFinishBudget: (String) -> Unit = onOpenBudget,
    onDeleteBudget: (String) -> Unit = onOpenBudget,
) {
    var optionsId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf(0) }
    var showQuotaDialog by rememberSaveable { mutableStateOf(false) }
    val quota = when (state) {
        is BudgetListUiState.Content -> state.quota
        is BudgetListUiState.Empty -> state.quota
        else -> null
    }
    val requestCreate = {
        if (quota?.isAtLimit == true) showQuotaDialog = true else onCreateBudget()
    }
    // The root (+) button asks for the same quota-aware flow as the in-list create button.
    LaunchedEffect(createRequested, quota) {
        if (createRequested && state !is BudgetListUiState.Loading) {
            onCreateRequestConsumed()
            requestCreate()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("budget_list_screen"),
        // Embedded in the Planning host, which owns the title and the Metas/Presupuestos/Deudas tabs.
        contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Organización", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Planificación", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        }
                    },
                )
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    FlowRow(Modifier.padding(4.dp), maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2) {
                        Tab(selected = false, onClick = onNavigateBack, modifier = Modifier.weight(1f).testTag("budget_tab_goals"),
                            text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Flag, null, Modifier.size(20.dp))
                                Text("Metas", fontWeight = FontWeight.SemiBold)
                            } })
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
                            Tab(selected = true, onClick = {}, modifier = Modifier.testTag("budget_tab_budgets"), selectedContentColor = MaterialTheme.colorScheme.primary,
                                text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.PieChart, null, Modifier.size(20.dp))
                                    Text("Presupuestos", fontWeight = FontWeight.Bold)
                                } })
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (state) {
            BudgetListUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    Modifier.testTag("budget_loading").semantics { contentDescription = "Cargando presupuestos" },
                )
            }
            is BudgetListUiState.Error -> BudgetErrorPanel(
                message = state.message,
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
                tag = "budget_error",
            )
            is BudgetListUiState.Empty -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { QuotaCard(state.quota) }
                item { CreateButton(state.quota.isAtLimit, requestCreate) }
                item { EmptyBudgets() }
            }
            is BudgetListUiState.Content -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag("budget_items"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { QuotaCard(state.quota) }
                item { CreateButton(state.quota.isAtLimit, requestCreate) }
                item {
                    val counts = listOf(state.items.size,
                        state.items.count { it.status == BudgetStatus.ACTIVE && it.consumption?.needsAttention(it.thresholdsBps) == true },
                        state.items.count { it.status == BudgetStatus.ACTIVE && it.consumption?.isExceeded == true })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Todos", "Atención", "Excedidos").forEachIndexed { index, label ->
                            KipuFilterChip(selected = selectedFilter == index, onClick = { selectedFilter = index },
                                label = { Text("$label ${counts[index]}") }, modifier = Modifier.testTag("budget_filter_$index"))
                        }
                    }
                }
                val visible = state.items.filter { item -> when (selectedFilter) {
                    1 -> item.status == BudgetStatus.ACTIVE && item.consumption?.needsAttention(item.thresholdsBps) == true
                    2 -> item.status == BudgetStatus.ACTIVE && item.consumption?.isExceeded == true
                    else -> true
                } }
                if (visible.isEmpty()) item { Text("No hay presupuestos en este filtro.", Modifier.padding(16.dp)) }
                items(visible, key = { it.id }) { item ->
                    BudgetSummaryCard(item, onOpen = { onOpenBudget(item.id) }, onOptions = { optionsId = item.id })
                }
                item { Box(Modifier.height(20.dp)) }
            }
        }
    }

    if (showQuotaDialog) {
        BudgetQuotaDialog(
            limit = quota?.limit ?: 2,
            onDismiss = { showQuotaDialog = false },
            onSeePlans = { showQuotaDialog = false; onSeePlans() },
        )
    }
    (state as? BudgetListUiState.Content)?.items?.firstOrNull { it.id == optionsId }?.let { item ->
        BudgetActionsSheet(
            item.name, item.status == BudgetStatus.ACTIVE, { optionsId = null },
            { onOpenBudget(item.id) }, { onEditBudget(item.id) },
            { onFinishBudget(item.id) }, { onDeleteBudget(item.id) },
            summary = { item.consumption?.let { BudgetMoney(it.consumedMinor, item.currency) } },
        )
    }
}

@Composable
internal fun BudgetErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, tag: String) {
    Column(
        modifier.fillMaxSize().padding(24.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp).testTag("budget_retry")) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun CreateButton(atLimit: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_create_button"),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(if (atLimit) Icons.Default.Lock else Icons.Default.Add, contentDescription = null)
        Text(if (atLimit) "  Nuevo presupuesto · Ver planes" else "  Crear presupuesto", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuotaCard(quota: BudgetQuotaState) {
    if (quota.isPremium) return
    val colors = rememberCalmEmeraldColors()
    KipuCard(modifier = Modifier.fillMaxWidth().testTag("budget_quota_card"), shape = RoundedCornerShape(24.dp), borderColor = null, elevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PLAN FREE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("${quota.activeCount} de ${quota.limit} presupuestos activos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { if (quota.limit > 0) (quota.activeCount.toFloat() / quota.limit).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = if (quota.isAtLimit) colors.warningAmber else MaterialTheme.colorScheme.primary)
            if (quota.isAtLimit) Text("Límite alcanzado", color = colors.warningText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyBudgets() {
    KipuCard(
        modifier = Modifier.fillMaxWidth().testTag("budget_empty"),
        shape = RoundedCornerShape(20.dp),
        borderColor = null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
            }
            Text("Todavía no tienes presupuestos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Pon un límite de gasto por periodo, para todo o para una categoría, y mira cuánto llevas. " +
                    "Un presupuesto es solo planificación: no mueve dinero.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BudgetSummaryCard(item: BudgetListItem, onOpen: () -> Unit, onOptions: () -> Unit) {
    val finished = item.status == BudgetStatus.FINISHED
    val colors = rememberCalmEmeraldColors()
    KipuCard(
        modifier = Modifier.fillMaxWidth().testTag("budget_card_${item.id}")
            .clickable(role = Role.Button, onClickLabel = "Abrir detalle de ${item.name}", onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        borderColor = null,
        elevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = when {
                    item.consumption?.isExceeded == true -> colors.expenseBg
                    item.consumption?.needsAttention(item.thresholdsBps) == true -> colors.warningBg
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }) {
                    Icon(Icons.Default.PieChart, null, modifier = Modifier.padding(12.dp).size(24.dp), tint = when {
                        item.consumption?.isExceeded == true -> colors.expenseText
                        item.consumption?.needsAttention(item.thresholdsBps) == true -> colors.warningText
                        else -> MaterialTheme.colorScheme.primary
                    })
                }
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            item.scopeLabel,
                            BudgetTexts.periodTypeShort(item.periodType),
                            if (finished) "Finalizado" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOptions, modifier = Modifier.testTag("budget_options_${item.id}")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${item.name}")
                }
            }
            item.interval?.let {
                Text(
                    "Periodo: ${BudgetTexts.interval(it)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val consumption = item.consumption
            if (consumption != null) {
                BudgetAmountsRow(consumption, item.currency)
                BudgetProgressBar(consumption, item.currency, thresholdsBps = item.thresholdsBps)
            } else {
                Text("Sin periodo disponible todavía.", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                BudgetTexts.currencyNotice(item.currency),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.hasPendingChange) {
                Text(
                    "Hay un cambio de alcance o calendario programado para el siguiente periodo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
