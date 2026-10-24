package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementRefundSheet(
    currency: String,
    remainingMinor: Long,
    purchaseOccurredAt: Long,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (amountMinor: Long, occurredAt: Long) -> Unit,
) {
    val emerald = rememberCalmEmeraldColors()
    val masked = LocalBalanceMasked.current
    val esPe = remember { Locale.forLanguageTag("es-PE") }
    val purchaseDate = remember(purchaseOccurredAt, esPe) {
        SimpleDateFormat("d 'de' MMMM 'de' yyyy", esPe).format(Date(purchaseOccurredAt))
    }
    val today = remember { System.currentTimeMillis() }
    var occurredAt by remember { mutableLongStateOf(today) }
    var amountInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val localZone = remember { ZoneId.systemDefault() }
    val pickerToday = remember(today, localZone) {
        Instant.ofEpochMilli(today).atZone(localZone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = pickerToday)
    val amountMinor = remember(amountInput) { parseRefundAmount(amountInput) }
    val validAmount = amountMinor != null && amountMinor in 1..remainingMinor
    val currencySymbol = when (currency.uppercase(Locale.ROOT)) {
        "PEN" -> "S/"
        "USD" -> "$"
        else -> currency.uppercase(Locale.ROOT)
    }
    val refundDate = remember(occurredAt, esPe) {
        SimpleDateFormat("d 'de' MMMM 'de' yyyy", esPe).format(Date(occurredAt))
    }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("movement_refund_sheet"),
        header = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Registrar reembolso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_refund")) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar reembolso")
                }
            }
        },
        footer = {
            Button(
                onClick = { amountMinor?.let { onSubmit(it, occurredAt) } },
                enabled = validAmount && !isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp).testTag("btn_confirm_refund"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = emerald.primaryDeep,
                    contentColor = emerald.onPrimaryDeep,
                ),
            ) {
                Text(if (isSubmitting) "Guardando reembolso…" else "Confirmar reembolso", fontWeight = FontWeight.Bold)
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Disponible para reembolsar", style = MaterialTheme.typography.labelLarge, color = emerald.secondaryMuted)
            MoneyText(
                amount = formatMinorUnits(remainingMinor),
                currencySymbol = currencySymbol,
                modifier = Modifier.testTag("refund_remaining_amount"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = amountInput,
                onValueChange = { input ->
                    if (input.matches(Regex("^\\d{0,9}([.,]\\d{0,2})?$"))) amountInput = input
                },
                label = { Text("Importe del reembolso") },
                leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.SemiBold) },
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().testTag("refund_amount_input")
                    .privateAmount(masked, !isSubmitting, onChange = { amountInput = it }),
            )
            if (amountMinor != null && amountMinor > remainingMinor) {
                Text(
                    "El importe supera el remanente disponible.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("refund_amount_error"),
                )
            }

            Text("Fecha real del reembolso", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("El dinero volverá a la cuenta o tarjeta de esta compra en la fecha del reembolso.", style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_refund_date"),
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(refundDate)
            }

            Row(
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "El gasto neto y el presupuesto se ajustarán en el periodo de la compra original: $purchaseDate"
                },
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = emerald.primaryDeep)
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("El gasto neto y el presupuesto se ajustarán en el periodo de la compra original.", style = MaterialTheme.typography.bodySmall)
                    Text("Compra original: $purchaseDate", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("refund_server_error"),
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        val localDate = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        occurredAt = localDate.atStartOfDay(localZone).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = datePickerState) }
    }
}

private fun parseRefundAmount(input: String): Long? {
    if (input.isBlank()) return null
    return runCatching {
        val decimal = BigDecimal(input.replace(',', '.'))
        if (decimal.scale() > 2 || decimal <= BigDecimal.ZERO) return null
        decimal.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }.getOrNull()
}
