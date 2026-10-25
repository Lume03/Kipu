package com.kipu.app.feature.budgets.presentation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.PlanningProgressRing
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDetailScreen(
    state: BudgetDetailUiState,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    initialAction: String? = null,
) {
    var showActions by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(initialAction == "finish") }
    var confirmDelete by rememberSaveable { mutableStateOf(initialAction == "delete") }
    val content = (state as? BudgetDetailUiState.Content)?.content
    var showCreated by rememberSaveable { mutableStateOf(initialAction == "created") }
    if (showCreated && content != null) {
        BudgetCreatedScreen(content, onView = { showCreated = false }, onReturn = onNavigateBack)
        return
    }

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
                    if (content != null) {
                        IconButton(onClick = { showActions = true }, enabled = !content.isWorking, modifier = Modifier.testTag("budget_actions")) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Acciones del presupuesto")
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
                onEdit = onEdit,
                onAskFinish = { confirmFinish = true },
                onAskDelete = { confirmDelete = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showActions && content != null) {
        BudgetActionsSheet(
            content.budget.name, content.isActive, { showActions = false }, {}, onEdit,
            { confirmFinish = true }, { confirmDelete = true },
            summary = { content.current?.let { BudgetMoney(it.consumption.consumedMinor, content.budget.currency) } },
        )
    }

    val name = content?.budget?.name.orEmpty()
    if (confirmFinish && content?.isActive == true) {
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
    if (confirmDelete && content != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.testTag("budget_delete_dialog"),
            icon = {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp).size(32.dp))
                }
            },
            title = { Text("¿Eliminar «$name»?", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Esta acción es irreversible. Se borrará la planificación: el presupuesto «$name» y todos " +
                            "sus periodos e historial. NO se borra ningún movimiento, gasto, cuenta ni categoría; " +
                            "tu dinero y tus registros quedan intactos.",
                    )
                    content?.current?.let {
                        KipuCard(containerColor = MaterialTheme.colorScheme.surfaceVariant, borderColor = null) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Llevabas", style = MaterialTheme.typography.labelMedium)
                                BudgetAmountsRow(it.consumption, content.budget.currency)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete() },
                    enabled = content?.isWorking != true,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
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
    onEdit: () -> Unit,
    onAskFinish: () -> Unit,
    onAskDelete: () -> Unit,
    modifier: Modifier,
) {
    val budget = content.budget
    val colors = rememberCalmEmeraldColors()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KipuCard(
            Modifier.fillMaxWidth().testTag("budget_current_card"),
            shape = RoundedCornerShape(18.dp), borderColor = null, elevation = 1.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val current = content.current
                if (current != null) {
                    Text(
                        (if (current.isCurrent) "Periodo actual: " else "Último periodo: ") + BudgetTexts.interval(current.interval),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally).semantics { heading() },
                    )
                    val consumption = current.consumption
                    val ringColor = when {
                        consumption.isExceeded -> colors.expenseCoral
                        consumption.percentBps >= 8000 && consumption.needsAttention(budget.thresholdsBps) -> colors.nearLimitOrange
                        consumption.remainingMinor == 0L || consumption.needsAttention(budget.thresholdsBps) -> colors.warningAmber
                        else -> MaterialTheme.colorScheme.primary
                    }
                    PlanningProgressRing(percent = consumption.percentWhole.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), label = "consumido", color = ringColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp).testTag("budget_progress"))
                    Text(consumption.statusLabel(budget.thresholdsBps), modifier = Modifier.align(Alignment.CenterHorizontally), fontWeight = FontWeight.SemiBold,
                        color = when {
                            consumption.isExceeded -> colors.expenseText
                            consumption.needsAttention(budget.thresholdsBps) -> colors.warningText
                            else -> MaterialTheme.colorScheme.primary
                        })
                    BudgetDetailAmounts(consumption, budget.currency, budget.thresholdsBps)

                } else {
                    Text("Sin periodo disponible todavía.")
                }
                content.pendingChangeLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("budget_pending_change"))
                }
            }
        }

        content.current?.let { current ->
            val consumption = current.consumption
            val warning = consumption.needsAttention(budget.thresholdsBps)
            Surface(shape = RoundedCornerShape(14.dp),
                color = when { consumption.isExceeded -> colors.expenseBg; warning -> colors.warningBg; else -> colors.incomeBg },
                contentColor = when { consumption.isExceeded -> colors.expenseText; warning -> colors.warningText; else -> colors.incomeText }) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when {
                        !content.isActive -> "Presupuesto finalizado"
                        consumption.isExceeded -> "Superaste el límite del periodo"
                        consumption.remainingMinor == 0L -> "Alcanzaste tu límite"
                        warning -> "Atención a tus gastos"
                        else -> "Vas dentro de tu presupuesto"
                    }, fontWeight = FontWeight.Bold)
                    Text(if (!content.isActive) "El historial se conserva; no habrá nuevas renovaciones ni alertas."
                        else "El consumo incluye los gastos confirmados que cumplen el alcance y la moneda del presupuesto.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        KipuCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), borderColor = null) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BudgetInfoLine("Qué gastos cuentan", content.scopeLabel)
                BudgetInfoLine("Periodo", BudgetTexts.schedule(budget.schedule))
                BudgetInfoLine("Moneda", budget.currency)
                BudgetInfoLine("Renovación", when {
                    !content.isActive -> "Finalizado"
                    budget.schedule is com.kipu.app.feature.budgets.domain.BudgetSchedule.CustomRange -> "No se renueva"
                    else -> content.current?.interval?.endExclusive?.let { "$it · automática" } ?: "Automática"
                })
                Text(BudgetTexts.currencyNotice(budget.currency), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                Surface(shape = RoundedCornerShape(8.dp), color = colors.incomeBg, contentColor = colors.incomeText) {
                    Text("Solo planificación · no mueve dinero", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        KipuCard(Modifier.fillMaxWidth().testTag("budget_alerts"), shape = RoundedCornerShape(18.dp), borderColor = null, elevation = 1.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Umbrales de alerta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (budget.thresholdsBps.isEmpty()) Text("Sin umbrales configurados")
                content.current?.let { BudgetProgressBar(it.consumption, budget.currency, thresholdsBps = budget.thresholdsBps) }
                budget.thresholdsBps.sorted().forEach { threshold ->
                    val reached = content.current?.consumption?.percentBps?.let { it >= threshold }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(BudgetTexts.thresholds(listOf(threshold)), fontWeight = FontWeight.SemiBold,
                            color = if (threshold >= 10000) colors.expenseText else colors.warningText)
                        Text(when {
                            !content.isActive -> "Desactivado"
                            reached == true -> "Alcanzado"
                            reached == false -> "Por alcanzar"
                            else -> "Sin periodo"
                        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("El estado indica el consumo del periodo; no confirma el envío de una notificación.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                KipuCard(
                    Modifier.fillMaxWidth().testTag("budget_history_${row.periodId}"),
                    shape = RoundedCornerShape(16.dp),
                    borderColor = null,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(BudgetTexts.interval(row.interval), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${row.scopeLabel} · límite de ese periodo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BudgetDetailAmounts(row.consumption, budget.currency, budget.thresholdsBps)
                        BudgetProgressBar(row.consumption, budget.currency, thresholdsBps = budget.thresholdsBps)
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
            Button(onClick = onEdit, enabled = !content.isWorking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_edit"), shape = RoundedCornerShape(14.dp)) { Text("Editar presupuesto") }
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
