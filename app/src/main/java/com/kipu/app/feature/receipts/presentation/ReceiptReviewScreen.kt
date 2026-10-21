package com.kipu.app.feature.receipts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Review queue. Financial account/category entry remains in QuickMovementScreen. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ReceiptReviewScreen(
    candidates: List<ReceiptCandidate>,
    onReview: (candidateId: String) -> Unit,
    onEdit: (candidateId: String, ReceiptCandidateEdit) -> Unit,
    onDiscard: (candidateId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Comprobantes pendientes", style = MaterialTheme.typography.titleLarge) }
        if (candidates.isEmpty()) item { Text("No hay comprobantes pendientes.") }
        items(candidates, key = ReceiptCandidate::id) { candidate ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when (candidate.isIncome) {
                        true -> "Ingreso · Te Yapearon"
                        false -> "Gasto · Yapeaste"
                        else -> "Dirección por confirmar"
                    }, style = MaterialTheme.typography.titleMedium)
                    Text(candidate.amountMinor?.let { "S/ ${BigDecimal.valueOf(it, 2).toPlainString()}" } ?: "Monto pendiente de completar")
                    Text(candidate.occurredAt?.let { LIMA_FORMAT.format(Instant.ofEpochMilli(it).atZone(LIMA_ZONE)) }
                        ?: "Fecha y hora pendiente de completar")
                    Text(candidate.recipient ?: "Contraparte pendiente de completar")
                    candidate.reference?.let { Text("Nro. de operación: $it") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onReview(candidate.id) }) { Text("Revisar comprobante") }
                        TextButton(onClick = { onDiscard(candidate.id) }) { Text("Descartar") }
                    }
                }
            }
        }
    }
}

/** Explicit consent is separate from choosing the app in Android's share sheet. */
@Composable
fun ReceiptShareConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Leer comprobante en este dispositivo") },
        text = { Text("Kipu analizará localmente la imagen compartida para proponer un movimiento. No guardará la imagen ni el texto reconocido. Revisa y confirma el movimiento antes de registrarlo.") },
        confirmButton = { Button(onClick = onAccept) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Cancelar") } },
    )
}

private val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")
private val LIMA_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "PE"))
