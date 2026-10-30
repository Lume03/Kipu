package com.kipu.app.feature.statements.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.movements.presentation.AccountSelectorBottomSheet
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.movements.presentation.PastelCategoryPickerBottomSheet
import com.kipu.app.feature.receipts.presentation.tealTint
import com.kipu.app.feature.statements.domain.*
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatementFilter(val label: String) { ALL("Todos"), SELECTED("Marcados"), REVIEW("Para revisar") }
internal val statementDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-PE"))
private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private fun LocalDate.short() = "$dayOfMonth ${MONTHS[monthValue - 1]}"
private fun LocalDate.long() = "${short()} $year"

private fun StatementImportLine.needsReview(state: StatementReviewUiState) = possibleDuplicate || kind == StatementLineKind.UNKNOWN ||
    (needsPurchaseCategory() && categoryId == null) || id in state.failures || id in state.missingCategoryIds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementReviewScreen(
    state: StatementReviewUiState,
    onBack: () -> Unit,
    onCard: (String) -> Unit,
    onSelect: (StatementImportLine, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onEdit: (String, StatementImportLineEdit, () -> Unit) -> Unit,
    onBatchCategory: (String) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onDismissMessage: () -> Unit,
    onPlans: () -> Unit,
    onViewMovements: () -> Unit = onBack,
) {
    val palette = rememberCalmEmeraldColors()
    var filter by remember { mutableStateOf(StatementFilter.ALL) }
    var cardPicker by remember { mutableStateOf(false) }
    var categoryPicker by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var confirmSheet by remember { mutableStateOf(false) }
    var registerRequested by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val savedLine = remember { mutableStateOf(0) }
    val statement = state.snapshot?.statement
    val lines = state.snapshot?.lines.orEmpty()
    val editable = statement?.status == StatementImportStatus.PENDING && !state.busy
    BackHandler(enabled = state.busy) { /* Do not interrupt a ledger batch by leaving the screen. */ }
    LaunchedEffect(state.busy) { if (!state.busy) registerRequested = false }
    LaunchedEffect(savedLine.value) { if (savedLine.value > 0) snackbar.showSnackbar("Cambios guardados", duration = SnackbarDuration.Short) }
    Scaffold(containerColor = palette.background, snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 4.dp)) },
        topBar = {
            TopAppBar(title = { Text("Revisar estado de cuenta", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (statement?.status == StatementImportStatus.PENDING && state.successCount == null) {
                        IconButton(onClick = { menu = true }, enabled = !state.busy) { Icon(Icons.Filled.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Descartar importación") }, onClick = { menu = false; discardDialog = true },
                                leadingIcon = { Icon(Icons.Filled.DeleteOutline, null) })
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background, titleContentColor = palette.primaryText,
                    navigationIconContentColor = palette.primaryText, actionIconContentColor = palette.primaryText))
        },
        bottomBar = {
            if (statement?.status == StatementImportStatus.PENDING && state.successCount == null) ReviewBottomBar(state) { confirmSheet = true }
        },
    ) { padding ->
        when {
            state.successCount != null -> StatementSuccess(state, lines, onViewMovements, onBack)
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("statement_review_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.message?.let { message -> item {
                    StatementNotice(message, onDismiss = onDismissMessage, failed = state.failures.isNotEmpty())
                    if (state.showPremium) TextButton(onClick = onPlans) { Text("Ver planes") }
                } }
                if (statement != null) {
                    item { ReviewHero(state, statement, lines, editable, onPickCard = { cardPicker = true }) }
                    val mismatch = listOf(statement.reconciledPen, statement.reconciledUsd).any { it == false }
                    if (mismatch) item {
                        BannerRow(Icons.Filled.WarningAmber, "warn", "Los totales no coinciden",
                            "Revisa los movimientos marcados antes de registrar: algo no cuadra con el PDF.")
                    }
                    if (statement.status == StatementImportStatus.COMPLETED) item { StatementNotice("Ya importaste este estado de cuenta") }
                    if (statement.status == StatementImportStatus.DISCARDED) item { StatementNotice("Esta importación fue descartada") }
                    if (statement.status == StatementImportStatus.PENDING) {
                        item { FilterRow(filter, lines, state) { filter = it } }
                        item {
                            ToolsRow(state, editable, onSelectAll, onCategorize = { categoryPicker = true })
                        }
                    }
                    val visible = lines.filter { line -> when (filter) {
                        StatementFilter.ALL -> true
                        StatementFilter.SELECTED -> line.selected && line.status == StatementImportLineStatus.PENDING
                        StatementFilter.REVIEW -> line.needsReview(state)
                    } }
                    if (lines.isEmpty()) item { EmptyCycle(onBack) }
                    else if (visible.isEmpty()) item { Text("No hay movimientos en este filtro.", color = palette.secondaryMuted, modifier = Modifier.padding(8.dp)) }
                    visible.groupBy { it.occurredDate }.forEach { (day, rows) ->
                        item(key = "day-$day") { DayGroup(day, rows, state, editable, onSelect, onEdit = { editId = it }) }
                    }
                }
            }
        }
    }
    if (cardPicker && state.snapshot != null) AccountSelectorBottomSheet(
        onDismissRequest = { cardPicker = false }, accounts = emptyList(), creditCards = state.cards,
        selectedCardId = statement?.cardId, title = "Elegir tarjeta de crédito", subtitle = "Asigna los movimientos a tu tarjeta Kipu",
        showNewAccountAction = false, onCardSelected = { cardPicker = false; onCard(it.id.value) })
    if (categoryPicker && state.snapshot != null) StatementCategoryPicker(state.categories, null,
        onSelect = { categoryPicker = false; onBatchCategory(it.id) }, onDismiss = { categoryPicker = false })
    lines.find { it.id == editId && it.status == StatementImportLineStatus.PENDING }?.let { line ->
        if (statement?.status == StatementImportStatus.PENDING && state.successCount == null) StatementLineEditSheet(line, state, source = statement,
            onDismiss = { editId = null }, onSave = { edit -> onEdit(line.id, edit) { editId = null; savedLine.value = savedLine.value + 1 } })
    }
    if (confirmSheet) StatementConfirmSheet(state, onDismiss = { confirmSheet = false }, onCategorize = { confirmSheet = false; categoryPicker = true },
        onConfirm = { confirmSheet = false; registerRequested = true; onConfirm() })
    if (registerRequested && state.busy) StatementRegisteringOverlay()
    if (discardDialog) StatementDiscardDialog(busy = state.busy, onDismiss = { discardDialog = false }, onDiscard = { discardDialog = false; onDiscard() })
}

