package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.feature.accounts.domain.model.Card as FinancialCard
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.presentation.FinancialTerminology
import com.kipu.app.feature.accounts.presentation.detail.Kibo3DCreditCard
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** R10 credit card terms editor, laid out like the "Editar cuenta" sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardTermsSheet(
    card: CreditCard,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (creditLimitMinorUnits: Long, billingDay: Int, dueDay: Int, lastFourDigits: String, alias: String?) -> Unit,
) {
    var alias by remember(card.id, card.alias) { mutableStateOf(card.alias.orEmpty()) }
    var lastFourDigits by remember(card.id, card.lastFourDigits) { mutableStateOf(card.lastFourDigits) }
    var creditLimit by remember(card.id, card.creditLimitMinorUnits) {
        mutableStateOf(formatMinorUnits(card.creditLimitMinorUnits))
    }
    var billingDay by remember(card.id, card.billingDay) { mutableStateOf(card.billingDay.toString()) }
    var dueDay by remember(card.id, card.dueDay) { mutableStateOf(card.dueDay.toString()) }
    var attemptedSave by remember(card.id) { mutableStateOf(false) }
    val colors = rememberCalmEmeraldColors()

    val parsedLimit = remember(creditLimit) { MoneyInputParser.parseMinorUnits(creditLimit) }
    val parsedBillingDay = remember(billingDay) { billingDay.toIntOrNull() }
    val parsedDueDay = remember(dueDay) { dueDay.toIntOrNull() }
    val validDigits = FinancialCard.isValidLastFourDigits(lastFourDigits)
    val validLimit = parsedLimit != null && parsedLimit >= 0L
    val validBillingDay = parsedBillingDay != null && parsedBillingDay in 1..31
    val validDueDay = parsedDueDay != null && parsedDueDay in 1..31
    val canSave = validLimit && validBillingDay && validDueDay && validDigits && !isSaving

    // Swipe-down must respect the same rule as the close button: no dismiss while saving.
    val currentIsSaving by rememberUpdatedState(isSaving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !currentIsSaving },
    )

    // Live preview only takes values the domain accepts; anything else keeps the saved value.
    val previewCard = runCatching {
        card.copy(
            alias = alias.trim().ifBlank { card.alias },
            lastFourDigits = if (validDigits) lastFourDigits else card.lastFourDigits,
            creditLimitMinorUnits = if (validLimit) requireNotNull(parsedLimit) else card.creditLimitMinorUnits,
            billingDay = if (validBillingDay) requireNotNull(parsedBillingDay) else card.billingDay,
            dueDay = if (validDueDay) requireNotNull(parsedDueDay) else card.dueDay,
        )
    }.getOrDefault(card)

    KipuBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        modifier = Modifier.testTag("credit_card_terms_sheet"),
        header = {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Editar tarjeta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (!isSaving) onDismiss() }, enabled = !isSaving) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar edición")
                }
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Kibo3DCreditCard(card = previewCard)

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddFormLabel("Nombre visible o alias", tag = "Obligatorio")
                        AddFormTextField(
                            value = alias,
                            onValueChange = { alias = it.take(40) },
                            placeholder = card.alias ?: "${card.issuer} ${card.network}",
                            modifier = Modifier.testTag("credit_card_terms_alias"),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddFormLabel("Últimos 4 dígitos de la tarjeta", tag = "4 dígitos", isError = attemptedSave && !validDigits)
                        AddFormTextField(
                            value = lastFourDigits,
                            onValueChange = { lastFourDigits = it.filter { char -> FinancialCard.isAsciiDigit(char) }.take(4) },
                            placeholder = "Ej. 8978",
                            prefix = "••••",
                            keyboardType = KeyboardType.Number,
                            errorMessage = "Ingresa los 4 dígitos numéricos finales.".takeIf { attemptedSave && !validDigits },
                            modifier = Modifier.testTag("credit_card_terms_digits"),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AddFormLabel(
                            "${FinancialTerminology.TOTAL_CREDIT_LINE} (${card.currency.name})",
                            tag = "${card.currency.name} (${card.currency.symbol()})",
                            isError = attemptedSave && !validLimit,
                        )
                        AddFormTextField(
                            value = creditLimit,
                            onValueChange = { creditLimit = it.take(18) },
                            placeholder = "0.00",
                            prefix = card.currency.symbol(),
                            keyboardType = KeyboardType.Decimal,
                            errorMessage = "Ingresa un monto válido igual o mayor a cero.".takeIf { attemptedSave && !validLimit },
                            modifier = Modifier.testTag("credit_card_terms_limit"),
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AddFormLabel("Día de corte", isError = attemptedSave && !validBillingDay)
                            AddFormTextField(
                                value = billingDay,
                                onValueChange = { billingDay = it.filter(Char::isDigit).take(2) },
                                placeholder = "1",
                                prefix = "Día",
                                keyboardType = KeyboardType.Number,
                                errorMessage = "Entre 1 y 31.".takeIf { attemptedSave && !validBillingDay },
                                modifier = Modifier.testTag("credit_card_terms_billing_day"),
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AddFormLabel("Día de pago", isError = attemptedSave && !validDueDay)
                            AddFormTextField(
                                value = dueDay,
                                onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                                placeholder = "1",
                                prefix = "Día",
                                keyboardType = KeyboardType.Number,
                                errorMessage = "Entre 1 y 31.".takeIf { attemptedSave && !validDueDay },
                                modifier = Modifier.testTag("credit_card_terms_due_day"),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            attemptedSave = true
                            if (canSave) {
                                onSave(
                                    requireNotNull(parsedLimit),
                                    requireNotNull(parsedBillingDay),
                                    requireNotNull(parsedDueDay),
                                    lastFourDigits,
                                    alias.ifBlank { null },
                                )
                            }
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_credit_card_terms"),
                    ) {
                        Text(if (isSaving) "Guardando..." else "Guardar cambios", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
