package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun DebtPaymentVoidAction(payment: DebtSettlementActivity, onVoid: (DebtSettlementActivity) -> Unit) {
    val syncMessage = when (payment.syncStatus) {
        "CONFLICT" -> "Conflicto de sincronización. Revisa el movimiento vinculado."
        "FAILED", "FAILED_PERMANENT", "REJECTED" -> "No se pudo sincronizar. Revisa el movimiento vinculado."
        "PENDING", "RETRY", "IN_FLIGHT" -> "Pendiente de sincronización"
        else -> null
    }
    syncMessage?.let {
        Text(it, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (payment.eventType == "PAYMENT" && payment.transactionId != null && !payment.isVoided) {
        TextButton(onClick = { onVoid(payment) }, modifier = Modifier.testTag("debt_void_payment_${payment.eventId}")) {
            Text("Anular pago", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun DebtPaymentVoidDialog(
    payment: DebtSettlementActivity,
    isVoiding: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isVoiding) onDismiss() },
        modifier = Modifier.testTag("debt_void_payment_dialog_${payment.eventId}"),
        title = { Text("¿Anular pago?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Se revertirán el principal y el interés de este pago o cobro. El saldo pendiente se restaurará y la deuda volverá a estar activa si estaba cerrada. El registro se conservará en el historial.")
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isVoiding, modifier = Modifier.testTag("debt_void_payment_confirm")) {
                Text(if (isVoiding) "Anulando…" else "Anular pago")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isVoiding) { Text("Cancelar") } },
    )
}