// ---------------------------------------------------------------------------------------------
// Header: the statement at a glance
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReviewHero(state: StatementReviewUiState, statement: StatementImport, lines: List<StatementImportLine>, editable: Boolean, onPickCard: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Surface(shape = RoundedCornerShape(24.dp), color = palette.surfaceCard, border = BorderStroke(1.dp, palette.borderSubtle), modifier = Modifier.fillMaxWidth().testTag("statement_hero")) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatementBankTile(statement.issuer)
                Column(Modifier.weight(1f)) {
                    Text("${statement.issuer} •••• ${statement.cardLastFour}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.CalendarToday, null, Modifier.size(14.dp), tint = palette.secondaryMuted)
                        Text("${statement.cycleStart.short()} – ${statement.cycleEnd.long()}", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                    }
                }
                statement.dueDate?.let { StatementToneChip("Vence ${it.short()}", "warn", Icons.Filled.AccessTime) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Currency.PEN to statement.reconciledPen, Currency.USD to statement.reconciledUsd).forEach { (currency, matches) ->
                    val rows = lines.filter { it.currency == currency && it.kind != StatementLineKind.INSTALLMENT_MONTHLY_CHARGE }
                    if (rows.isNotEmpty()) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(currency.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = palette.secondaryMuted, letterSpacing = .6.sp)
                        Text(statementCurrencyTotal(rows, currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.primaryText, maxLines = 1)
                        if (matches == true) StatementToneChip("Verificado", "ok", Icons.Filled.Check) else StatementToneChip("Totales no cuadran", "warn", Icons.Filled.WarningAmber)
                    }
                }
            }
            HorizontalDivider(color = palette.borderSubtle)
            Text("TARJETA DE KIPU", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = palette.secondaryMuted, letterSpacing = .6.sp)
            val card = state.card
            if (card == null) Surface(onClick = onPickCard, enabled = editable, shape = RoundedCornerShape(16.dp), color = statementTone("warn").bg,
                border = BorderStroke(1.5.dp, statementTone("warn").border), modifier = Modifier.fillMaxWidth().testTag("statement_card_required")) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(statementTone("warn").border.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CreditCard, null, Modifier.size(22.dp), tint = statementTone("warn").fg)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Elige la tarjeta", fontWeight = FontWeight.Bold, color = palette.primaryText)
                        Text("Necesaria para registrar", style = MaterialTheme.typography.bodySmall, color = statementTone("warn").fg)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = statementTone("warn").fg)
                }
            } else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.tealTint(.08f)).border(1.5.dp, palette.tealTint(.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF64BAB0)))), contentAlignment = Alignment.BottomStart) {
                    Text("•••• ${card.lastFourDigits}", Modifier.padding(start = 6.dp, bottom = 4.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("${card.alias ?: card.issuer} •••• ${card.lastFourDigits}", fontWeight = FontWeight.Bold, color = palette.primaryText, maxLines = 1)
                    if (card.lastFourDigits == statement.cardLastFour) StatementToneChip("Coincide con el PDF", "ok", Icons.Filled.Check)
                    else Text(card.currency.name, style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                }
                TextButton(onClick = onPickCard, enabled = editable, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cambiar", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun BannerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: String, title: String, text: String) {
    val t = statementTone(tone)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.bg).border(1.5.dp, t.border, RoundedCornerShape(18.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = t.fg)
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, color = t.fg)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = t.fg)
        }
    }
}

