package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.ui.component.KipuOptionalDateField
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalFormScreen(
    existingGoal: Goal?,
    accounts: List<GoalBackingAccount>,
    isSaving: Boolean,
    errorMessage: String?,
    onNavigateBack: () -> Unit,
    replacementCandidates: List<Goal> = emptyList(),
    currentReserved: Money? = null,
    hasPremiumAccess: Boolean = false,
    onSave: (goalId: String?, name: String, target: Money, targetDate: LocalDate?, linkedAccountId: String?, replacementGoalId: String?) -> Unit,
) {
    var name by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.name.orEmpty()) }
    var amount by rememberSaveable(existingGoal?.id) {
        mutableStateOf(existingGoal?.target?.minorUnits?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty())
    }
    var currency by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.currency?.name ?: Currency.PEN.name) }
    var dateText by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.targetDate?.toString().orEmpty()) }
    var linkedAccount by rememberSaveable(existingGoal?.id) { mutableStateOf(existingGoal?.linkedAccountId) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var replacementDialogVisible by rememberSaveable(existingGoal?.id) { mutableStateOf(false) }
    var selectedReplacementId by rememberSaveable(existingGoal?.id) { mutableStateOf<String?>(null) }
    val selectedCurrency = runCatching { Currency.valueOf(currency) }.getOrDefault(Currency.PEN)
    val compatibleAccounts = accounts.filter { it.currency == selectedCurrency }
    val parsedTarget = parseGoalTarget(amount, selectedCurrency)
    val targetExceedsReserved = currentReserved?.let { reserved ->
        reserved.currency == selectedCurrency && parsedTarget?.minorUnits?.let { it > reserved.minorUnits } == true
    } == true
    val requiresReplacement = existingGoal?.status == GoalStatus.COMPLETED && targetExceedsReserved &&
        !hasPremiumAccess && replacementCandidates.size >= 2
    val parsedDate = dateText.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val dateIsValid = dateText.isBlank() || parsedDate != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingGoal == null) "Nueva meta" else "Editar meta") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("La meta organiza tu avance. Crear o editarla no mueve dinero.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("goal_form_name"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Currency.PEN, Currency.USD).forEach { option ->
                    if (selectedCurrency == option) {
                        Button(onClick = { currency = option.name }) { Text(option.label()) }
                    } else {
                        OutlinedButton(onClick = {
                            currency = option.name
                            if (accounts.none { it.id == linkedAccount && it.currency == option }) linkedAccount = null
                        }) { Text(option.label()) }
                    }
                }
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                label = { Text("Monto objetivo") },
                prefix = { Text(if (selectedCurrency == Currency.PEN) "S/ " else "$ ") },
                singleLine = true,
                isError = amount.isNotBlank() && parsedTarget == null,
                modifier = Modifier.fillMaxWidth().testTag("goal_form_target"),
            )
            KipuOptionalDateField(
                value = dateText,
                onValueChange = { dateText = it.take(10) },
                label = stringResource(R.string.optional_date_goal_label),
                calendarContentDescription = stringResource(R.string.optional_date_goal_calendar),
                tag = "goal_form_date",
                enabled = !isSaving,
                isError = !dateIsValid,
            )
            Column {
                Text("Cuenta vinculada (opcional)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                val selectedName = compatibleAccounts.firstOrNull { it.id == linkedAccount }?.name
                    ?: if (linkedAccount == null) "Sin cuenta vinculada · meta virtual" else "Cuenta no disponible"
                OutlinedButton(onClick = { accountMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedName)
                }
                DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Sin cuenta vinculada · meta virtual") },
                        onClick = { linkedAccount = null; accountMenuExpanded = false },
                    )
                    compatibleAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text("${account.name} · libre ${account.available.minorUnits.formatAsMoney()}") },
                            onClick = { linkedAccount = account.id; accountMenuExpanded = false },
                        )
                    }
                }
                Text("La cuenta real es solo una referencia; la reserva se elige al aportar.", style = MaterialTheme.typography.bodySmall)
            }
            if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (requiresReplacement) replacementDialogVisible = true
                    else parsedTarget?.let { onSave(existingGoal?.id, name, it, parsedDate, linkedAccount, null) }
                },
                enabled = !isSaving && name.isNotBlank() && parsedTarget != null && dateIsValid,
                modifier = Modifier.fillMaxWidth().testTag("goal_save_button"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (isSaving) "Guardando…" else "Guardar meta")
            }
        }
    }
    if (replacementDialogVisible) {
        AlertDialog(
            onDismissRequest = { replacementDialogVisible = false; selectedReplacementId = null },
            title = { Text("Elige una meta para pausar") },
            text = {
                Column {
                    Text("Al reactivar esta meta, una de tus metas activas pasará a estar bloqueada por el plan. Sus reservas e historial se conservarán.")
                    replacementCandidates.filter { it.status == GoalStatus.ACTIVE }.forEach { candidate ->
                        androidx.compose.material3.RadioButton(
                            selected = selectedReplacementId == candidate.id,
                            onClick = { selectedReplacementId = candidate.id },
                            modifier = Modifier.testTag("replacement_radio_${candidate.id}"),
                        )
                        Text(candidate.name, modifier = Modifier.testTag("replacement_${candidate.id}"))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedReplacementId != null && !isSaving,
                    onClick = {
                        val id = selectedReplacementId ?: return@TextButton
                        parsedTarget?.let { onSave(existingGoal?.id, name, it, parsedDate, linkedAccount, id) }
                        replacementDialogVisible = false
                    },
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { replacementDialogVisible = false; selectedReplacementId = null }) { Text("Cancelar") }
            },
        )
    }
}

private fun parseGoalTarget(raw: String, currency: Currency): Money? = runCatching {
    val normalized = raw.trim().replace(',', '.')
    val decimal = BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY)
    val minor = decimal.movePointRight(2).longValueExact()
    Money(minor, currency).takeIf { it.minorUnits > 0L }
}.getOrNull()

private fun Currency.label(): String = if (this == Currency.PEN) "Soles (PEN)" else "Dólares (USD)"

private fun Long.formatAsMoney(): String = BigDecimal.valueOf(this, 2).toPlainString()
