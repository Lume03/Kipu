package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus

/** Specialized active operations retain their domain-specific reversal flows. */
internal fun canDeleteFromHistory(item: TransactionItem): Boolean =
    item.transaction.status == TransactionStatus.VOIDED ||
        (item.transaction.status in setOf(TransactionStatus.ACTIVE, TransactionStatus.CONFIRMED, TransactionStatus.REVISED) && !isSpecializedMovement(item))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeletableTransactionRow(
    item: TransactionItem,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    enabled: Boolean = true,
) {
    val eligible = canDeleteFromHistory(item) && enabled
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentEligible by rememberUpdatedState(eligible)
    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart && currentEligible) currentOnDelete()
        false // Keep the row until confirmation and durable success, as in receipts.
    })
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = eligible,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).semantics {
            if (eligible) customActions = listOf(CustomAccessibilityAction("Eliminar del historial") { onDelete(); true })
        },
        backgroundContent = {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text("Eliminar", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        TransactionRow(item = item, onClick = onClick, offline = offline, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun DeleteMovementDialog(
    item: TransactionItem,
    busy: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val voided = item.transaction.status == TransactionStatus.VOIDED
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = { if (!busy) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp).testTag("movement_delete_dialog"),
            shape = RoundedCornerShape(30.dp), color = palette.surfaceCard, shadowElevation = 20.dp, tonalElevation = 2.dp,
        ) {
            Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(Modifier.size(104.dp), shape = CircleShape, color = scheme.errorContainer.copy(alpha = .55f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(Modifier.size(78.dp), shape = CircleShape, color = scheme.errorContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.DeleteOutline, null, Modifier.size(36.dp), tint = scheme.error)
                            }
                        }
                    }
                }
                Text("¿Eliminar movimiento?", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = palette.primaryText)
                val counterpart = item.merchantName ?: item.categoryName ?: "Movimiento"
                val currency = if (item.transaction.currency == "PEN") "S/" else item.transaction.currency
                Text("$counterpart · $currency ${formatMinorUnits(item.transaction.amountMinor)}",
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = palette.primaryText)
                Text(if (voided) "Este movimiento ya está anulado. Se ocultará del historial en este dispositivo, sin cambiar nuevamente tu saldo."
                    else "Se anulará este movimiento y se revertirá su efecto en el saldo. Luego se ocultará del historial en este dispositivo.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = palette.secondaryMuted)
                Text("El registro contable se conserva. La ocultación no se sincroniza con otros dispositivos.",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = palette.secondaryMuted)
                error?.let { Text(it, color = scheme.error, modifier = Modifier.testTag("movement_delete_error")) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick = onConfirm, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("movement_delete_confirm"),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)) {
                    Icon(Icons.Filled.DeleteOutline, null, Modifier.size(21.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(if (error != null) "Reintentar" else "Eliminar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(onClick = onCancel, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.surfaceContainerHigh, contentColor = palette.primaryText)) {
                    Text("Cancelar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
