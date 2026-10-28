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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.core.finance.domain.model.Currency
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
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
import com.kipu.app.ui.theme.rememberPlanningColors

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
            content.budget.name, content.isActive, { showActions = false }, { showActions = false }, { showActions = false; onEdit() },
            { showActions = false; confirmFinish = true }, { showActions = false; confirmDelete = true },
            summary = { content.current?.let { BudgetMoney(it.consumption.consumedMinor, content.budget.currency) } },
        )
    }

    if (confirmFinish && content?.isActive == true) {
        BudgetFinishDialog(content, onConfirm = { confirmFinish = false; onFinish() }, onCancel = { confirmFinish = false })
    }
    if (confirmDelete && content != null) {
        BudgetDeleteDialog(content,
            onDelete = { confirmDelete = false; onDelete() },
            onCancel = { confirmDelete = false },
            onKeepHistory = if (content.isActive) ({ confirmDelete = false; confirmFinish = true }) else null,
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
    val colors = rememberPlanningColors()
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
                    val today = LocalDate.now()
                    val days = if (content.isActive && today in current.interval) ChronoUnit.DAYS.between(today, current.interval.endExclusive) else null
                    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = colors.pillTrack) {
                            Text(BudgetTexts.periodTypeShort(budget.schedule.type), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                        }
                        Text(BudgetTexts.compactInterval(current.interval) + (days?.let { " · quedan $it ${if (it == 1L) "día" else "días"}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
                    }
                    val consumption = current.consumption
                    val ringColor = when {
                        consumption.isExceeded -> colors.expenseCoral
                        consumption.nearLimit(budget.thresholdsBps) -> colors.nearLimitOrange
                        consumption.remainingMinor == 0L || consumption.needsAttention(budget.thresholdsBps) -> colors.warningAmber
                        else -> MaterialTheme.colorScheme.primary
                    }
                    PlanningProgressRing(percent = consumption.percentWhole.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), label = "consumido", color = ringColor,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp).testTag("budget_progress"))
                    // The period state as a compact pill under the ring.
                    val attention = consumption.isExceeded || consumption.remainingMinor == 0L || consumption.needsAttention(budget.thresholdsBps)
                    val pillText = if (consumption.isExceeded) colors.expenseText else if (attention) colors.warningText else colors.primaryDeep
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (consumption.isExceeded) colors.expenseBg else if (attention) colors.warningBg else colors.incomeBg,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(if (attention) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null, tint = pillText,
                                modifier = Modifier.size(16.dp))
                            Text(consumption.statusLabel(budget.thresholdsBps), style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold, color = pillText)
                        }
                    }
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
            val pace = budgetPace(current, content.isActive, LocalDate.now())
            if (pace != null) {
                val masked = LocalBalanceMasked.current
                fun amount(value: Long): String = if (masked) "••••" else "${Currency.fromCode(budget.currency).symbol()} ${formatMinorUnits(value)}"
                Surface(shape = RoundedCornerShape(14.dp), color = colors.warningBg, contentColor = colors.warningText) {
                    Text("Vas a ritmo de pasarte por ${amount(pace.excess)}. Gastas ${amount(pace.daily)} al día; para llegar al ${current.interval.lastDay.dayOfMonth} puedes gastar hasta ${amount(pace.remainingDaily)} al día.",
                        Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!content.isActive) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.pillTrack, contentColor = colors.primaryText) {
                Text("Finalizado · conserva su historial", Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.SemiBold)
            }
        }
        KipuCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), borderColor = null) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Configuración del presupuesto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = colors.borderSubtle)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetIconBadge(content.iconId, colors.primaryDeep, colors.incomeBg)
                    Text("Categoría", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(content.scopeLabel.substringAfterLast(" › "), fontWeight = FontWeight.Bold)
                        if (content.isParentCategory) Text("todas sus subcategorías", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    }
                }
                HorizontalDivider(color = colors.borderSubtle)
                BudgetInfoLine("Moneda", if (budget.currency == "PEN") "Soles · PEN" else "Dólares · USD")
                HorizontalDivider(color = colors.borderSubtle)
                BudgetInfoLine("Se renueva", when {
                    !content.isActive -> "Finalizado"
                    budget.schedule is com.kipu.app.feature.budgets.domain.BudgetSchedule.CustomRange -> "No se renueva"
                    else -> content.current?.interval?.endExclusive?.let { it.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("es-PE"))) + " · automático" } ?: "Automático"
                })
                HorizontalDivider(color = colors.borderSubtle)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("No mueve dinero", color = colors.secondaryMuted, style = MaterialTheme.typography.bodyMedium)
                    Surface(shape = RoundedCornerShape(8.dp), color = colors.incomeBg) {
                        Text("Solo planificación", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = colors.incomeText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        KipuCard(Modifier.fillMaxWidth().testTag("budget_alerts"), shape = RoundedCornerShape(18.dp), borderColor = null, elevation = 1.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Alertas activadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (budget.thresholdsBps.isEmpty()) Text("Sin umbrales configurados")
                else {
                    val marks = (listOf(0) + budget.thresholdsBps + 10000).distinct().sorted()
                    Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                        marks.zipWithNext().forEachIndexed { index, (a, b) ->
                            val color = when (index) { 0 -> colors.incomeEmerald; 1 -> colors.warningAmber; else -> colors.expenseCoral }
                            Box(Modifier.weight((b - a).toFloat()).height(8.dp).background(color))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        marks.forEach { Text("${it / 100}%", style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted) }
                    }
                    budget.thresholdsBps.sorted().forEachIndexed { index, threshold ->
                        val reached = currentReached(content.current, threshold)
                        val color = if (threshold >= 10000) colors.expenseText else colors.warningText
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(color, CircleShape))
                            Text((if (threshold >= 10000) "Superar el" else if (index == 0) "Atención al" else "Casi al límite al") + " ${threshold / 100}%", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(if (reached == null) "Sin periodo" else if (reached) "Alcanzada" else "Pendiente", color = if (reached == true) color else colors.secondaryMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("Estado del consumo; no confirma el envío.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            }
        }
        var showAll by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("budget_history")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Periodos anteriores", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (content.history.size > 3) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Ver menos" else "Ver todos") }
            }
            if (content.history.isEmpty()) Text("Aún no hay periodos anteriores.", Modifier.testTag("budget_history_empty"), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
            (if (showAll) content.history else content.history.take(3)).forEach { row ->
                val consumption = row.consumption
                val exceeded = consumption.isExceeded
                KipuCard(Modifier.fillMaxWidth().testTag("budget_history_${row.periodId}"), shape = RoundedCornerShape(16.dp), borderColor = null) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val title = if (budget.schedule.type == com.kipu.app.feature.budgets.domain.BudgetPeriodType.MONTHLY)
                            row.interval.start.format(DateTimeFormatter.ofPattern("LLLL", Locale.forLanguageTag("es-PE"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-PE")) }
                        else BudgetTexts.interval(row.interval)
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BudgetMoney(consumption.consumedMinor, budget.currency, style = MaterialTheme.typography.bodySmall)
                            Text(" de ", style = MaterialTheme.typography.bodySmall)
                            BudgetMoney(consumption.limitMinor, budget.currency, style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(progress = { (consumption.percentBps / 10000f).coerceIn(0f, 1f) },
                            color = if (exceeded) colors.expenseCoral else colors.incomeEmerald, trackColor = colors.pillTrack,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), drawStopIndicator = {})
                        Surface(shape = RoundedCornerShape(20.dp), color = if (exceeded) colors.expenseBg else colors.incomeBg) {
                            Text("${if (exceeded) "▲ Excedido" else "✓ Cumplido" } · ${consumption.percentWhole}%", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = if (exceeded) colors.expenseText else colors.incomeText, style = MaterialTheme.typography.labelMedium)
                        }
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
        // Finish and delete live in the ⋮ menu; the screen keeps one primary action.
        if (content.isActive) {
            Button(onClick = onEdit, enabled = !content.isWorking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("budget_edit"), shape = RoundedCornerShape(14.dp)) { Text("Editar") }
        }
    }
}

private fun currentReached(current: BudgetPeriodRow?, threshold: Int): Boolean? =
    current?.consumption?.percentBps?.let { it >= threshold }

internal data class BudgetPace(val excess: Long, val daily: Long, val remainingDaily: Long)

internal fun budgetPace(current: BudgetPeriodRow, active: Boolean, today: LocalDate): BudgetPace? {
    if (!active || today !in current.interval) return null
    val elapsed = ChronoUnit.DAYS.between(current.interval.start, today) + 1
    val remaining = ChronoUnit.DAYS.between(today, current.interval.endExclusive) - 1
    if (remaining <= 0) return null
    val consumption = current.consumption
    val daily = BigDecimal.valueOf(consumption.consumedMinor).divide(BigDecimal.valueOf(elapsed), 4, RoundingMode.HALF_UP)
    val projected = daily.multiply(BigDecimal.valueOf(elapsed + remaining))
    val excess = projected.subtract(BigDecimal.valueOf(consumption.limitMinor))
    if (excess.signum() <= 0) return null
    fun rounded(value: BigDecimal) = value.setScale(0, RoundingMode.HALF_UP).coerceAtMost(BigDecimal.valueOf(Long.MAX_VALUE)).toLong()
    return BudgetPace(rounded(excess), rounded(daily), rounded(BigDecimal.valueOf(consumption.remainingMinor.coerceAtLeast(0)).divide(BigDecimal.valueOf(remaining), 4, RoundingMode.HALF_UP)))
}
