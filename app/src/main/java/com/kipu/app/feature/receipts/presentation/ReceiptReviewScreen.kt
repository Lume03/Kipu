package com.kipu.app.feature.receipts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
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
    onHowToShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comprobantes pendientes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(candidates.size.toString(), Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (candidates.isEmpty()) item {
            Column(Modifier.fillParentMaxSize().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(88.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    }
                }
                Text("No tienes comprobantes pendientes", style = MaterialTheme.typography.titleMedium)
                Text("Cuando compartas un comprobante desde Yape, aparecerá aquí para que puedas revisarlo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onHowToShare) { Text("¿Cómo compartir desde Yape?") }
              }
            }
        }
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
