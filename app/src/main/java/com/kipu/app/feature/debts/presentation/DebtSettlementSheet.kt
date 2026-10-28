package com.kipu.app.feature.debts.presentation

import com.kipu.app.ui.theme.KipuModalSystemBars
import com.kipu.app.ui.theme.kipuBottomSheetProperties

import com.kipu.app.ui.theme.rememberKipuColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.debts.domain.DebtAmountParser
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.ui.component.KipuFilterChip
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.rememberPlanningColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtSettlementSheet(
    debt: DebtSummary,
    state: DebtSettlementUiState,
    onPrincipalAmountChange: (String) -> Unit,
    onInterestAmountChange: (String) -> Unit,
    onAccountSelected: (String) -> Unit,
    onInterestCategorySelected: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val themeColors = rememberKipuColors()
    val colors = rememberPlanningColors()
    val principalMinor = DebtAmountParser.toMinorUnits(state.principalAmount)
    val interestMinor = DebtAmountParser.toNonNegativeMinorUnits(state.interestAmount)
    val principalTooHigh = principalMinor != null && principalMinor > debt.remainingPrincipalMinor
    val payable = debt.obligationType == DebtObligationType.PAYABLE
    val needsInterestCategory = payable && (interestMinor ?: 0L) > 0L
    val selectedAccount = state.availableAccounts.firstOrNull { it.id == state.selectedAccountId }
    val accountValid = selectedAccount != null && selectedAccount.currencyCode == debt.currencyCode
    val categoryValid = !needsInterestCategory || state.availableInterestCategories.any {
        it.id == state.selectedInterestCategoryId && it.categoryType in setOf("GENERAL", "EXPENSE")
    }
    val canSave = principalMinor != null && !principalTooHigh && interestMinor != null && accountValid &&
        categoryValid && !state.isSaving && state.conflictMessage == null
    val cashTotal = if (principalMinor != null && interestMinor != null) principalMinor + interestMinor else null
    val remainingText = debtMoney(debt.currencyCode, debt.remainingPrincipalMinor)
    val fullAmount = formatMinorUnits(debt.remainingPrincipalMinor).replace(",", "")
    val action = if (payable) "pago" else "cobro"

    ModalBottomSheet(
        properties = kipuBottomSheetProperties(),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(themeColors.border, RoundedCornerShape(999.dp)),
            )
        },
    ) {
        KipuModalSystemBars()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        if (payable) "Registrar pago" else "Registrar cobro",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "${debt.counterpartyName} · saldo $remainingText",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryMuted,
                        maxLines = 1,
                    )
                }
                DebtTypePill(payable)
            }

            state.conflictMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.expenseBg,
                    border = BorderStroke(1.dp, colors.expenseBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.expenseText, modifier = Modifier.size(18.dp))
                        Text(message, color = colors.expenseText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            DebtAmountCard(
                label = "Principal a amortizar",
                amount = state.principalAmount,
                onAmountChange = onPrincipalAmountChange,
                currencyCode = debt.currencyCode,
                supportingText = "Máximo $remainingText",
                enabled = !state.isSaving,
                tag = "settlement-principal",
            ) {
                KipuFilterChip(
                    selected = state.principalAmount == fullAmount,
                    onClick = { onPrincipalAmountChange(fullAmount) },
                    label = { Text("Todo $remainingText", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = colors.primaryText,
                        selectedContainerColor = colors.primaryDeep,
                        selectedLabelColor = colors.onPrimaryDeep,
                    ),
                )
                AnimatedVisibility(visible = principalTooHigh) {
                    Text(
                        "El principal supera el saldo pendiente.",
                        color = colors.expenseText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                "El interés se registra aparte como gasto o ingreso.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryMuted,
                lineHeight = 18.sp,
            )

            OutlinedTextField(
                value = state.interestAmount,
                onValueChange = onInterestAmountChange,
                label = { Text("Interés adicional (opcional)") },
                placeholder = { Text("0.00") },
                supportingText = {
                    Text(if (payable) "El interés sí forma parte de tus gastos." else "El interés sí forma parte de tus ingresos.")
                },
                prefix = { Text("${debtCurrencySymbol(debt.currencyCode)} ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                isError = interestMinor == null,
                modifier = Modifier.fillMaxWidth().testTag("settlement-interest"),
            )
            AnimatedVisibility(visible = interestMinor == null) {
                Text(
                    "Revisa el interés; usa hasta dos decimales y no un monto negativo.",
                    color = colors.expenseText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            DebtSectionLabel(if (payable) "Pagar desde" else "Cobrar en")
            if (state.availableAccounts.isEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = colors.expenseBg, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No hay cuentas activas en ${debt.currencyCode}.",
                        color = colors.expenseText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.availableAccounts.forEach { account ->
                        val matchesCurrency = account.currencyCode == debt.currencyCode
                        DebtAccountOptionCard(
                            name = account.name,
                            detail = if (matchesCurrency) "Moneda: ${account.currencyCode}" else "Moneda distinta (${account.currencyCode})",
                            selected = state.selectedAccountId == account.id,
                            enabled = matchesCurrency,
                            onClick = { onAccountSelected(account.id) },
                            balance = account.balanceMinor?.let { debtMoney(account.currencyCode, it) },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = needsInterestCategory) {
                Surface(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Category, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(18.dp))
                            Text("Categoría del interés", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        if (state.availableInterestCategories.isEmpty()) {
                            Text("Crea o sincroniza una categoría de gasto para el interés.", color = colors.expenseText, style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.availableInterestCategories
                                .filter { it.categoryType in setOf("GENERAL", "EXPENSE") }
                                .forEach { category ->
                                    val isSelected = state.selectedInterestCategoryId == category.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .selectable(
                                                selected = isSelected,
                                                role = Role.RadioButton,
                                                onClick = { onInterestCategorySelected(category.id) },
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(selectedColor = colors.primaryDeep),
                                        )
                                        Text(category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                        }
                    }
                }
            }

            cashTotal?.let { total ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.incomeBg,
                    border = BorderStroke(1.dp, colors.incomeBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (payable) "Saldrá de tu cuenta:" else "Entrará a tu cuenta:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.incomeText,
                        )
                        Text(
                            debtMoney(debt.currencyCode, total),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.incomeText,
                        )
                    }
                }
            }

            state.errorMessage?.let { message ->
                if (message != "El principal supera el saldo pendiente.") {
                    Text(message, color = colors.expenseText, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("settlement-save"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep),
            ) {
                Text(
                    when {
                        state.isSaving -> "Guardando…"
                        cashTotal != null && canSave -> "Registrar $action de ${debtMoney(debt.currencyCode, cashTotal)}"
                        else -> "Registrar $action"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun DebtTypePill(payable: Boolean) {
    val colors = rememberPlanningColors()
    Surface(shape = RoundedCornerShape(20.dp), color = if (payable) colors.expenseBg else colors.incomeBg) {
        Text(
            if (payable) "Yo debo" else "Me deben",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (payable) colors.expenseText else colors.incomeText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
