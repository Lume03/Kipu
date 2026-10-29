package com.kipu.app.feature.recurrences.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.capture.domain.ExpectedPayment
import com.kipu.app.feature.capture.domain.ExpectedPaymentStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import java.time.LocalDate

@Composable
fun RecurrenceLifecycleScreen(
    recurrences: List<RecurrenceRule>,
    isLoading: Boolean,
    errorMessage: String?,
    onPause: (RecurrenceRule) -> Unit,
    onRequestResume: (RecurrenceRule) -> Unit,
    onCancel: (RecurrenceRule) -> Unit,
    onConfirmResume: () -> Unit,
    onDismissConfirmation: () -> Unit,
    resumeDate: LocalDate? = null,
    pendingOperation: Boolean = false,
    expectedPayments: List<ExpectedPayment> = emptyList(),
    onBack: () -> Unit,
) {
    var cancelTarget by remember { mutableStateOf<RecurrenceRule?>(null) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mis recurrencias", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (isLoading && recurrences.isEmpty()) CircularProgressIndicator()
        if (recurrences.isEmpty() && !isLoading) Text("Todavía no tienes recurrencias")
        recurrences.forEach { rule ->
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rule.templateTransaction["concept"]?.toString()?.trim('"') ?: "Obligación", style = MaterialTheme.typography.titleMedium)
                    Text(recurrenceStateLabel(rule.status, rule.nextDueDate), modifier = Modifier.testTag("recurrence_state_${rule.id}"))
                    (expectedPayments.firstOrNull { it.recurrenceId == rule.id && it.dueDate == rule.nextDueDate }
                        ?: expectedPayments.filter { it.recurrenceId == rule.id }.maxByOrNull { it.dueDate })?.let { payment ->
                        if (payment.status != ExpectedPaymentStatus.SKIPPED) Text(if (payment.status == ExpectedPaymentStatus.ATTENDED) "Pago atendido" else "Pago esperado")
                    }
                    when (rule.syncState) {
                        RecurrenceSyncState.PENDING -> Text("Pendiente de sincronización local", modifier = Modifier.testTag("recurrence_offline_${rule.id}"))
                        RecurrenceSyncState.CONFLICT -> Text("Conflicto de sincronización · requiere revisión", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("recurrence_conflict_${rule.id}"))
                        RecurrenceSyncState.FAILED -> Text("No se pudo sincronizar · vuelve a intentarlo", color = MaterialTheme.colorScheme.error)
                        RecurrenceSyncState.SYNCED -> Unit
                    }
                    if (rule.status == RecurrenceStatus.ACTIVE) TextButton(onClick = { onPause(rule) }, modifier = Modifier.heightIn(min = 48.dp).testTag("recurrence_pause_${rule.id}")) { Text("Pausar") }
                    if (rule.status == RecurrenceStatus.PAUSED) TextButton(onClick = { onRequestResume(rule) }, modifier = Modifier.heightIn(min = 48.dp).testTag("recurrence_resume_${rule.id}")) { Text("Reanudar") }
                    if (rule.status != RecurrenceStatus.CANCELLED) TextButton(onClick = { cancelTarget = rule }, modifier = Modifier.heightIn(min = 48.dp).testTag("recurrence_cancel_${rule.id}")) { Text("Cancelar recurrencia") }
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("recurrence_lifecycle_error")) }
        if (pendingOperation) CircularProgressIndicator(Modifier.testTag("recurrence_lifecycle_loading"))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Volver") }
    }
    if (resumeDate != null) AlertDialog(
        onDismissRequest = onDismissConfirmation,
        title = { Text("Confirmar reanudación") },
        text = { Text("La próxima fecha válida será $resumeDate. No se crearán gastos de periodos omitidos.") },
        confirmButton = { TextButton(onClick = onConfirmResume, modifier = Modifier.testTag("recurrence_resume_confirm")) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismissConfirmation, modifier = Modifier.testTag("recurrence_resume_back")) { Text("Volver") } },
    )
    cancelTarget?.let { rule -> AlertDialog(
        onDismissRequest = { cancelTarget = null },
        title = { Text("Cancelar recurrencia") },
        text = { Text("La regla quedará cancelada y su historial se conservará.") },
        confirmButton = { TextButton(onClick = { cancelTarget = null; onCancel(rule) }, modifier = Modifier.testTag("recurrence_cancel_confirm")) { Text("Confirmar cancelación") } },
        dismissButton = { TextButton(onClick = { cancelTarget = null }, modifier = Modifier.testTag("recurrence_cancel_back")) { Text("Volver") } },
    ) }
}

/** "Activa · próximo 3 nov": user-facing state and next date, without internal enums or revisions. */
internal fun recurrenceStateLabel(
    status: com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus,
    nextDueDate: java.time.LocalDate,
): String {
    val label = when (status) {
        com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus.ACTIVE -> "Activa"
        com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus.PAUSED -> "Pausada"
        com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus.CANCELLED -> "Cancelada"
    }
    val pattern = if (nextDueDate.year == java.time.LocalDate.now().year) "d MMM" else "d MMM yyyy"
    val date = nextDueDate.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.forLanguageTag("es-PE")))
    return if (status == com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus.CANCELLED) label else "$label · próximo $date"
}
