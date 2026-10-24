package com.kipu.app.feature.budgets.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDetailScreen(
    state: BudgetDetailUiState,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val content = (state as? BudgetDetailUiState.Content)?.content

    Scaffold(
        modifier = Modifier.testTag("budget_detail_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        content?.budget?.name ?: "Presupuesto",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (content?.isActive == true) {
                        IconButton(onClick = onEdit, modifier = Modifier.testTag("budget_edit")) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar presupuesto")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            BudgetDetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Cargando presupuesto" })
            }
            BudgetDetailUiState.NotFound -> BudgetErrorPanel(
                "Este presupuesto ya no existe.", onNavigateBack, Modifier.padding(padding), "budget_detail_not_found",
            )
            is BudgetDetailUiState.Error -> BudgetErrorPanel(state.message, onRetry, Modifier.padding(padding), "budget_detail_error")
            is BudgetDetailUiState.Content -> DetailContent(
                state.content,
                onAskFinish = { confirmFinish = true },
                onAskDelete = { confirmDelete = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    val name = content?.budget?.name.orEmpty()
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            modifier = Modifier.testTag("budget_finish_dialog"),
            title = { Text("Finalizar presupuesto") },
            text = {
                Text(
                    "«$name» dejará de renovarse y de enviar alertas. Conservas su historial de periodos y " +
                        "podrás seguir consultándolo. Libera un cupo de presupuestos activos del plan Free.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; onFinish() }, modifier = Modifier.testTag("budget_finish_confirm")) {
                    Text("Finalizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }, modifier = Modifier.testTag("budget_finish_cancel")) { Text("Cancelar") }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.testTag("budget_delete_dialog"),
            title = { Text("Eliminar presupuesto") },
            text = {
                Text(
                    "Esta acción es irreversible. Se borrará la planificación: el presupuesto «$name» y todos " +
                        "sus periodos e historial. NO se borra ningún movimiento, gasto, cuenta ni categoría; " +
                        "tu dinero y tus registros quedan intactos.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("budget_delete_confirm"),
                ) { Text("Eliminar definitivamente") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("budget_delete_cancel")) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun DetailContent(
    content: BudgetDetailContent,
    onAskFinish: () -> Unit,
    onAskDelete: () -> Unit,
    modifier: Modifier,
) {
    val budget = content.budget
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            Modifier.fillMaxWidth().testTag("budget_current_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    listOf(
                        content.scopeLabel,
                        BudgetTexts.schedule(budget.schedule),
                        if (!content.isActive) "Finalizado" else null,
                    ).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val current = content.current
                if (current != null) {
                    Text(
                        (if (current.isCurrent) "Periodo actual: " else "Último periodo: ") + BudgetTexts.interval(current.interval),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    BudgetAmountsRow(current.consumption, budget.currency)
                    BudgetProgressBar(current.consumption, budget.currency)
                } else {
                    Text("Sin periodo disponible todavía.")
                }
                Text(
                    BudgetTexts.currencyNotice(budget.currency),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Alertas en: ${BudgetTexts.thresholds(budget.thresholdsBps)}" +
                        if (content.isActive) "" else " (desactivadas: presupuesto finalizado)",
                    style = MaterialTheme.typography.labelMedium,
                )
                content.pendingChangeLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("budget_pending_change"))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("budget_history")) {
            Text("Periodos anteriores", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            if (content.history.isEmpty()) {
                Text(
                    "Aún no hay periodos anteriores.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("budget_history_empty"),
                )
            }
            content.history.forEach { row ->
                Card(
                    Modifier.fillMaxWidth().testTag("budget_history_${row.periodId}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(BudgetTexts.interval(row.interval), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${row.scopeLabel} · límite de ese periodo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BudgetAmountsRow(row.consumption, budget.currency)
                        BudgetProgressBar(row.consumption, budget.currency)
                    }
                }
            }
        }

        content.actionError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("budget_action_error").semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        HorizontalDivider()
        if (content.isActive) {
            OutlinedButton(
                onClick = onAskFinish,
                enabled = !content.isWorking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_finish"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Flag, contentDescription = null)
                Text("  Finalizar presupuesto")
            }
        }
        Button(
            onClick = onAskDelete,
            enabled = !content.isWorking,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_delete"),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Text("  Eliminar presupuesto")
        }
    }
}
