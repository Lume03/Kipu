package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private enum class GoalDetailDialog { RELEASE, REASSIGN, ARCHIVE }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GoalDetailScreen(
    state: GoalDetailUiState,
    onNavigateBack: () -> Unit,
    onContribute: () -> Unit,
    onRelease: (backingAccountId: String, amount: Money, replacementGoalId: String?) -> Unit,
    onReassign: (destinationGoalId: String, backingAccountId: String, amount: Money) -> Unit,
    onArchive: () -> Unit,
) {
    val goal = state.goal
    val progress = state.progress
    var openDialog by remember { mutableStateOf<GoalDetailDialog?>(null) }
    var amountText by rememberSaveable(goal?.id) { mutableStateOf("") }
    var backingAccountId by rememberSaveable(goal?.id) { mutableStateOf("") }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(goal?.name ?: "Detalle de meta") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            goal == null || progress == null -> Text("No encontramos esta meta.", Modifier.padding(padding).padding(24.dp))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Text(goal.status.detailLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Reservado", style = MaterialTheme.typography.labelMedium)
                        MoneyText(progress.reserved, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Objetivo", style = MaterialTheme.typography.labelMedium)
                        MoneyText(goal.target, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                LinearProgressIndicator(
                    progress = { progress.progressPercent.divide(BigDecimal.valueOf(100)).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Avance ${progress.progressPercent.stripTrailingZeros().toPlainString()}%", style = MaterialTheme.typography.bodySmall)
                Text(
                    goal.targetDate?.let { "Fecha objetivo: $it" } ?: "Sin fecha objetivo",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (goal.linkedAccountId == null) "Meta virtual, sin cuenta real vinculada"
                    else "Cuenta real vinculada como referencia",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (goal.status == GoalStatus.COMPLETED) {
                    Text(
                        "Completaste el objetivo. La reserva permanece intacta hasta que decidas liberarla o reasignarla.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (goal.status == GoalStatus.ACTIVE) {
                    Button(onClick = onContribute, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  Aportar o reservar")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { openDialog = GoalDetailDialog.RELEASE },
                        enabled = !state.isSaving && releasableAccounts.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("goal_release"),
                    ) { Text("Liberar") }
                    OutlinedButton(
                        onClick = { openDialog = GoalDetailDialog.REASSIGN },
                        enabled = !state.isSaving && releasableAccounts.isNotEmpty() && candidateGoals.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("goal_reassign"),
                    ) { Text("Reasignar") }
                }
                if (progress.reserved.minorUnits != 0L) {
                    Text("Para archivar, libera o reasigna toda la reserva. Archivar no ejecuta movimientos.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = { openDialog = GoalDetailDialog.ARCHIVE },
                    enabled = !state.isSaving && goal.status != GoalStatus.ARCHIVED && progress.reserved.minorUnits == 0L,
                    modifier = Modifier.fillMaxWidth().testTag("goal_archive"),
                ) { Text(if (goal.status == GoalStatus.ARCHIVED) "Meta archivada" else "Archivar meta") }
                GoalBackingStatus(state.backingAccounts)
                GoalEventHistory(state.events)
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    when (openDialog) {
        GoalDetailDialog.RELEASE -> {
            var selectedAccount by rememberSaveable(goal?.id) { mutableStateOf(releasableAccounts.firstOrNull()?.id.orEmpty()) }
            val account = releasableAccounts.firstOrNull { it.id == selectedAccount }
            val releaseAmount = account?.let { parseAmount(amountText, it.currency) }
            val maxRelease = account?.let { goalReservedAccounts[it.id] } ?: 0L
            val needsReplacement = goal != null && progress != null && goal.status == GoalStatus.COMPLETED &&
                releaseAmount != null && progress.reserved.minus(releaseAmount).let { it < goal.target } &&
                !state.hasPremiumAccess && state.otherGoals.count { it.status == GoalStatus.ACTIVE } >= 2
            val canRelease = releaseAmount != null && releaseAmount.minorUnits <= maxRelease &&
                (!needsReplacement || destinationGoalId in candidateGoals.map { it.id })
            AlertDialog(
                onDismissRequest = { openDialog = null },
                title = { Text("Liberar reserva") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Liberar devuelve disponibilidad a la cuenta; no crea ingreso ni movimiento.")
                        AccountPicker(releasableAccounts, selectedAccount, { accountMenuExpanded = it }, accountMenuExpanded, "goal_detail_account_picker") { selectedAccount = it }
                        AmountInput(amountText, { amountText = it })
                        if (needsReplacement) {
                            Text("Al liberar, esta meta volverá a estar activa y se alcanzaría el límite Free.")
                            GoalPicker(candidateGoals.map { it.id to it.name }, destinationGoalId,
                                { destinationMenuExpanded = it }, destinationMenuExpanded,
                                testTag = "goal_release_replacement_picker") { destinationGoalId = it }
                        }
                    }
                },
                confirmButton = {
                    TextButton(enabled = canRelease, onClick = {
                        if (account != null && releaseAmount != null) {
                            onRelease(account.id, releaseAmount, destinationGoalId.takeIf { needsReplacement })
                            openDialog = null
                            amountText = ""
                        }
                    }, modifier = Modifier.testTag("goal_release_confirm")) { Text("Liberar") }
                },
                dismissButton = { TextButton(onClick = { openDialog = null; amountText = ""; destinationGoalId = "" }, modifier = Modifier.testTag("goal_release_cancel")) { Text("Cancelar") } },
            )
        }
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
            title = { Text("Archivar meta") },
            text = { Text("Se conservarán la meta y todos sus eventos. No se liberarán fondos ni se creará un movimiento.") },
            confirmButton = {
                TextButton(onClick = { openDialog = null; onArchive() }, modifier = Modifier.testTag("goal_archive_confirm")) { Text("Archivar") }
            },
            dismissButton = { TextButton(onClick = { openDialog = null }, modifier = Modifier.testTag("goal_archive_cancel")) { Text("Cancelar") } },
        )
        null -> Unit
    }
}

@Composable
private fun AmountInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' || char == ',' }) },
        label = { Text("Monto") },
        singleLine = true,
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
private fun GoalPicker(
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
