package com.kipu.app.feature.recurrences.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.recurrences.domain.RecurrenceDraft
import com.kipu.app.feature.recurrences.domain.model.RecurrenceCommandResult
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import com.kipu.app.feature.recurrences.domain.schedule.RecurrenceScheduleCalculator
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun RecurrenceRegistrationScreen(
    source: TransactionItem,
    onDismiss: () -> Unit,
    onCreate: (RecurrenceDraft) -> Unit,
    isSaving: Boolean = false,
    saveError: String? = null,
    savedRule: RecurrenceRule? = null,
    onManageRecurrences: () -> Unit = {},
) {
    val transaction = source.transaction
    var amountMode by remember { mutableStateOf("FIXED") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var customIntervalUnit by remember { mutableStateOf("WEEK") }
    var customInterval by remember { mutableStateOf("1") }
    var nextDueDate by remember { mutableStateOf("") }
    var reminderLeadDays by remember { mutableStateOf("2") }
    var concept by remember(source) {
        mutableStateOf(source.merchantName?.takeIf(String::isNotBlank)
            ?: source.categoryName?.takeIf(String::isNotBlank) ?: "Obligación")
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    val parsedDate = remember(nextDueDate) { runCatching { LocalDate.parse(nextDueDate) }.getOrNull() }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Registrar pago recurrente", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("Movimiento de origen: ${source.merchantName ?: source.categoryName ?: "Movimiento"}", style = MaterialTheme.typography.bodyMedium)
        Text("Importe esperado: ${com.kipu.app.ui.component.formatMinorUnits(transaction.amountMinor)} ${transaction.currency}", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = concept,
            onValueChange = { concept = it },
            label = { Text("Concepto") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("recurrence_concept"),
        )
        Text("Modalidad del importe", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (amountMode == "FIXED") Button(onClick = { amountMode = "FIXED" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_amount_mode_fixed")) { Text("Fijo") }
            else OutlinedButton(onClick = { amountMode = "FIXED" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_amount_mode_fixed")) { Text("Fijo") }
            if (amountMode == "APPROXIMATE") Button(onClick = { amountMode = "APPROXIMATE" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_amount_mode_approximate")) { Text("Aproximado") }
            else OutlinedButton(onClick = { amountMode = "APPROXIMATE" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_amount_mode_approximate")) { Text("Aproximado") }
        }
        if (amountMode == "APPROXIMATE") {
            Text("Monto aproximado · no es un pago realizado", color = MaterialTheme.colorScheme.tertiary)
        } else {
            Text("Monto fijo esperado · no se registra como pago", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Frecuencia", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = frequency == "WEEKLY", onClick = { frequency = "WEEKLY" }, label = { Text("Semanal") }, modifier = Modifier.testTag("recurrence_frequency_weekly"))
            FilterChip(selected = frequency == "BIWEEKLY", onClick = { frequency = "BIWEEKLY" }, label = { Text("Cada 2 semanas") }, modifier = Modifier.testTag("recurrence_frequency_biweekly"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = frequency == "MONTHLY", onClick = { frequency = "MONTHLY" }, label = { Text("Mensual") }, modifier = Modifier.testTag("recurrence_frequency_monthly"))
            FilterChip(selected = frequency == "ANNUALLY", onClick = { frequency = "ANNUALLY" }, label = { Text("Anual") }, modifier = Modifier.testTag("recurrence_frequency_annually"))
        }
        FilterChip(selected = frequency == "CUSTOM", onClick = { frequency = "CUSTOM" }, label = { Text("Personalizada") }, modifier = Modifier.testTag("recurrence_frequency_custom"))
        if (frequency == "CUSTOM") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = customIntervalUnit == "WEEK", onClick = { customIntervalUnit = "WEEK" }, label = { Text("Semanas") }, modifier = Modifier.testTag("recurrence_custom_unit_week"))
                FilterChip(selected = customIntervalUnit == "MONTH", onClick = { customIntervalUnit = "MONTH" }, label = { Text("Meses") }, modifier = Modifier.testTag("recurrence_custom_unit_month"))
            }
            OutlinedTextField(value = customInterval, onValueChange = { customInterval = it.filter(Char::isDigit); validationError = null }, label = { Text("Intervalo") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("recurrence_custom_interval"))
            Text(if (customIntervalUnit == "WEEK") "Rango permitido: 1–5200 semanas" else "Rango permitido: 1–1200 meses", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = nextDueDate,
            onValueChange = { nextDueDate = it; validationError = null },
            label = { Text("Próxima fecha (AAAA-MM-DD)") },
            singleLine = true,
            isError = validationError != null && parsedDate == null,
            modifier = Modifier.fillMaxWidth().testTag("recurrence_next_due_date"),
        )
        OutlinedTextField(
            value = reminderLeadDays,
            onValueChange = { reminderLeadDays = it.filter(Char::isDigit).take(3); validationError = null },
            label = { Text("Avisar días antes") },
            supportingText = { Text("Entre 0 y 30 días; 0 desactiva los recordatorios") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("recurrence_reminder_lead_days"),
        )
        validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("recurrence_save_error")) }
        savedRule?.let {
            Text(
                if (it.syncState == RecurrenceSyncState.PENDING) "Guardado en este dispositivo · pendiente de sincronización" else "Recurrencia sincronizada",
                modifier = Modifier.testTag("recurrence_save_result"),
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Estado: ${it.status} · Próxima fecha: ${it.nextDueDate}")
        }
        if (isSaving) CircularProgressIndicator(Modifier.testTag("recurrence_loading"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cerrar") }
            Button(
                onClick = {
                    val date = parsedDate
                    when {
                        date == null -> validationError = "Selecciona la próxima fecha de vencimiento"
                        concept.isBlank() -> validationError = "Escribe un concepto"
                        frequency !in FREQUENCIES -> validationError = "Selecciona una frecuencia válida"
                        reminderLeadDays.toIntOrNull()?.let { it in 0..30 } != true -> validationError = "El aviso debe estar entre 0 y 30 días"
                        frequency == "CUSTOM" && customInterval.toLongOrNull()?.let { it in if (customIntervalUnit == "WEEK") 1L..5200L else 1L..1200L } != true -> validationError = "El intervalo debe estar dentro del rango permitido"
                        !RecurrenceScheduleCalculator.isOccurrence(date, frequency, if (frequency == "CUSTOM") customInterval.toLong() else 1L, if (frequency == "CUSTOM") customIntervalUnit else null, date) -> validationError = "La próxima fecha no pertenece a la secuencia"
                        else -> {
                            validationError = null
                            onCreate(
                                RecurrenceDraft(
                                    templateTransaction = JsonObject(
                                        mapOf(
                                            "concept" to JsonPrimitive(concept.trim()),
                                            "type" to JsonPrimitive(transaction.type.name),
                                            "amount_minor" to JsonPrimitive(transaction.amountMinor),
                                            "currency_code" to JsonPrimitive(transaction.currency),
                                            "source_account_id" to (transaction.sourceAccountId?.let(::JsonPrimitive) ?: JsonNull),
                                            "destination_account_id" to (transaction.destinationAccountId?.let(::JsonPrimitive) ?: JsonNull),
                                            "category_id" to (transaction.categoryId?.let(::JsonPrimitive) ?: JsonNull),
                                            "merchant_id" to (transaction.merchantId?.let(::JsonPrimitive) ?: JsonNull),
                                            "merchant_provisional_text" to (transaction.merchantProvisionalText?.let(::JsonPrimitive) ?: JsonNull),
                                        ),
                                    ),
                                    amountMode = amountMode,
                                    frequency = frequency,
                                    intervalCount = if (frequency == "CUSTOM") customInterval.toLong() else 1L,
                                    customIntervalUnit = if (frequency == "CUSTOM") customIntervalUnit else null,
                                    startDate = date,
                                    endDate = null,
                                    nextDueDate = date,
                                    reminderLeadDays = reminderLeadDays.toInt(),
                                ),
                            )
                        }
                    }
                },
                enabled = !isSaving && savedRule == null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_save"),
            ) { Text("Guardar") }
        }
        TextButton(onClick = onManageRecurrences, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ver mis recurrencias") }
        Spacer(Modifier.height(8.dp))
    }
}

private val FREQUENCIES = setOf("WEEKLY", "BIWEEKLY", "MONTHLY", "ANNUALLY", "CUSTOM")
