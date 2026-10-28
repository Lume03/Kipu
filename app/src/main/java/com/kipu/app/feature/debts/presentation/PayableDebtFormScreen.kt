package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.debts.domain.DebtAmountParser
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.ui.component.KipuOptionalDateField
import com.kipu.app.ui.theme.rememberPlanningColors

@Composable
fun PayableDebtFormScreen(
    state: DebtFormUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onPrincipalAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningModeChange: (DebtOpeningMode) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    onSwitchType: (() -> Unit)? = null,
) = DebtOpeningFormScreen(
    obligationType = DebtObligationType.PAYABLE,
    state = state,
    onCounterpartyNameChange = onCounterpartyNameChange,
    onPrincipalAmountChange = onPrincipalAmountChange,
    onCurrencyChange = onCurrencyChange,
    onOpeningModeChange = onOpeningModeChange,
    onAccountSelected = onAccountSelected,
    onDueDateChange = onDueDateChange,
    onNotesChange = onNotesChange,
    onSave = onSave,
    onNavigateBack = onNavigateBack,
    onSwitchType = onSwitchType,
)

@Composable
fun ReceivableDebtFormScreen(
    state: DebtFormUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onPrincipalAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningModeChange: (DebtOpeningMode) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    onSwitchType: (() -> Unit)? = null,
) = DebtOpeningFormScreen(
    obligationType = DebtObligationType.RECEIVABLE,
    state = state,
    onCounterpartyNameChange = onCounterpartyNameChange,
    onPrincipalAmountChange = onPrincipalAmountChange,
    onCurrencyChange = onCurrencyChange,
    onOpeningModeChange = onOpeningModeChange,
    onAccountSelected = onAccountSelected,
    onDueDateChange = onDueDateChange,
    onNotesChange = onNotesChange,
    onSave = onSave,
    onNavigateBack = onNavigateBack,
    onSwitchType = onSwitchType,
)

@Composable
private fun DebtOpeningFormScreen(
    obligationType: DebtObligationType,
    state: DebtFormUiState,
    onCounterpartyNameChange: (String) -> Unit,
    onPrincipalAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOpeningModeChange: (DebtOpeningMode) -> Unit,
    onAccountSelected: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    onSwitchType: (() -> Unit)?,
) {
    val payable = obligationType == DebtObligationType.PAYABLE
    val personLabel = if (payable) "Acreedor (a quién le debes)" else "Deudor (a quién le prestaste)"
    val movementTitle = if (payable) "Me prestaron" else "Presté dinero ahora"
    val movementDescription = if (payable) {
        "El saldo de la cuenta aumenta por el principal recibido."
    } else {
        "El saldo de la cuenta disminuye por el dinero entregado."
    }
    val colors = rememberPlanningColors()
    val amountMinor = DebtAmountParser.toMinorUnits(state.principalAmount)
    val name = state.counterpartyName.trim()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    "Nueva deuda o préstamo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (amountMinor != null && name.isNotEmpty()) {
                    Text(
                        "${if (payable) "Debes" else "Te deben"} ${debtMoney(state.currencyCode, amountMinor)} · $name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryMuted,
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = state.isValidForSave(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("save-debt"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAction),
                ) {
                    Text(if (state.isSaving) "Guardando…" else "Guardar", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DebtSectionLabel("¿Qué quieres registrar?")
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DebtTypeOption(
                    title = "Yo debo",
                    description = "Dinero que me prestaron",
                    selected = payable,
                    onClick = if (payable) null else onSwitchType,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                DebtTypeOption(
                    title = "Me deben",
                    description = "Dinero que presté",
                    selected = !payable,
                    onClick = if (payable) onSwitchType else null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            DebtSectionLabel(personLabel)
            OutlinedTextField(
                value = state.counterpartyName,
                onValueChange = onCounterpartyNameChange,
                placeholder = { Text(if (payable) "Ej.: Préstamo BCP" else "Ej.: Carlos R.") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = personLabel },
            )

            DebtAmountCard(
                label = "Monto total",
                amount = state.principalAmount,
                onAmountChange = onPrincipalAmountChange,
                currencyCode = state.currencyCode,
                onCurrencyChange = onCurrencyChange,
                supportingText = "Solo el principal; los intereses se registran al amortizar o cancelar.",
                enabled = !state.isSaving,
                tag = "debt-principal",
            )

            DebtSectionLabel("Origen del dinero")
            OpeningModeOption(
                selected = state.openingMode == DebtOpeningMode.NEW_CASH_FLOW,
                title = movementTitle,
                description = movementDescription,
                tag = "opening-new",
                onClick = { onOpeningModeChange(DebtOpeningMode.NEW_CASH_FLOW) },
            )
            OpeningModeOption(
                selected = state.openingMode == DebtOpeningMode.HISTORICAL,
                title = "Ya estaba registrado previamente",
                description = "El saldo ya lo refleja en tus cuentas; no se registrará un segundo movimiento.",
                tag = "opening-historical",
                onClick = { onOpeningModeChange(DebtOpeningMode.HISTORICAL) },
            )

            AnimatedVisibility(visible = state.openingMode == DebtOpeningMode.NEW_CASH_FLOW) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DebtSectionLabel(if (payable) "Cuenta que recibió el dinero" else "Cuenta de origen")
                    if (state.availableAccounts.isEmpty()) {
                        Text("No hay cuentas activas disponibles en ${state.currencyCode}.", color = MaterialTheme.colorScheme.error)
                    } else {
                        state.availableAccounts.forEach { account ->
                            val currencyMatches = account.currencyCode == state.currencyCode
                            DebtAccountOptionCard(
                                name = account.name,
                                detail = if (currencyMatches) account.currencyCode else "Moneda distinta (${account.currencyCode})",
                                selected = state.selectedAccountId == account.id,
                                enabled = currencyMatches,
                                onClick = { onAccountSelected(account.id) },
                                balance = account.balanceMinor?.let { debtMoney(account.currencyCode, it) },
                            )
                        }
                    }
                }
            }

            DebtSectionLabel("Vencimiento y notas", optionalHint = "(opcional)")
            KipuOptionalDateField(
                value = state.dueDateInput,
                onValueChange = onDueDateChange,
                label = stringResource(R.string.optional_date_due_label),
                calendarContentDescription = stringResource(R.string.optional_date_due_calendar),
                tag = "debt-due-date",
                calendarOnly = true,
                enabled = !state.isSaving,
                isError = state.dueDateInput.length >= 10 && state.dueDate == null,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = { Text("Notas (opcional)") },
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.heightIn(min = 4.dp))
        }
    }
}

/** "Yo debo" / "Me deben" card. The current type comes from the route; the other one switches it. */
@Composable
private fun DebtTypeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = rememberPlanningColors()
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .selectable(
                selected = selected,
                enabled = selected || onClick != null,
                role = Role.RadioButton,
                onClick = { onClick?.invoke() },
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.incomeBg else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primaryDeep else colors.borderSubtle),
    ) {
        Column(Modifier.heightIn(min = 72.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        }
    }
}

@Composable
private fun OpeningModeOption(
    selected: Boolean,
    title: String,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    val emeraldColors = rememberPlanningColors()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) emeraldColors.incomeBg else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) emeraldColors.primaryAction else emeraldColors.borderSubtle,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = emeraldColors.primaryAction),
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp, horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
