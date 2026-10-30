package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.component.FilterOption
import com.kipu.app.ui.component.OptionFilterButton
import com.kipu.app.ui.component.OptionFilterSheet
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.rememberPlanningColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun DebtListScreen(
    debts: List<DebtSummary>,
    selectedType: DebtObligationType?,
    onTypeSelected: (DebtObligationType?) -> Unit,
    onDebtSelected: (String) -> Unit,
    onAddPayable: () -> Unit,
    onAddReceivable: () -> Unit,
    scheduledInstallments: Map<String, DebtScheduleItem> = emptyMap(),
    errorMessage: String? = null,
    openRegisterDebt: Boolean = false,
    onConsumeRegisterDebt: () -> Unit = {},
    embedded: Boolean = false,
    freeLimitReached: Boolean = false,
    activeDebtCount: Int = debts.count { it.status == DebtLifecycleStatus.ACTIVE },
    onSeePlans: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    onDebtAction: (String, String) -> Unit = { id, _ -> onDebtSelected(id) },
    isLoading: Boolean = false,
    allInstallments: Map<String, List<DebtScheduleItem>> = emptyMap(),
) {
    var optionsId by rememberSaveable { mutableStateOf<String?>(null) }
    var showClosed by rememberSaveable { mutableStateOf(false) }
    val colors = rememberPlanningColors()
    val filtered = remember(debts, selectedType) {
        debts.filter { selectedType == null || it.obligationType == selectedType }
    }
    var showRegisterTypeSheet by rememberSaveable { mutableStateOf(false) }
    var showLimitSheet by rememberSaveable { mutableStateOf(false) }
    var showDueAlerts by rememberSaveable { mutableStateOf(false) }
    // Every entry point (root + and empty state) passes through the Basic plan check first.
    val requestCreate: (DebtObligationType?) -> Unit = { type ->
        when {
            freeLimitReached -> showLimitSheet = true
            type == DebtObligationType.PAYABLE -> onAddPayable()
            type == DebtObligationType.RECEIVABLE -> onAddReceivable()
            else -> showRegisterTypeSheet = true
        }
    }

    LaunchedEffect(openRegisterDebt) {
        if (openRegisterDebt) {
            onConsumeRegisterDebt()
            requestCreate(null)
        }
    }

    Scaffold(
        modifier = Modifier.testTag("debt_list_screen"),
        contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        containerColor = if (embedded) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!embedded) {
                Text(
                    "Deudas y préstamos",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("debt_items"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            errorMessage?.let { message ->
                item(key = "error") {
                    Surface(shape = RoundedCornerShape(12.dp), color = colors.expenseBg, modifier = Modifier.fillMaxWidth()) {
                        Text(message, color = colors.expenseText, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item(key = "summary") { DebtTotalsCard(debts) }
            item(key = "filters") {
                DebtFilterRow(
                    selectedType = selectedType,
                    onTypeSelected = onTypeSelected,
                )
            }
            if (debts.any { it.status == DebtLifecycleStatus.ACTIVE && (it.dueDate != null || allInstallments[it.debtId].orEmpty().isNotEmpty()) }) {
                item(key = "due_alerts") {
                    androidx.compose.material3.TextButton(onClick = { showDueAlerts = true }, modifier = Modifier.testTag("debt_due_open")) { Text("Ver vencimientos") }
                }
            }
            when {
                isLoading && debts.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                debts.isEmpty() -> {
                    item(key = "empty") { DebtEmptyCard(onRegister = { requestCreate(null) }) }
                    item(key = "empty_types") {
                        DebtTypeShortcuts(
                            onPayable = { requestCreate(DebtObligationType.PAYABLE) },
                            onReceivable = { requestCreate(DebtObligationType.RECEIVABLE) },
                        )
                    }
                }
                filtered.isEmpty() -> item(key = "filter_empty") {
                    Text(
                        "No tienes registros en este filtro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
                else -> {
                    // Settled or cancelled debts fold away like finished budgets and archived goals.
                    val (open, closed) = filtered.partition { it.status == DebtLifecycleStatus.ACTIVE }
                    items(open, key = DebtSummary::debtId) { debt ->
                        DebtListItem(
                            debt = debt,
                            scheduledInstallment = scheduledInstallments[debt.debtId],
                            today = today,
                            onClick = { onDebtSelected(debt.debtId) },
                            onOptions = { optionsId = debt.debtId },
                        )
                    }
                    if (closed.isNotEmpty()) {
                        item(key = "closed_header") {
                            Surface(
                                onClick = { showClosed = !showClosed },
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, colors.borderSubtle),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().testTag("debt_closed_section")
                                    .semantics { stateDescription = if (showClosed) "Expandido" else "Contraído" },
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.secondaryMuted)
                                    Text("Saldadas (${closed.size})", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Icon(if (showClosed) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                                }
                            }
                        }
                        if (showClosed) {
                            items(closed, key = DebtSummary::debtId) { debt ->
                                DebtListItem(
                                    debt = debt,
                                    scheduledInstallment = scheduledInstallments[debt.debtId],
                                    today = today,
                                    onClick = { onDebtSelected(debt.debtId) },
                                    onOptions = { optionsId = debt.debtId },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDueAlerts) DebtDueAlertsSheet(debts, allInstallments, today, { showDueAlerts = false }, onDebtSelected,
        { onDebtAction(it, "settle") })
    debts.firstOrNull { it.debtId == optionsId }?.let { debt ->
        DebtActionsSheet(debt, { optionsId = null }, { onDebtSelected(debt.debtId) },
            { onDebtAction(debt.debtId, "settle") }, { onDebtAction(debt.debtId, "history") },
            { onDebtAction(debt.debtId, "edit") }, { onDebtAction(debt.debtId, "close") },
            { onDebtAction(debt.debtId, "manage") })
    }
    if (showRegisterTypeSheet) {
        DebtCreationTypeSheet(
            onDismissRequest = { showRegisterTypeSheet = false },
            onTypeSelected = { type ->
                showRegisterTypeSheet = false
                if (type == DebtObligationType.PAYABLE) onAddPayable() else onAddReceivable()
            },
        )
    }
    if (showLimitSheet) {
        DebtPlanLimitSheet(
            activeCount = activeDebtCount,
            limit = DebtListUiState.FREE_ACTIVE_DEBT_LIMIT,
            onDismiss = { showLimitSheet = false },
            onSeePlans = {
                showLimitSheet = false
                onSeePlans()
            },
            onSeeActiveDebts = {
                showLimitSheet = false
                onTypeSelected(null)
            },
        )
    }
}

@Composable
private fun DebtTotalsCard(debts: List<DebtSummary>) {
    val colors = rememberPlanningColors()
    val active = debts.filter { it.status == DebtLifecycleStatus.ACTIVE }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("debt_totals"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(16.dp)) {
            DebtTotal(
                label = "Debes",
                totals = active.totalsFor(DebtObligationType.PAYABLE),
                accent = colors.expenseText,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(Modifier.fillMaxHeight().padding(horizontal = 14.dp), color = colors.borderSubtle)
            DebtTotal(
                label = "Te deben",
                totals = active.totalsFor(DebtObligationType.RECEIVABLE),
                accent = colors.incomeText,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DebtTotal(label: String, totals: Map<Currency, Long>, accent: Color, modifier: Modifier = Modifier) {
    val colors = rememberPlanningColors()
    // PEN leads; other currencies are listed below instead of being mixed into one sum.
    val primary = totals[Currency.PEN] ?: 0L
    val others = totals.filterKeys { it != Currency.PEN }.filterValues { it > 0L }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        MoneyText(
            minorUnits = primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (primary > 0L) accent else MaterialTheme.colorScheme.onSurface,
        )
        others.forEach { (currency, minor) ->
            MoneyText(minorUnits = minor, currency = currency, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun DebtFilterRow(
    selectedType: DebtObligationType?,
    onTypeSelected: (DebtObligationType?) -> Unit,
) {
    val colors = rememberPlanningColors()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val options = listOf(
        FilterOption<DebtObligationType?>(null, "Todas", Icons.Default.AccountBalance, colors.primaryDeep, "debt_filter_all"),
        FilterOption<DebtObligationType?>(DebtObligationType.PAYABLE, "Debo", Icons.AutoMirrored.Filled.CallMade, colors.expenseText, "debt_filter_payable"),
        FilterOption<DebtObligationType?>(DebtObligationType.RECEIVABLE, "Me deben", Icons.AutoMirrored.Filled.CallReceived, colors.incomeText, "debt_filter_receivable"),
    )
    OptionFilterButton(
        label = options.first { it.value == selectedType }.label,
        active = selectedType != null,
        description = "Filtrar deudas: ${options.first { it.value == selectedType }.label}",
        onClick = { showFilters = true },
        modifier = Modifier.testTag("debt_filter_button"),
    )
    if (showFilters) {
        OptionFilterSheet("Filtrar deudas", options, selectedType,
            onSelect = { onTypeSelected(it); showFilters = false },
            onDismiss = { showFilters = false })
    }
}

@Composable
private fun DebtEmptyCard(onRegister: () -> Unit) {
    val colors = rememberPlanningColors()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("debt_empty_state"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(colors.incomeBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(36.dp))
            }
            Text(
                "Todavía no tienes deudas ni préstamos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Registra lo que debes o lo que te deben para seguir los pagos, los vencimientos y tu saldo pendiente.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryMuted,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("debt_empty_register"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Registrar deuda o préstamo", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DebtTypeShortcuts(onPayable: () -> Unit, onReceivable: () -> Unit) {
    val colors = rememberPlanningColors()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DebtTypeShortcut(
            title = "Yo debo",
            description = "Préstamos, cuotas, familiares",
            icon = Icons.AutoMirrored.Filled.CallMade,
            tint = colors.expenseText,
            background = colors.expenseBg,
            onClick = onPayable,
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("debt_shortcut_payable"),
        )
        DebtTypeShortcut(
            title = "Me deben",
            description = "Dinero que prestaste",
            icon = Icons.AutoMirrored.Filled.CallReceived,
            tint = colors.incomeText,
            background = colors.incomeBg,
            onClick = onReceivable,
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("debt_shortcut_receivable"),
        )
    }
}

@Composable
private fun DebtTypeShortcut(
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberPlanningColors()
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Registrar", onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DebtTypeBadgeIcon(icon, tint, background, size = 40)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            }
        }
    }
}

@Composable
private fun DebtTypeBadgeIcon(icon: ImageVector, tint: Color, background: Color, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun DebtListItem(
    debt: DebtSummary,
    scheduledInstallment: DebtScheduleItem?,
    today: LocalDate,
    onClick: () -> Unit,
    onOptions: () -> Unit,
) {
    val colors = rememberPlanningColors()
    val isPayable = debt.obligationType == DebtObligationType.PAYABLE
    val currency = debt.currency()
    val paidFraction = if (debt.principalMinor > 0L) {
        ((debt.principalMinor - debt.remainingPrincipalMinor).toFloat() / debt.principalMinor).coerceIn(0f, 1f)
    } else 0f
    val paidPercent = (paidFraction * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("debt_card_${debt.debtId}")
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Ver detalle", onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, colors.borderSubtle),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DebtTypeBadgeIcon(
                    icon = if (isPayable) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                    tint = if (isPayable) colors.expenseText else colors.incomeText,
                    background = if (isPayable) colors.expenseBg else colors.incomeBg,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(debt.counterpartyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(
                        debtPaymentKind(debt, scheduledInstallment),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryMuted,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onOptions, modifier = Modifier.testTag("debt_options_${debt.debtId}")) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${debt.counterpartyName}", tint = colors.secondaryMuted)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Saldo pendiente", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        MoneyText(
                            minorUnits = debt.remainingPrincipalMinor,
                            currency = currency,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (debt.status == DebtLifecycleStatus.SETTLED) colors.incomeText else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(" / ", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, modifier = Modifier.padding(bottom = 3.dp))
                        MoneyText(
                            minorUnits = debt.principalMinor,
                            currency = currency,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryMuted,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (isPayable) "Pagado" else "Cobrado", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    Text("$paidPercent%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
            DebtProgressBar(
                fraction = paidFraction,
                color = if (isPayable) colors.payableProgress() else colors.incomeEmerald,
                track = colors.pillTrack,
                description = "${if (isPayable) "Pagado" else "Cobrado"} $paidPercent por ciento",
            )
            DebtStatusLine(debt, scheduledInstallment, currency, today)
        }
    }
}

@Composable
private fun DebtProgressBar(fraction: Float, color: Color, track: Color, description: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(track)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
private fun DebtStatusLine(debt: DebtSummary, installment: DebtScheduleItem?, currency: Currency, today: LocalDate) {
    val colors = rememberPlanningColors()
    val masked = LocalBalanceMasked.current
    when (debt.status) {
        DebtLifecycleStatus.SETTLED -> {
            DebtPill(if (debt.obligationType == DebtObligationType.PAYABLE) "Pagada" else "Cobrada", colors.incomeText, colors.incomeBg)
            return
        }
        DebtLifecycleStatus.CANCELLED -> {
            DebtPill(if (debt.remainingPrincipalMinor > 0L) "Cancelada · pendiente" else "Cancelada", colors.expenseText, colors.expenseBg)
            return
        }
        DebtLifecycleStatus.ACTIVE -> Unit
    }
    // A planned installment is never a payment: only its due date and amount are shown here.
    val pendingInstallment = installment?.takeIf { it.status == "PENDING" || it.status == "PARTIAL" }
    val dueDate = pendingInstallment?.dueDate ?: debt.dueDate
    if (dueDate == null && installment?.status != "PARTIAL" && installment?.status != "PAID") return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dueDate != null) {
            val days = ChronoUnit.DAYS.between(today, dueDate)
            val amountPrefix = if (pendingInstallment != null) {
                if (masked) "Cuota" else "Cuota ${currency.symbol()} ${formatMinorUnits(pendingInstallment.principalMinor)} ·"
            } else null
            val (text, tone) = when {
                days < 0 -> "Vencida hace ${-days} ${if (days == -1L) "día" else "días"}" to DueTone.Overdue
                days == 0L -> listOfNotNull(amountPrefix, "vence hoy").joinToString(" ").replaceFirstChar { it.uppercase() } to DueTone.Soon
                days <= DUE_SOON_DAYS -> listOfNotNull(amountPrefix, "en $days ${if (days == 1L) "día" else "días"}")
                    .joinToString(" ").replaceFirstChar { it.uppercase() } to DueTone.Soon
                else -> listOfNotNull(amountPrefix, "vence ${dueDate.format(if (dueDate.year == today.year) DUE_FORMAT_SHORT else DUE_FORMAT)}")
                    .joinToString(" ").replaceFirstChar { it.uppercase() } to DueTone.Neutral
            }
            when (tone) {
                DueTone.Overdue -> DebtPill(text, colors.expenseText, colors.expenseBg)
                DueTone.Soon -> DebtPill(text, colors.warningText, colors.warningBg)
                DueTone.Neutral -> DebtPill(text, colors.primaryText, colors.pillTrack)
            }
        }
    }
}

@Composable
private fun DebtPill(text: String, textColor: Color, background: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = background) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private enum class DueTone { Overdue, Soon, Neutral }

private const val DUE_SOON_DAYS = 7L
private val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))
private val DUE_FORMAT_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE"))

/** How the debt is repaid, following the creation form options. */
private fun debtPaymentKind(debt: DebtSummary, installment: DebtScheduleItem?): String = when {
    installment != null -> "En cuotas"
    debt.dueDate != null -> "Pago único"
    else -> "Sin fecha fija"
}

private fun DebtSummary.currency(): Currency =
    runCatching { Currency.valueOf(currencyCode) }.getOrDefault(Currency.PEN)

private fun List<DebtSummary>.totalsFor(type: DebtObligationType): Map<Currency, Long> =
    filter { it.obligationType == type }
        .groupBy { it.currency() }
        .mapValues { (_, items) -> items.sumOf { it.remainingPrincipalMinor } }

/** Repayment progress follows the same primary color as the other Planning tabs. */
private fun CalmEmeraldColors.payableProgress(): Color = incomeEmerald
