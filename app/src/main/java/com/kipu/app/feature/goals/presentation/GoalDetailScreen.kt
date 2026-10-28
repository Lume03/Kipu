package com.kipu.app.feature.goals.presentation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.PlanningProgressRing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.formatMoneyTextDisplay
import com.kipu.app.ui.component.symbol
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import com.kipu.app.ui.theme.rememberPlanningColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.kipu.app.ui.component.PlanningNotice
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.ui.component.MoneyText
import java.math.BigDecimal
import java.math.RoundingMode

private enum class GoalDetailDialog { RELEASE, REASSIGN, ARCHIVE, COMPLETE, DELETE }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GoalDetailScreen(
    state: GoalDetailUiState,
    onNavigateBack: () -> Unit,
    onContribute: () -> Unit,
    onRelease: (backingAccountId: String, amount: Money, replacementGoalId: String?) -> Unit,
    onReassign: (destinationGoalId: String, backingAccountId: String, amount: Money) -> Unit,
    onArchive: () -> Unit,
    onEdit: (() -> Unit)? = null,
    startWithWithdrawal: Boolean = false,
    onCreateGoal: (() -> Unit)? = null,
    initialAction: String? = null,
    onReserve: ((Money, String, Boolean, String?) -> Unit)? = null,
    fundsState: GoalContributionUiState? = null,
    onComplete: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onPause: ((Boolean) -> Unit)? = null,
    onDeleteWithReplacement: ((String?) -> Unit)? = null,
) {
    val colors = rememberPlanningColors()
    var optionsExpanded by remember { mutableStateOf(false) }
    val goal = state.goal
    val progress = state.progress
    var openDialog by remember { mutableStateOf<GoalDetailDialog?>(when {
        startWithWithdrawal -> GoalDetailDialog.RELEASE
        initialAction == "archive" || initialAction == "pause" -> GoalDetailDialog.ARCHIVE
        initialAction == "complete" -> GoalDetailDialog.COMPLETE
        initialAction == "delete" -> GoalDetailDialog.DELETE
        else -> null
    }) }
    var showHistory by rememberSaveable { mutableStateOf(initialAction == "history") }
    var completionRequested by rememberSaveable(goal?.id) { mutableStateOf(false) }
    var showCelebration by rememberSaveable(goal?.id) { mutableStateOf(initialAction == "celebrate") }
    androidx.compose.runtime.LaunchedEffect(goal?.status) {
        if (completionRequested && goal?.status == GoalStatus.COMPLETED) { showCelebration = true; completionRequested = false }
    }
    var amountText by rememberSaveable(goal?.id) { mutableStateOf("") }
    var destinationGoalId by rememberSaveable(goal?.id) { mutableStateOf("") }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var destinationMenuExpanded by remember { mutableStateOf(false) }
    val goalReservedAccounts = remember(state.events) {
        state.events.groupBy { it.backingAccountId }.mapNotNull { (id, events) ->
            if (id == null) null else id to events.filterNot(GoalEvent::transactionVoided)
                .sumOf { it.delta.minorUnits }
        }.toMap()
    }
    val releasableAccounts = state.backingAccounts.filter { (goalReservedAccounts[it.id] ?: 0L) > 0L }
    val candidateGoals = state.otherGoals.filter {
        it.currency == goal?.currency && it.status == GoalStatus.ACTIVE
    }

    if (showCelebration && goal != null && goal.status == GoalStatus.COMPLETED) {
        GoalAchievedScreen(goal, state.events,
            onRelease = { showCelebration = false; openDialog = GoalDetailDialog.RELEASE },
            onRetain = onNavigateBack, onCreate = onCreateGoal)
        return
    }

    if (openDialog == GoalDetailDialog.RELEASE) {
        GoalContributionSheet(
            state = fundsState?.copy(
                isSaving = fundsState.isSaving || state.isSaving,
                errorMessage = state.errorMessage ?: fundsState.errorMessage,
                message = state.message ?: fundsState.message,
            ) ?: GoalContributionUiState(state.userId, goal, state.backingAccounts, state.isLoading, state.isSaving, state.errorMessage, state.message),
            onNavigateBack = { openDialog = null },
            onReserve = onReserve ?: { _, _, _, _ -> onContribute() },
            withdrawalState = state, onRelease = onRelease, initialWithdrawal = true,
        )
        return
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (goal != null && progress != null && goal.status != GoalStatus.ARCHIVED &&
                (goal.status == GoalStatus.ACTIVE || goal.status == GoalStatus.COMPLETED || releasableAccounts.isNotEmpty())) Surface(color = colors.surfaceCard) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (goal.status == GoalStatus.ACTIVE && !state.isPaused) Button(
                            onClick = onContribute, enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                        ) { Icon(Icons.Default.Add, null); Text("Aportar", Modifier.padding(start = 6.dp)) }
                        if (releasableAccounts.isNotEmpty()) Button(
                            onClick = { openDialog = GoalDetailDialog.RELEASE },
                            enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = if (goal.status == GoalStatus.COMPLETED) colors.primaryDeep else colors.surfaceCard,
                                contentColor = if (goal.status == GoalStatus.COMPLETED) colors.onPrimaryDeep else colors.primaryDeep),
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("goal_release"), shape = RoundedCornerShape(16.dp),
                        ) { Text(if (goal.status == GoalStatus.COMPLETED) "Liberar dinero" else "Retirar") }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(goal?.name ?: "Detalle de meta") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    if (goal != null) {
                        IconButton(onClick = { optionsExpanded = true }) { Icon(Icons.Default.MoreVert, "Opciones de la meta") }

                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            goal == null || progress == null -> Text("No encontramos esta meta.", Modifier.padding(padding).padding(24.dp))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                state.errorMessage?.let { PlanningNotice("No se pudo completar la acción", it, isError = true) }
                state.message?.let { PlanningNotice("Cambios guardados", it) }
                if (state.isPaused) PlanningNotice("Meta pausada", "Tu dinero sigue reservado. Reanuda la meta desde el menú para volver a aportar.")
                KipuCard(shape = RoundedCornerShape(24.dp), containerColor = colors.surfaceCard, borderColor = null, elevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(
                            listOfNotNull(
                                goal.targetDate?.let { "Meta para el ${it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es-PE")))}" } ?: "Sin fecha objetivo",
                                state.backingAccounts.firstOrNull { it.id == goal.linkedAccountId }?.name,
                            ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, textAlign = TextAlign.Center,
                        )
                        PlanningProgressRing(progress.progressPercent.toInt(), "reservado", colors.incomeEmerald)
                        Row(Modifier.fillMaxWidth().testTag("goal_summary_amounts"), verticalAlignment = Alignment.CenterVertically) {
                            GoalSummaryAmount("Reservado", progress.reserved, Modifier.weight(1f))
                            VerticalDivider(Modifier.height(40.dp), color = colors.borderSubtle)
                            GoalSummaryAmount("Faltan", Money((goal.target.minorUnits - progress.reserved.minorUnits).coerceAtLeast(0L), goal.currency), Modifier.weight(1f))
                            VerticalDivider(Modifier.height(40.dp), color = colors.borderSubtle)
                            GoalSummaryAmount("Objetivo", goal.target, Modifier.weight(1f))
                        }
                    }
                }
                if (goal.status == GoalStatus.ARCHIVED) {
                    Text("Meta pausada · historial conservado.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("goal_archived_explanation"))
                }
                GoalMonthlyContributions(state.events, goal, progress.reserved.minorUnits)
                GoalEventHistory(state.events)
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (optionsExpanded && goal != null && progress != null) {
        com.kipu.app.ui.component.PlanningActionsSheet(goal.name, { optionsExpanded = false }, buildList {
            if (goal.status == GoalStatus.ACTIVE && !state.isPaused) add(com.kipu.app.ui.component.PlanningAction(
                "Aportar", "Aparta dinero de una cuenta", Icons.Default.Add, onContribute,
                com.kipu.app.ui.component.PlanningActionTone.PRIMARY))
            if (releasableAccounts.isNotEmpty()) add(com.kipu.app.ui.component.PlanningAction(
                "Retirar", "Devuelve dinero a tu disponible", Icons.Default.Remove, { openDialog = GoalDetailDialog.RELEASE }))
            if (releasableAccounts.isNotEmpty() && candidateGoals.isNotEmpty()) add(com.kipu.app.ui.component.PlanningAction(
                "Reasignar", "Reserva este dinero para otra meta", Icons.Default.Edit,
                { openDialog = GoalDetailDialog.REASSIGN }, enabled = !state.isSaving, tag = "goal_reassign"))
            onEdit?.let { edit -> add(com.kipu.app.ui.component.PlanningAction(
                "Editar meta", "Nombre, monto, fecha e icono", Icons.Default.Edit, edit)) }
            add(com.kipu.app.ui.component.PlanningAction("Ver historial", "Aportes, retiros y ajustes", Icons.Default.History, { showHistory = true }))
            if (goal.status == GoalStatus.COMPLETED) add(com.kipu.app.ui.component.PlanningAction(
                "Ver celebración", "Resumen de tu meta lograda", Icons.Default.EmojiEvents, { showCelebration = true }, com.kipu.app.ui.component.PlanningActionTone.PRIMARY))
            if (goal.status == GoalStatus.ACTIVE && onComplete != null) add(com.kipu.app.ui.component.PlanningAction(
                "Marcar como lograda", "Ajusta el objetivo al dinero reservado", Icons.Default.EmojiEvents,
                { openDialog = GoalDetailDialog.COMPLETE }, com.kipu.app.ui.component.PlanningActionTone.WARNING, tag = "goal_complete"))
            if (goal.status == GoalStatus.ACTIVE || goal.status == GoalStatus.LOCKED_BY_PLAN) add(com.kipu.app.ui.component.PlanningAction(
                if (state.isPaused) "Reanudar meta" else "Pausar meta", "Conserva el dinero reservado y su historial", Icons.Default.Archive,
                { openDialog = GoalDetailDialog.ARCHIVE }, com.kipu.app.ui.component.PlanningActionTone.WARNING,
                enabled = !state.isSaving, tag = "goal_archive"))
            if (onDelete != null) add(com.kipu.app.ui.component.PlanningAction(
                "Eliminar meta", "Revisa las condiciones antes de eliminar", Icons.Default.DeleteOutline,
                { openDialog = GoalDetailDialog.DELETE }, com.kipu.app.ui.component.PlanningActionTone.DESTRUCTIVE))
        })
    }

    when (openDialog) {
        GoalDetailDialog.COMPLETE -> if (goal != null && progress != null) GoalCompleteDialog(goal, progress.reserved, state.isSaving,
            onDismiss = { openDialog = null }, onConfirm = { openDialog = null; completionRequested = true; onComplete?.invoke() })
        GoalDetailDialog.DELETE -> if (goal != null) GoalDeleteDialog(goal, (progress?.reserved?.minorUnits ?: 0L) > 0L, state.isSaving,
            onDismiss = { openDialog = null }, onDelete = { openDialog = null; onDelete?.invoke() },
            onArchive = { openDialog = GoalDetailDialog.ARCHIVE }, reserved = progress?.reserved,
            replacementCandidates = if (goal.status == GoalStatus.COMPLETED && !state.hasPremiumAccess && candidateGoals.size >= 2 && (progress?.reserved?.minorUnits ?: 0L) > 0L) candidateGoals.map { it.id to it.name } else emptyList(),
            onDeleteWithReplacement = onDeleteWithReplacement, errorMessage = state.errorMessage)
        GoalDetailDialog.RELEASE -> Unit // The shared funds screen above owns withdrawal.
        GoalDetailDialog.REASSIGN -> {
            var selectedAccount by rememberSaveable(goal?.id) { mutableStateOf(releasableAccounts.firstOrNull()?.id.orEmpty()) }
            val account = releasableAccounts.firstOrNull { it.id == selectedAccount }
            val reassignAmount = account?.let { parseAmount(amountText, it.currency) }
            val maxReassign = account?.let { goalReservedAccounts[it.id] } ?: 0L
            val canReassign = reassignAmount != null && reassignAmount.minorUnits <= maxReassign &&
                destinationGoalId in candidateGoals.map { it.id }
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text("Reasignar reserva") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("La reasignación conserva el mismo respaldo y no transfiere dinero.")
                        AccountPicker(releasableAccounts, selectedAccount, { accountMenuExpanded = it }, accountMenuExpanded, "goal_detail_account_picker") { selectedAccount = it }
                        GoalPicker(candidateGoals.map { it.id to it.name }, destinationGoalId, { destinationMenuExpanded = it }, destinationMenuExpanded) { destinationGoalId = it }
                        AmountInput(amountText, { amountText = it })
                    }
                },
                confirmButton = {
                    TextButton(enabled = canReassign, onClick = {
                        if (account != null && reassignAmount != null) {
                            onReassign(destinationGoalId, account.id, reassignAmount)
                            openDialog = null
                            amountText = ""
                        }
                    }, modifier = Modifier.testTag("goal_reassign_confirm")) { Text("Reasignar") }
                },
                dismissButton = { TextButton(onClick = { openDialog = null }) { Text("Cancelar") } },
            )
        }
        GoalDetailDialog.ARCHIVE -> AlertDialog(
            onDismissRequest = { openDialog = null },
            title = { Text(if (state.isPaused) "Reanudar meta" else "Pausar meta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (state.isPaused) "Volverás a ver esta meta entre tus metas activas y podrás seguir aportando."
                        else "La meta pasará a Metas pausadas. Tu dinero seguirá reservado y todo el historial se conservará.")
                }
            },
            confirmButton = {
                Button(enabled = goal != null && !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
                    onClick = { openDialog = null; if (onPause != null) onPause(!state.isPaused) else onArchive() },
                    modifier = Modifier.testTag("goal_archive_confirm")) { Text(if (state.isPaused) "Reanudar" else "Pausar") }
            },
            dismissButton = { TextButton(onClick = { openDialog = null }, modifier = Modifier.testTag("goal_archive_cancel")) { Text("Cancelar") } },
        )
        null -> Unit
    }
    if (showHistory) {
        com.kipu.app.ui.component.KipuBottomSheet(onDismissRequest = { showHistory = false },
            footer = { TextButton(onClick = { showHistory = false }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar historial") } }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) { GoalEventHistory(state.events) }
        }
    }
}

@Composable
private fun AmountInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' || char == ',' }) },
        label = { Text("Monto") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("goal_detail_amount"),
    )
}