@Composable
private fun FilterRow(filter: StatementFilter, lines: List<StatementImportLine>, state: StatementReviewUiState, onChange: (StatementFilter) -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatementFilter.entries.forEach { choice ->
            val count = when (choice) {
                StatementFilter.ALL -> lines.size
                StatementFilter.SELECTED -> state.selected.size
                StatementFilter.REVIEW -> lines.count { it.needsReview(state) }
            }
            val on = filter == choice
            val warn = choice == StatementFilter.REVIEW && count > 0
            Surface(onClick = { onChange(choice) }, shape = RoundedCornerShape(21.dp), color = if (on) palette.primaryDeep else palette.surfaceCard,
                contentColor = if (on) palette.onPrimaryDeep else palette.primaryText, border = BorderStroke(1.5.dp, if (on) palette.primaryDeep else palette.borderSubtle),
                modifier = Modifier.heightIn(min = 42.dp).testTag("statement_filter_${choice.name.lowercase()}")) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(choice.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(
                        if (on) palette.onPrimaryDeep.copy(alpha = .22f) else if (warn) statementTone("warn").bg else palette.pillTrack).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                        Text(count.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
                            color = if (on) palette.onPrimaryDeep else if (warn) statementTone("warn").fg else palette.secondaryMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsRow(state: StatementReviewUiState, editable: Boolean, onSelectAll: (Boolean) -> Unit, onCategorize: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val pendingLines = state.snapshot?.lines.orEmpty().filter { it.status == StatementImportLineStatus.PENDING && state.selectable(it) }
    val selectedCount = pendingLines.count { it.selected }
    val toggle = when { pendingLines.isEmpty() || selectedCount == 0 -> ToggleableState.Off; selectedCount == pendingLines.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
    val missing = state.selected.count { it.categoryId == null && it.needsPurchaseCategory() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.weight(1f, fill = false).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = editable) { onSelectAll(toggle != ToggleableState.On) }.padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TriStateCheckbox(state = toggle, onClick = { onSelectAll(toggle != ToggleableState.On) }, enabled = editable,
                modifier = Modifier.semantics { contentDescription = "Seleccionar todo" })
            Text("Seleccionar todo", fontWeight = FontWeight.SemiBold, color = palette.primaryText, maxLines = 1)
        }
        Surface(onClick = onCategorize, enabled = editable && missing > 0, shape = RoundedCornerShape(20.dp), color = palette.tealTint(.14f), contentColor = palette.primaryDeep,
            modifier = Modifier.heightIn(min = 44.dp).testTag("statement_categorize")) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(17.dp))
                Text(if (missing > 0) "Categorizar $missing" else "Todo categorizado", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun DayGroup(day: LocalDate, rows: List<StatementImportLine>, state: StatementReviewUiState, editable: Boolean,
    onSelect: (StatementImportLine, Boolean) -> Unit, onEdit: (String) -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(day.long().uppercase(Locale.ROOT), Modifier.padding(start = 6.dp, top = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
            color = palette.secondaryMuted, letterSpacing = .6.sp)
        Column(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(palette.surfaceCard).border(1.dp, palette.borderSubtle, RoundedCornerShape(22.dp))) {
            rows.forEachIndexed { index, line ->
                StatementLineRow(line, state, editable, onSelect = { onSelect(line, it) }, onEdit = { onEdit(line.id) })
                if (index < rows.lastIndex) HorizontalDivider(color = palette.borderSubtle)
            }
        }
    }
}

@Composable
private fun StatementLineRow(line: StatementImportLine, state: StatementReviewUiState, enabled: Boolean, onSelect: (Boolean) -> Unit, onEdit: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val error = state.failures[line.id]?.message() ?: "Elige una categoría para continuar".takeIf { line.id in state.missingCategoryIds }
    val credit = line.isCredit()
    val category = state.categories.find { it.id == line.categoryId }
    val editableLine = enabled && line.status == StatementImportLineStatus.PENDING && line.kind != StatementLineKind.POINTS_REDEMPTION
    Row(Modifier.fillMaxWidth().background(if (error != null) statementTone("bad").bg.copy(alpha = .35f) else Color.Transparent)
        .clickable(enabled = editableLine, onClick = onEdit).testTag("statement_line_${line.id}").padding(horizontal = 12.dp, vertical = 12.dp).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Checkbox(checked = line.selected && line.status == StatementImportLineStatus.PENDING, onCheckedChange = onSelect, enabled = enabled && state.selectable(line),
            colors = CheckboxDefaults.colors(checkedColor = palette.primaryDeep, checkmarkColor = palette.onPrimaryDeep),
            modifier = Modifier.semantics { contentDescription = "Seleccionar ${line.description}" })
        StatementCategoryTile(category, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(line.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.primaryText, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatementKindChip(line.kind, line.installmentCount)
                if (line.kind != StatementLineKind.POINTS_REDEMPTION) {
                    Text(category?.displayName ?: "Sin categoría", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        color = if (category == null) statementTone("warn").fg else palette.secondaryMuted, fontWeight = if (category == null) FontWeight.Bold else FontWeight.Normal)
                }
            }
            if (line.possibleDuplicate) StatementToneChip("Posible duplicado", "bad", Icons.Filled.WarningAmber)
            if (line.kind == StatementLineKind.POINTS_REDEMPTION) Text("Informativo: el canje de puntos no se registra.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
            if (state.card != null && state.card?.currency != line.currency) Text("Moneda distinta a la tarjeta; no se puede seleccionar.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
            if (line.kind == StatementLineKind.CARD_PAYMENT) Text("Cuenta origen: ${state.accounts.find { it.id.value == line.sourceAccountId }?.alias ?: "Elige una cuenta"}",
                style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
            if (line.status == StatementImportLineStatus.REGISTERED) StatementToneChip("Registrado", "ok", Icons.Filled.Check)
            if (error != null) Text(error, color = statementTone("bad").fg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${if (credit) "+" else "−"}${statementSymbol(line.currency)}${statementPlainAmount(line.amountMinor)}", fontWeight = FontWeight.ExtraBold,
                color = if (credit) palette.incomeText else palette.primaryText, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyCycle(onBack: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(palette.pillTrack), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ReceiptLong, null, Modifier.size(34.dp), tint = palette.secondaryMuted)
        }
        Text("No hay movimientos en este ciclo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = palette.primaryText)
        Text("El estado se leyó bien, pero no trae compras en este ciclo.", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted, textAlign = TextAlign.Center)
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver", fontWeight = FontWeight.Bold) }
    }
}

// ---------------------------------------------------------------------------------------------
// Bottom bar, confirmation, registering and success
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReviewBottomBar(state: StatementReviewUiState, onRegister: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Surface(color = palette.surfaceCard, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.selected.size} seleccionados", fontWeight = FontWeight.ExtraBold, color = palette.primaryText, modifier = Modifier.testTag("statement_selected_count"))
                Text(statementTotals(state.selected), style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted, modifier = Modifier.testTag("statement_selected_summary"))
            }
            Button(onClick = onRegister, enabled = state.canConfirm, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("statement_confirm")) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = palette.onPrimaryDeep, strokeWidth = 2.dp)
                else { Text("Registrar ${state.selected.size} movimientos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
            when {
                state.card == null -> Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.WarningAmber, null, Modifier.size(14.dp), tint = statementTone("warn").fg)
                    Text("Elige la tarjeta para continuar", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = statementTone("warn").fg)
                }
                state.selected.isEmpty() -> Text("Marca al menos un movimiento para registrar.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementConfirmSheet(state: StatementReviewUiState, onDismiss: () -> Unit, onCategorize: () -> Unit, onConfirm: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val selected = state.selected
    val missing = selected.count { it.categoryId == null && it.needsPurchaseCategory() }
    fun group(title: String, kinds: Set<StatementLineKind>): Triple<String, Int, String>? {
        val rows = selected.filter { it.kind in kinds }
        if (rows.isEmpty()) return null
        return Triple(title, rows.size, rows.groupBy { it.currency }.entries.joinToString(" · ") { (c, r) -> statementCurrencyTotal(r, c) })
    }
    val groups = listOfNotNull(
        group("Compras", setOf(StatementLineKind.PURCHASE, StatementLineKind.INSTALLMENT_PURCHASE)),
        group("Devoluciones", setOf(StatementLineKind.REFUND)), group("Pagos de tarjeta", setOf(StatementLineKind.CARD_PAYMENT)),
        group("Intereses", setOf(StatementLineKind.INTEREST)), group("Seguros y comisiones", setOf(StatementLineKind.INSURANCE, StatementLineKind.FEE)),
        group("Por revisar", setOf(StatementLineKind.UNKNOWN)),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = palette.surfaceCard,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), modifier = Modifier.testTag("statement_confirm_sheet")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Registrar ${selected.size} movimientos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                state.card?.let { card ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.tealTint(.08f)).border(1.5.dp, palette.tealTint(.3f), RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.CreditCard, null, tint = palette.primaryDeep)
                        Column {
                            Text("SE REGISTRAN EN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryDeep, letterSpacing = .6.sp)
                            Text("${card.alias ?: card.issuer} •••• ${card.lastFourDigits}", fontWeight = FontWeight.Bold, color = palette.primaryText)
                        }
                    }
                }
                Column {
                    groups.forEach { (title, count, total) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), color = palette.primaryText)
                            Text("$count", color = palette.secondaryMuted, modifier = Modifier.padding(end = 12.dp))
                            Text(total, fontWeight = FontWeight.Bold, color = palette.primaryText, maxLines = 1)
                        }
                        HorizontalDivider(color = palette.borderSubtle)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(palette.pillTrack).padding(12.dp)) {
                        Text("${selected.size - missing}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                        Text("listos", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                    }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (missing > 0) statementTone("warn").bg else palette.pillTrack)
                        .clickable(enabled = missing > 0, onClick = onCategorize).padding(12.dp)) {
                        Text("$missing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = if (missing > 0) statementTone("warn").fg else palette.primaryText)
                        Text(if (missing > 0) "sin categoría · Categorizar" else "sin categoría", style = MaterialTheme.typography.bodySmall,
                            color = if (missing > 0) statementTone("warn").fg else palette.secondaryMuted, fontWeight = if (missing > 0) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Text("Se suman a los movimientos de la tarjeta y actualizan tu deuda. Podrás editarlos después.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onConfirm, enabled = missing == 0, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("statement_confirm_sheet_register")) {
                Text(if (missing > 0) "Categoriza $missing para registrar" else "Registrar ${selected.size} movimientos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Seguir revisando", fontWeight = FontWeight.Bold, color = palette.secondaryMuted) }
        }
    }
}

@Composable
private fun StatementRegisteringOverlay() {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF073F3A)))).testTag("statement_registering"), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(32.dp)) {
                CircularProgressIndicator(Modifier.size(72.dp), color = Color(0xFF5EEAD4), trackColor = Color.White.copy(alpha = .2f), strokeWidth = 7.dp)
                Text("Registrando movimientos…", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text("No cierres Kipu. Esto toma unos segundos.", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StatementSuccess(state: StatementReviewUiState, lines: List<StatementImportLine>, onViewMovements: () -> Unit, onDone: () -> Unit) {
    val registered = lines.filter { it.status == StatementImportLineStatus.REGISTERED }
    val categorized = registered.count { it.categoryId != null }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF073F3A))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(112.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)).border(3.dp, Color.White.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, Modifier.size(58.dp), tint = Color.White)
            }
            Spacer(Modifier.height(26.dp))
            Text("${state.successCount} movimientos registrados", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, modifier = Modifier.testTag("statement_success"))
            Spacer(Modifier.height(10.dp))
            Text("Ya están en tu tarjeta y tu deuda refleja el estado de cuenta.", color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (registered.isNotEmpty()) Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SuccessStat("$categorized", "con categoría", Modifier.weight(1f))
                SuccessStat("${registered.size - categorized}", "sin categoría", Modifier.weight(1f))
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onViewMovements, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0B5F58)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Ver movimientos", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Listo", color = Color.White.copy(alpha = .9f), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SuccessStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .14f)).border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatementDiscardDialog(busy: Boolean, onDismiss: () -> Unit, onDiscard: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val bad = statementTone("bad")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = palette.surfaceCard, shadowElevation = 20.dp, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("statement_discard_dialog")) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(bad.bg), contentAlignment = Alignment.Center) { Icon(Icons.Filled.DeleteOutline, null, Modifier.size(30.dp), tint = bad.fg) }
                Text("¿Descartar esta importación?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = palette.primaryText)
                Text("Se descartarán los movimientos pendientes y lo que ya elegiste. Los movimientos que ya registraste se conservan; tu saldo no cambia.",
                    style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Button(onClick = onDiscard, enabled = !busy, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = bad.fg, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("statement_confirm_discard")) {
                    Icon(Icons.Filled.DeleteOutline, null, Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)); Text("Sí, descartar", fontWeight = FontWeight.Bold)
                }
                Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = palette.pillTrack, contentColor = palette.primaryText),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Seguir revisando", fontWeight = FontWeight.Bold) }
                Text("Si solo quieres pausar, sal con la flecha: te guardamos un borrador.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun StatementNotice(text: String, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null, failed: Boolean = false) {
    val t = statementTone(if (failed) "bad" else "teal")
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.bg).border(1.dp, t.border, RoundedCornerShape(16.dp)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = t.fg)
        if (onDismiss != null) TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Entendido", fontWeight = FontWeight.Bold, color = t.fg) }
    }
}

@Composable
internal fun StatementCategoryPicker(categories: List<CategoryOption>, selected: String?, onSelect: (CategoryOption) -> Unit, onDismiss: () -> Unit) {
    PastelCategoryPickerBottomSheet(categories = categories, selectedCategoryId = selected, onSelect = onSelect,
        onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
        onDismiss = onDismiss, allowCreate = false, directSelectionMode = true)
}

internal fun statementAmount(minor: Long, currency: Currency): String =
    "${if (currency == Currency.PEN) "S/" else "US$"} ${BigDecimal.valueOf(minor, 2).toPlainString()}"

internal fun statementTotals(lines: List<StatementImportLine>): String = lines.groupBy { it.currency }.entries.joinToString(" · ") { (currency, rows) ->
    val amount = rows.fold(BigDecimal.ZERO) { total, line ->
        total + BigDecimal.valueOf(line.amountMinor).let { if (line.kind == StatementLineKind.REFUND || line.kind == StatementLineKind.CARD_PAYMENT) -it else it }
    }.movePointLeft(2).toPlainString()
    "${currency.name} $amount"
}.ifEmpty { "Sin importe" }
