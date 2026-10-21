package com.kipu.app.feature.goals.presentation

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
) {
    val goal = state.goal
    val focusManager = LocalFocusManager.current
    var amountText by rememberSaveable(goal?.id) { mutableStateOf("") }
    var backingAccountId by rememberSaveable(goal?.id) { mutableStateOf("") }
    var sourceAccountId by rememberSaveable(goal?.id) { mutableStateOf("") }
    var confirmTransfer by rememberSaveable(goal?.id) { mutableStateOf(false) }
    var backingMenuExpanded by remember { mutableStateOf(false) }
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
                Text("Elige de qué cuenta se respalda. Una meta puede recibir reservas de varias cuentas de la misma moneda.")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                    label = { Text("Importe") },
                    prefix = { Text(if (goal.currency.name == "PEN") "S/ " else "$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().testTag("goal_contribution_amount"),
                )
                AccountChoice(
                    title = "Cuenta de respaldo",
                    accounts = accounts,
                    selectedId = chosenBackingId,
                    expanded = backingMenuExpanded,
                    onExpand = { backingMenuExpanded = it },
                    onSelect = { backingAccountId = it },
                    testTag = "goal_backing_account_picker",
                )
                selectedBacking?.let { GoalBackingStatus(listOf(it)) }
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
                Button(
                    onClick = { amount?.let { onReserve(it, chosenBackingId, effectiveTransfer, sourceAccountId.takeIf(String::isNotBlank)) } },
                    enabled = !state.isSaving && canReserve,
                    modifier = Modifier.fillMaxWidth().testTag("goal_contribution_submit"),
                ) {
                    Text(
                        if (state.isSaving) "Guardando…"
                        else if (effectiveTransfer) "Trasladar y reservar"
                        else "Reservar sin traslado",
                    )
                }
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
