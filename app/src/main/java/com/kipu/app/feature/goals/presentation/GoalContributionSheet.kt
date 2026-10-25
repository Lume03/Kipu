package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GoalContributionSheet(
    state: GoalContributionUiState,
    onNavigateBack: () -> Unit,
    onReserve: (amount: Money, backingAccountId: String, confirmTransfer: Boolean, sourceAccountId: String?) -> Unit,
    onWithdraw: (() -> Unit)? = null,
) {
    val colors = rememberCalmEmeraldColors()
    val goal = state.goal
    val focusManager = LocalFocusManager.current
    var amountText by rememberSaveable(goal?.id) { mutableStateOf("") }
    var backingAccountId by rememberSaveable(goal?.id) { mutableStateOf("") }
    var sourceAccountId by rememberSaveable(goal?.id) { mutableStateOf("") }
    var confirmTransfer by rememberSaveable(goal?.id) { mutableStateOf(false) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    val accounts = state.accounts.filter { it.currency == goal?.currency }
    val chosenBackingId = backingAccountId.takeIf { id -> accounts.any { it.id == id } }
        ?: goal?.linkedAccountId?.takeIf { id -> accounts.any { it.id == id } }
        ?: accounts.firstOrNull()?.id.orEmpty()
    val selectedBacking = accounts.firstOrNull { it.id == chosenBackingId }
    val selectedSource = accounts.firstOrNull { it.id == sourceAccountId }
    val amount = goal?.let { parseContributionAmount(amountText, it.currency) }
    val effectiveTransfer = confirmTransfer && selectedSource?.id != selectedBacking?.id
    val canReserve = amount != null && chosenBackingId.isNotBlank() &&
        goal.status == GoalStatus.ACTIVE && if (effectiveTransfer) {
            selectedSource != null && selectedSource.available >= amount
        } else {
            selectedBacking?.available?.let { it >= amount } == true
        }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (goal != null && !state.isLoading) Surface(color = colors.surfaceCard) {
                Button(
                    onClick = { focusManager.clearFocus(); amount?.let { onReserve(it, chosenBackingId, effectiveTransfer, sourceAccountId.takeIf(String::isNotBlank)) } },
                    enabled = !state.isSaving && canReserve,
                    modifier = Modifier.navigationBarsPadding().imePadding().padding(16.dp).fillMaxWidth().heightIn(min = 52.dp).testTag("goal_contribution_submit"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (state.isSaving) "Guardando…" else if (effectiveTransfer) "Trasladar y reservar" else "Aportar", fontWeight = FontWeight.Bold)
                    if (!state.isSaving && !effectiveTransfer && amount != null) MoneyText(amount, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Aportar a ${goal?.name.orEmpty()}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            goal == null -> Text("No encontramos esta meta.", Modifier.padding(padding).padding(24.dp))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                onWithdraw?.let { withdraw ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KipuFilterChip(selected = true, onClick = {}, label = { Text("Aportar") }, modifier = Modifier.weight(1f))
                        KipuFilterChip(selected = false, onClick = withdraw, label = { Text("Retirar") }, modifier = Modifier.weight(1f))
                    }
                }
                KipuCard(containerColor = colors.surfaceCard, borderColor = null, shape = RoundedCornerShape(24.dp), elevation = 1.dp) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Monto", style = MaterialTheme.typography.titleSmall, color = colors.secondaryMuted)
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                            label = { Text("Importe") },
                            prefix = { Text(goal.currency.symbol(), color = colors.primaryDeep, style = MaterialTheme.typography.headlineMedium) },
                            textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().testTag("goal_contribution_amount"),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(100, 300, 500).forEach { quick ->
                                KipuFilterChip(selected = amount?.minorUnits == quick * 100L,
                                    onClick = { amountText = quick.toString() },
                                    label = { Text("${goal.currency.symbol()} $quick") })
                            }
                        }
                    }
                }
                Text("Aportar desde", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(Modifier.selectableGroup().testTag("goal_backing_account_picker"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        val selected = account.id == chosenBackingId
                        Surface(
                            modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { backingAccountId = account.id }),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else colors.surfaceCard,
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primaryDeep else colors.borderSubtle),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(Modifier.padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RadioButton(selected = selected, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(account.name, fontWeight = FontWeight.SemiBold)
                                    Text("Disponible para reservar", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                                }
                                MoneyText(account.available, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.titleSmall.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (accounts.isEmpty()) Text("No hay cuentas activas compatibles para respaldar esta meta.")
                if (selectedBacking != null && amount != null && !effectiveTransfer) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Dinero disponible después", style = MaterialTheme.typography.bodyMedium)
                            MoneyText(selectedBacking.available.minus(amount), fontWeight = FontWeight.Bold)
                            if (selectedBacking.available < amount) Text("El importe supera el disponible de esta cuenta.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = confirmTransfer,
                        onCheckedChange = { confirmTransfer = it },
                        modifier = Modifier.testTag("goal_transfer_toggle"),
                    )
                    Column {
                        Text("Confirmar traslado interno", style = MaterialTheme.typography.bodyMedium)
                        Text("Solo se moverá dinero entre cuentas de Kipu si activas esta opción.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (confirmTransfer) {
                    AccountChoice(
                        title = "Cuenta origen",
                        accounts = accounts,
                        selectedId = sourceAccountId,
                        expanded = sourceMenuExpanded,
                        onExpand = { sourceMenuExpanded = it },
                        onSelect = { sourceAccountId = it },
                        testTag = "goal_transfer_source_picker",
                    )
                    selectedSource?.let { Text("Saldo libre en origen: ${it.available.minorUnits.asCurrency()}") }
                }
                CardNote(effectiveTransfer)
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(12.dp))

            }
        }
    }
}

@Composable
private fun AccountChoice(
    title: String,
    accounts: List<GoalBackingAccount>,
    selectedId: String,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    testTag: String,
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { onExpand(true) }, modifier = Modifier.fillMaxWidth().testTag(testTag)) {
            Text(selected?.let { "${it.name} · libre ${it.available.minorUnits.asCurrency()}" } ?: "Selecciona una cuenta")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpand(false) }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.name} · libre ${account.available.minorUnits.asCurrency()}") },
                    onClick = { onSelect(account.id); onExpand(false) },
                )
            }
        }
    }
}

@Composable
private fun CardNote(confirmTransfer: Boolean) {
    androidx.compose.material3.Card {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (confirmTransfer) "El traslado interno y la reserva se registran juntos. No se crea gasto ni ingreso."
                else "La reserva no cambia el saldo real y no duplica el patrimonio.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Completar la meta tampoco libera automáticamente el dinero.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun parseContributionAmount(raw: String, currency: com.kipu.app.core.finance.domain.model.Currency): Money? = runCatching {
    val decimal = BigDecimal(raw.trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY)
    Money(decimal.movePointRight(2).longValueExact(), currency).takeIf { it.minorUnits > 0L }
}.getOrNull()

private fun Long.asCurrency(): String = BigDecimal.valueOf(this, 2).toPlainString()
