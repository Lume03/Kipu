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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.saveable.rememberSaveable
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.feature.categories.presentation.components.MerchantAvatar
import com.kipu.app.feature.categories.presentation.components.MerchantLogoDrawables
import com.kipu.app.feature.categories.presentation.components.getMerchantVisualProfile
import java.time.ZoneOffset
import java.time.Instant
import java.time.format.DateTimeFormatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.recurrences.domain.RecurrenceDraft
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import com.kipu.app.feature.recurrences.domain.schedule.RecurrenceScheduleCalculator
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceRegistrationScreen(
    source: TransactionItem,
    onDismiss: () -> Unit,
    onCreate: (RecurrenceDraft) -> Unit,
    isSaving: Boolean = false,
    saveError: String? = null,
    savedRule: RecurrenceRule? = null,
    onManageRecurrences: (() -> Unit)? = null,
) {
    val transaction = source.transaction
    var amountMode by rememberSaveable(source.transaction.id) { mutableStateOf("FIXED") }
    var frequency by rememberSaveable(source.transaction.id) { mutableStateOf("MONTHLY") }
    var customIntervalUnit by rememberSaveable(source.transaction.id) { mutableStateOf("WEEK") }
    var customInterval by rememberSaveable(source.transaction.id) { mutableStateOf("1") }
    var nextDueDate by rememberSaveable(source.transaction.id) { mutableStateOf(LocalDate.now(com.kipu.app.core.di.LIMA_BUSINESS_ZONE).toString()) }
    var reminderLeadDays by rememberSaveable(source.transaction.id) { mutableStateOf("2") }
    var concept by rememberSaveable(source.transaction.id) {
        mutableStateOf(source.merchantName?.takeIf(String::isNotBlank)
            ?: source.categoryName?.takeIf(String::isNotBlank) ?: "Obligación")
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    val parsedDate = remember(nextDueDate) { runCatching { LocalDate.parse(nextDueDate) }.getOrNull() }

    val colors = rememberCalmEmeraldColors()
    var showDatePicker by remember { mutableStateOf(false) }
    var showFrequencyMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(recurrenceNotificationsEnabled(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = recurrenceNotificationsEnabled(context)
    }
    LifecycleResumeEffect(Unit) {
        notificationsEnabled = recurrenceNotificationsEnabled(context)
        onPauseOrDispose { }
    }
    val frequencyOptions = linkedMapOf("WEEKLY" to "Semanal", "BIWEEKLY" to "Quincenal", "MONTHLY" to "Mensual", "ANNUALLY" to "Anual", "CUSTOM" to "Personalizada")
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = parsedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { nextDueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                validationError = null
                showDatePicker = false
            }, modifier = Modifier.testTag("recurrence_date_confirm")) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = picker, modifier = Modifier.testTag("recurrence_date_picker")) }
    }
    Column(
        Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Registrar pago recurrente", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("Importe esperado: ${if (transaction.currency == "PEN") "S/" else if (transaction.currency == "USD") "$" else transaction.currency} ${com.kipu.app.ui.component.formatMinorUnits(transaction.amountMinor)}", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = concept,
            onValueChange = { concept = it },
            label = { Text("Concepto") },
            leadingIcon = {
                MerchantAvatar(
                    drawableId = MerchantLogoDrawables.resolve(source.merchantLogoKey),
                    remoteLogoUrl = source.merchantLogoUrl,
                    remoteDarkLogoUrl = source.merchantLogoDarkUrl,
                    profile = getMerchantVisualProfile(source.merchantName ?: concept, source.merchantBrandColor),
                    size = 36.dp,
                )
            },
            supportingText = { source.merchantName?.takeIf(String::isNotBlank)?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("recurrence_concept"),
        )
        Text("Modalidad del importe", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (amountMode == "FIXED") Button(colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep), onClick = { amountMode = "FIXED" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_amount_mode_fixed")) { Text("Fijo") }
            else OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryDeep), onClick = { amountMode = "FIXED" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_amount_mode_fixed")) { Text("Fijo") }
            if (amountMode == "APPROXIMATE") Button(colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep), onClick = { amountMode = "APPROXIMATE" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_amount_mode_approximate")) { Text("Aproximado") }
            else OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryDeep), onClick = { amountMode = "APPROXIMATE" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recurrence_amount_mode_approximate")) { Text("Aproximado") }
        }
        if (amountMode == "APPROXIMATE") {
            Text("Monto aproximado · no es un pago realizado", color = MaterialTheme.colorScheme.tertiary)
        } else {
            Text("Monto fijo esperado · no se registra como pago", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            OutlinedButton(onClick = { showFrequencyMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("recurrence_frequency_selector")) {
                Text("Frecuencia: ${frequencyOptions[frequency]}", color = colors.primaryText)
            }
            DropdownMenu(expanded = showFrequencyMenu, onDismissRequest = { showFrequencyMenu = false }) {
                frequencyOptions.forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(if (value == "BIWEEKLY") "$label · cada 2 semanas (14 días)" else label) },
                        onClick = { frequency = value; showFrequencyMenu = false },
                        modifier = Modifier.testTag("recurrence_frequency_${value.lowercase()}"))
                }
            }
        }
        if (frequency == "BIWEEKLY") Text("Quincenal: cada 2 semanas (14 días), desde la fecha elegida.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        if (frequency == "CUSTOM") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { customIntervalUnit = "WEEK" }, modifier = Modifier.heightIn(min = 48.dp).testTag("recurrence_custom_unit_week")) { Text(if (customIntervalUnit == "WEEK") "Semanas (seleccionado)" else "Semanas") }
                OutlinedButton(onClick = { customIntervalUnit = "MONTH" }, modifier = Modifier.heightIn(min = 48.dp).testTag("recurrence_custom_unit_month")) { Text(if (customIntervalUnit == "MONTH") "Meses (seleccionado)" else "Meses") }
            }
            OutlinedTextField(value = customInterval, onValueChange = { customInterval = it.filter(Char::isDigit); validationError = null }, label = { Text("Intervalo") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("recurrence_custom_interval"))
            Text(if (customIntervalUnit == "WEEK") "Rango permitido: 1–5200 semanas" else "Rango permitido: 1–1200 meses", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("recurrence_next_due_date"),
        ) {
            Text("Próxima fecha: ${parsedDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Seleccionar"}", color = colors.primaryText)
        }
        OutlinedTextField(
            value = reminderLeadDays,
            onValueChange = { reminderLeadDays = it.filter(Char::isDigit).take(3); validationError = null },
            label = { Text("Avisar días antes") },
            supportingText = { Text("Entre 0 y 30 días; 0 desactiva los recordatorios") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("recurrence_reminder_lead_days"),
        )
        if (reminderLeadDays.toIntOrNull()?.let { it > 0 } == true && !notificationsEnabled) {
            Text("Las notificaciones están desactivadas. El aviso se conservará en el centro de notificaciones cuando la recurrencia esté sincronizada.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            TextButton(onClick = {
                if (!permissionRequested && android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionRequested = true
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }) { Text("Activar notificaciones", color = colors.primaryDeep) }
        }
        validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("recurrence_save_error")) }
        savedRule?.let {
            Text(
                if (it.syncState == RecurrenceSyncState.PENDING) "Guardado en este dispositivo · pendiente de sincronización" else "Recurrencia sincronizada",
                modifier = Modifier.testTag("recurrence_save_result"),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(recurrenceStateLabel(it.status, it.nextDueDate))
        }
        if (isSaving) CircularProgressIndicator(Modifier.testTag("recurrence_loading"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cerrar") }
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
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
        onManageRecurrences?.let { manage -> TextButton(onClick = manage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ver mis recurrencias") } }
        Spacer(Modifier.height(8.dp))
    }
}

private val FREQUENCIES = setOf("WEEKLY", "BIWEEKLY", "MONTHLY", "ANNUALLY", "CUSTOM")

private fun recurrenceNotificationsEnabled(context: android.content.Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return false
    val channel = manager.getNotificationChannel(com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderWorker.CHANNEL_ID)
    return channel == null || channel.importance != android.app.NotificationManager.IMPORTANCE_NONE
}