@Composable
private fun AccountPicker(
    accounts: List<GoalBackingAccount>,
    selectedId: String,
    onExpandedChange: (Boolean) -> Unit,
    expanded: Boolean,
    testTag: String,
    onSelected: (String) -> Unit,
) {
    val selected = accounts.firstOrNull { it.id == selectedId }?.name ?: "Elige una cuenta"
    Column {
        OutlinedButton(onClick = { onExpandedChange(true) }, modifier = Modifier.fillMaxWidth().testTag(testTag)) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.name} · libre ${account.available.minorUnits.moneyText()}") },
                    onClick = { onSelected(account.id); onExpandedChange(false) },
                )
            }
        }
    }
}

@Composable
internal fun GoalPicker(
    goals: List<Pair<String, String>>,
    selectedId: String,
    onExpandedChange: (Boolean) -> Unit,
    expanded: Boolean,
    testTag: String = "goal_reassign_destination_picker",
    onSelected: (String) -> Unit,
) {
    val selected = goals.firstOrNull { it.first == selectedId }?.second ?: "Elige una meta activa"
    Column {
        OutlinedButton(onClick = { onExpandedChange(true) }, modifier = Modifier.fillMaxWidth().testTag(testTag)) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            goals.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelected(id); onExpandedChange(false) }) }
        }
    }
}

private fun parseAmount(raw: String, currency: com.kipu.app.core.finance.domain.model.Currency): Money? = runCatching {
    val amount = BigDecimal(raw.trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY)
    Money(amount.movePointRight(2).longValueExact(), currency).takeIf { it.minorUnits > 0L }
}.getOrNull()

private fun GoalStatus.detailLabel(): String = when (this) {
    GoalStatus.ACTIVE -> "Activa"
    GoalStatus.COMPLETED -> "Completada"
    GoalStatus.ARCHIVED -> "Archivada"
    GoalStatus.LOCKED_BY_PLAN -> "Pausada por el plan · historia conservada"
}

private fun Long.moneyText(): String = BigDecimal.valueOf(this, 2).toPlainString()

@Composable
private fun GoalSummaryAmount(label: String, money: Money, modifier: Modifier = Modifier) {
    val colors = rememberPlanningColors()
    val display = formatMoneyTextDisplay(formatMinorUnits(money.minorUnits).removeSuffix(".00"), money.currency.symbol(), LocalBalanceMasked.current)
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, textAlign = TextAlign.Center)
        BasicText(display, modifier = Modifier.fillMaxWidth(), maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 20.sp),
            color = { colors.primaryText },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"))
    }
}
