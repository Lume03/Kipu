package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.budgets.domain.BudgetStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetListScreen(
    state: BudgetListUiState,
    onNavigateBack: () -> Unit,
    onCreateBudget: () -> Unit,
    onOpenBudget: (String) -> Unit,
    onRetry: () -> Unit,
    onSeePlans: () -> Unit,
) {
    var showQuotaDialog by rememberSaveable { mutableStateOf(false) }
    val quota = when (state) {
        is BudgetListUiState.Content -> state.quota
        is BudgetListUiState.Empty -> state.quota
        else -> null
    }
    val requestCreate = {
        if (quota?.isAtLimit == true) showQuotaDialog = true else onCreateBudget()
    }

    Scaffold(
        modifier = Modifier.testTag("budget_list_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text("Presupuestos", fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
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
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { QuotaCard(state.quota) }
                item { CreateButton(state.quota.isAtLimit, requestCreate) }
                items(state.items, key = { it.id }) { item ->
                    BudgetSummaryCard(item, onOpen = { onOpenBudget(item.id) })
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
        Icon(Icons.Default.Add, contentDescription = null)
        Text(if (atLimit) "  Crear presupuesto (límite Free)" else "  Crear presupuesto", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuotaCard(quota: BudgetQuotaState) {
    if (quota.isPremium) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("budget_quota_card"),
    ) {
        Text(
            "${quota.activeCount.coerceAtMost(quota.limit)} de ${quota.limit} presupuestos activos en el plan Free" +
                if (quota.isAtLimit) ". Límite alcanzado." else ".",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyBudgets() {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("budget_empty"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
private fun BudgetSummaryCard(item: BudgetListItem, onOpen: () -> Unit) {
    val finished = item.status == BudgetStatus.FINISHED
    Card(
        modifier = Modifier.fillMaxWidth().testTag("budget_card_${item.id}")
            .clickable(role = Role.Button, onClickLabel = "Abrir detalle de ${item.name}", onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                BudgetProgressBar(consumption, item.currency)
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
