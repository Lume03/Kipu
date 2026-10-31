package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.debts.domain.model.*
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.rememberPlanningColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val upcomingDueDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))
private val dayFormatter = DateTimeFormatter.ofPattern("d", Locale.forLanguageTag("es-PE"))
private val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-PE"))

private data class DueStatusStyle(val container: Color, val content: Color, val icon: ImageVector)

private fun DueStatus.style(colors: CalmEmeraldColors): DueStatusStyle = when (this) {
    DueStatus.OVERDUE -> DueStatusStyle(colors.expenseBg, colors.expenseText, Icons.Outlined.ErrorOutline)
    DueStatus.UPCOMING -> DueStatusStyle(colors.warningBg, colors.warningText, Icons.Outlined.Schedule)
    DueStatus.PAID -> DueStatusStyle(colors.incomeBg, colors.incomeText, Icons.Outlined.CheckCircle)
    DueStatus.PENDING -> DueStatusStyle(colors.pillTrack, colors.secondaryMuted, Icons.Outlined.HourglassEmpty)
}

private fun DuePeriod.accent(colors: CalmEmeraldColors): Color = when (this) {
    DuePeriod.OVERDUE -> colors.expenseText
    DuePeriod.THIS_WEEK -> colors.warningText
    DuePeriod.RECENTLY_PAID -> colors.incomeText
    else -> colors.primaryDeep
}

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
    val secondaryFiltersActive = state.obligationType != null || state.currency != null
    var moreFilters by rememberSaveable(state.ownerId) { mutableStateOf(secondaryFiltersActive) }
    Scaffold(
        modifier = modifier.testTag("upcoming_dues_screen"), containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Próximos vencimientos", fontWeight = FontWeight.ExtraBold, color = colors.primaryText) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = colors.primaryText) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("summary") { DuesSummaryCard(state, colors) }
            item("filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipRow {
                        DueOriginFilter.entries.forEach { origin ->
                            KipuFilterChip(state.origin == origin, { onOriginSelected(origin) }, label = { Text(origin.label) },
                                modifier = Modifier.semantics { contentDescription = "Origen: ${origin.label}" })
                        }
                        val chevron by animateFloatAsState(if (moreFilters) 180f else 0f, label = "filters-chevron")
                        TextButton(
                            onClick = { moreFilters = !moreFilters },
                            modifier = Modifier.heightIn(min = 48.dp)
                                .semantics { stateDescription = if (moreFilters) "Expandido" else "Contraído" },
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (secondaryFiltersActive) "Más filtros •" else "Más filtros", color = colors.primaryDeep, fontWeight = FontWeight.Bold)
                            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.rotate(chevron))
                        }
                    }
                    ChipRow {
                        DueStatusFilter.entries.forEach { status ->
                            KipuFilterChip(state.status == status, { onStatusSelected(status) }, label = { Text(status.label) },
                                modifier = Modifier.semantics { contentDescription = "Estado: ${status.label}" })
                        }
                    }
                    AnimatedVisibility(moreFilters) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterLabel("Obligación", colors)
                            ChipRow {
                                listOf<DebtObligationType?>(null, DebtObligationType.PAYABLE, DebtObligationType.RECEIVABLE).forEach { type ->
                                    val label = when (type) { null -> "Pagar y cobrar"; DebtObligationType.PAYABLE -> "Por pagar"; DebtObligationType.RECEIVABLE -> "Por cobrar" }
                                    KipuFilterChip(state.obligationType == type, { onTypeSelected(type) }, label = { Text(label) })
                                }
                            }
                            FilterLabel("Moneda", colors)
                            ChipRow {
                                (listOf<Currency?>(null) + Currency.entries).forEach { currency ->
                                    KipuFilterChip(state.currency == currency, { onCurrencySelected(currency) }, label = { Text(currency?.name ?: "Todas las monedas") })
                                }
                            }
                        }
                    }
                }
            }
            when {
                state.isLoading -> item("loading") {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = colors.primaryDeep)
                        Text("Cargando vencimientos…", color = colors.secondaryMuted)
                    }
                }
                state.error != null -> item("error") {
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.expenseBg, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.error, color = colors.expenseText)
                            OutlinedButton(onClick = onRetry) { Text("Reintentar") }
                        }
                    }
                }
                state.rows.isEmpty() -> item("empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp).testTag("upcoming_dues_empty"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(64.dp).background(colors.incomeBg, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.EventAvailable, null, tint = colors.primaryDeep, modifier = Modifier.size(30.dp))
                        }
                        Text("Todo al día en esta selección", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = colors.primaryText)
                        Text("Aquí verás los vencimientos de tus deudas y tarjetas.", color = colors.secondaryMuted)
                    }
                }
                else -> DuePeriod.entries.forEach { period ->
                    val rows = state.periods[period].orEmpty()
                    if (rows.isNotEmpty()) {
                        item("heading_${period.name}") {
                            if (period == DuePeriod.RECENTLY_PAID) {
                                val chevron by animateFloatAsState(if (paidExpanded) 180f else 0f, label = "paid-chevron")
                                TextButton(
                                    onClick = { paidExpanded = !paidExpanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .semantics { stateDescription = if (paidExpanded) "Expandido" else "Contraído" },
                                ) {
                                    Text("${period.label} (${rows.size}) · últimos 30 días", Modifier.weight(1f),
                                        color = period.accent(colors), fontWeight = FontWeight.Bold)
                                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = period.accent(colors), modifier = Modifier.rotate(chevron))
                                }
                            } else {
                                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(period.label.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                                        color = period.accent(colors), modifier = Modifier.semantics { heading() })
                                    Surface(shape = CircleShape, color = colors.pillTrack) {
                                        Text("${rows.size}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.secondaryMuted)
                                    }
                                }
                            }
                        }
                        if (period != DuePeriod.RECENTLY_PAID || paidExpanded) items(rows, key = { it.id }) { row ->
                            UpcomingDueCard(row, state.today, Modifier.animateItem(), onOpen = {
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
private fun DuesSummaryCard(state: UpcomingDuesUiState, colors: CalmEmeraldColors) {
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceCard, border = BorderStroke(1.dp, colors.borderSubtle)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SALDO PENDIENTE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = colors.primaryDeep)
            if (state.payableTotals.isEmpty() && state.receivableTotals.isEmpty()) {
                Text("Sin saldo pendiente en esta selección", color = colors.secondaryMuted)
            }
            state.payableTotals.forEach { money ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Por pagar · ${money.currency.name}", style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                    MoneyText(money, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
                }
            }
            state.receivableTotals.forEach { money ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Por cobrar · ${money.currency.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
                    MoneyText(money, fontWeight = FontWeight.Bold, color = colors.incomeText)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val overdue = state.overdueCount > 0
                Surface(shape = RoundedCornerShape(50), color = if (overdue) colors.expenseBg else colors.incomeBg) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (overdue) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, contentDescription = null,
                            tint = if (overdue) colors.expenseText else colors.incomeText, modifier = Modifier.size(16.dp))
                        Text("${state.overdueCount} ${if (state.overdueCount == 1) "vencida" else "vencidas"}",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = if (overdue) colors.expenseText else colors.incomeText)
                    }
                }
                Text("Próximas: hoy y 7 días", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun FilterLabel(label: String, colors: CalmEmeraldColors) {
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.secondaryMuted)
}

@Composable
private fun UpcomingDueCard(
    row: UpcomingDue,
    today: java.time.LocalDate,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onSettle: () -> Unit,
) {
    val colors = rememberPlanningColors()
    val style = row.status.style(colors)
    val amountLabel = java.math.BigDecimal.valueOf(row.outstanding.minorUnits, 2).toPlainString()
    val installmentLabel = row.installmentNumber?.let { ", cuota $it de ${row.installmentCount}" }.orEmpty()
    val description = "${row.origin.label}, ${row.title}$installmentLabel, ${row.status.label}, vence ${row.dueDate.format(upcomingDueDateFormatter)}, pendiente $amountLabel ${row.outstanding.currency.name}"
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, if (row.status == DueStatus.OVERDUE) colors.expenseBorder else colors.borderSubtle),
        modifier = modifier.fillMaxWidth().testTag("upcoming_due_${row.id}").semantics { contentDescription = description },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Calendar block: day + month, tinted by status.
                Column(
                    Modifier.size(52.dp).background(style.container, RoundedCornerShape(14.dp)).clearAndSetSemantics { },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(row.dueDate.format(dayFormatter), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = style.content)
                    Text(row.dueDate.format(monthFormatter).uppercase().trimEnd('.'), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = style.content)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (row.origin == DueOrigin.CARD) Icons.Outlined.CreditCard else Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null, tint = colors.secondaryMuted, modifier = Modifier.size(14.dp))
                        Text(row.origin.label + (row.installmentNumber?.let { " · Cuota $it/${row.installmentCount}" }.orEmpty()),
                            style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
                    }
                    Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (row.status == DueStatus.PAID) "Pagada el ${row.settledOn?.format(upcomingDueDateFormatter).orEmpty()}"
                        else debtDueDayLabel(row.dueDate, today),
                        style = MaterialTheme.typography.bodySmall, color = if (row.status == DueStatus.OVERDUE) colors.expenseText else colors.secondaryMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyText(if (row.status == DueStatus.PAID) row.total else row.outstanding,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
                    Surface(shape = RoundedCornerShape(50), color = style.container) {
                        Row(Modifier.padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(style.icon, contentDescription = null, tint = style.content, modifier = Modifier.size(12.dp))
                            Text(row.status.label, color = style.content, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            row.description?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (row.canSettle) FilledTonalButton(
                onClick = onSettle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.incomeBg, contentColor = colors.primaryDeep),
            ) {
                Text(if (row.obligationType == DebtObligationType.PAYABLE) "Registrar pago" else "Registrar cobro", fontWeight = FontWeight.Bold)
            }
        }
    }
}
