package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.debts.domain.model.*
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.rememberPlanningColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val upcomingDueDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingDuesScreen(
    state: UpcomingDuesUiState,
    onNavigateBack: () -> Unit,
    onOriginSelected: (DueOriginFilter) -> Unit,
    onStatusSelected: (DueStatusFilter) -> Unit,
    onTypeSelected: (DebtObligationType?) -> Unit,
    onCurrencySelected: (Currency?) -> Unit,
    onOpenDebt: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    onSettleDebt: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberPlanningColors()
    var paidExpanded by rememberSaveable(state.ownerId) { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.testTag("upcoming_dues_screen"), containerColor = colors.background,
        topBar = {
            TopAppBar(title = { Text("Próximos vencimientos") }, navigationIcon = {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            })
        },
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("summary") {
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.borderSubtle)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saldo pendiente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (state.payableTotals.isEmpty() && state.receivableTotals.isEmpty()) Text("Sin saldo pendiente en esta selección", color = colors.secondaryMuted)
                        state.payableTotals.forEach { money ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Por pagar · ${money.currency.name}", Modifier.weight(1f)); MoneyText(money, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        state.receivableTotals.forEach { money ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Por cobrar · ${money.currency.name}", Modifier.weight(1f)); MoneyText(money, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text("${state.overdueCount} ${if (state.overdueCount == 1) "vencida" else "vencidas"}", color = if (state.overdueCount > 0) colors.expenseText else colors.secondaryMuted)
                        Text("Próximas: hoy y los siguientes 7 días", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    }
                }
            }
            item("filters") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UpcomingDuesFilterRow("Origen") {
                        DueOriginFilter.entries.forEach { origin ->
                            KipuFilterChip(state.origin == origin, { onOriginSelected(origin) }, label = { Text(origin.label) },
                                modifier = Modifier.semantics { contentDescription = "Origen: ${origin.label}" })
                        }
                    }
                    UpcomingDuesFilterRow("Estado") {
                        DueStatusFilter.entries.forEach { status ->
                            KipuFilterChip(state.status == status, { onStatusSelected(status) }, label = { Text(status.label) },
                                modifier = Modifier.semantics { contentDescription = "Estado: ${status.label}" })
                        }
                    }
                    UpcomingDuesFilterRow("Obligación") {
                        listOf<DebtObligationType?>(null, DebtObligationType.PAYABLE, DebtObligationType.RECEIVABLE).forEach { type ->
                            val label = when (type) { null -> "Pagar y cobrar"; DebtObligationType.PAYABLE -> "Por pagar"; DebtObligationType.RECEIVABLE -> "Por cobrar" }
                            KipuFilterChip(state.obligationType == type, { onTypeSelected(type) }, label = { Text(label) })
                        }
                    }
                    UpcomingDuesFilterRow("Moneda") {
                        (listOf<Currency?>(null) + Currency.entries).forEach { currency ->
                            KipuFilterChip(state.currency == currency, { onCurrencySelected(currency) }, label = { Text(currency?.name ?: "Todas las monedas") })
                        }
                    }
                }
            }
            when {
                state.isLoading -> item("loading") {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(); Text("Cargando vencimientos…") }
                }
                state.error != null -> item("error") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.error, color = colors.expenseText)
                        OutlinedButton(onClick = onRetry) { Text("Reintentar") }
                    }
                }
                state.rows.isEmpty() -> item("empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("upcoming_dues_empty"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EventAvailable, null, tint = colors.primaryDeep)
                        Text("Todo al día en esta selección", style = MaterialTheme.typography.titleMedium)
                        Text("Aquí verás los vencimientos de tus deudas y tarjetas.", color = colors.secondaryMuted)
                    }
                }
                else -> DuePeriod.entries.forEach { period ->
                    val rows = state.periods[period].orEmpty()
                    if (rows.isNotEmpty()) {
                        item("heading_${period.name}") {
                            if (period == DuePeriod.RECENTLY_PAID) TextButton(
                                onClick = { paidExpanded = !paidExpanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .semantics { stateDescription = if (paidExpanded) "Expandido" else "Contraído" },
                            ) { Text("${period.label} (${rows.size}) · últimos 30 días") }
                            else Text("${period.label} (${rows.size})", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        }
                        if (period != DuePeriod.RECENTLY_PAID || paidExpanded) items(rows, key = { it.id }) { row ->
                            UpcomingDueCard(row, state.today, onOpen = {
                                if (row.origin == DueOrigin.DEBT) onOpenDebt(row.destinationId) else onOpenCard(row.destinationId)
                            }, onSettle = { onSettleDebt(row.destinationId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingDuesFilterRow(label: String, content: @Composable RowScope.() -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun UpcomingDueCard(row: UpcomingDue, today: java.time.LocalDate, onOpen: () -> Unit, onSettle: () -> Unit) {
    val colors = rememberPlanningColors()
    val amountLabel = java.math.BigDecimal.valueOf(row.outstanding.minorUnits, 2).toPlainString()
    val installmentLabel = row.installmentNumber?.let { ", cuota $it de ${row.installmentCount}" }.orEmpty()
    val description = "${row.origin.label}, ${row.title}$installmentLabel, ${row.status.label}, vence ${row.dueDate.format(upcomingDueDateFormatter)}, pendiente $amountLabel ${row.outstanding.currency.name}"
    Surface(onClick = onOpen, shape = RoundedCornerShape(16.dp), color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderSubtle), modifier = Modifier.fillMaxWidth().testTag("upcoming_due_${row.id}")
            .semantics { contentDescription = description }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.origin.label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                val (background, foreground) = when (row.status) {
                    DueStatus.OVERDUE -> colors.expenseBg to colors.expenseText
                    DueStatus.UPCOMING -> colors.warningBg to colors.warningText
                    DueStatus.PAID -> colors.incomeBg to colors.incomeText
                    DueStatus.PENDING -> colors.pillTrack to colors.primaryText
                }
                Surface(shape = RoundedCornerShape(8.dp), color = background) {
                    Text(row.status.label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = foreground,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            row.installmentNumber?.let { Text("Cuota $it/${row.installmentCount}", style = MaterialTheme.typography.bodySmall) }
            row.description?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted) }
            Text("Vence ${row.dueDate.format(upcomingDueDateFormatter)}", style = MaterialTheme.typography.bodyMedium)
            Text(if (row.status == DueStatus.PAID) "Pagada el ${row.settledOn?.format(upcomingDueDateFormatter).orEmpty()}"
                else debtDueDayLabel(row.dueDate, today), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pendiente", Modifier.weight(1f)); MoneyText(row.outstanding, fontWeight = FontWeight.SemiBold)
            }
            if (row.status == DueStatus.PAID) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Total", Modifier.weight(1f)); MoneyText(row.total)
            }
            if (row.canSettle) FilledTonalButton(onClick = onSettle, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (row.obligationType == DebtObligationType.PAYABLE) "Registrar pago" else "Registrar cobro")
            }
        }
    }
}
