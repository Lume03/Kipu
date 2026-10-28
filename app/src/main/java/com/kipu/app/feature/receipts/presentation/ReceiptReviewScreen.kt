package com.kipu.app.feature.receipts.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
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
    isBusy: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCalmEmeraldColors()
    var candidateToDiscard by remember { mutableStateOf<ReceiptCandidate?>(null) }
    val contentModifier = if (candidateToDiscard == null) modifier else modifier.blur(4.dp)

    if (candidates.isEmpty()) {
        Column(
            modifier = contentModifier.fillMaxSize().padding(horizontal = 20.dp),
        ) {
            PendingReceiptsHeader(count = 0)
            EmptyReceiptsState(
                modifier = Modifier.weight(1f),
                onHowToShare = onHowToShare,
            )
        }
    } else {
        LazyColumn(
            modifier = contentModifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PendingReceiptsHeader(count = candidates.size) }
            items(candidates, key = ReceiptCandidate::id) { candidate ->
                ReceiptCandidateCard(
                    candidate = candidate,
                    isBusy = isBusy,
                    onReview = { onReview(candidate.id) },
                    onDiscard = { candidateToDiscard = candidate },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    candidateToDiscard?.let { candidate ->
        ReceiptDiscardConfirmationDialog(
            candidate = candidate,
            onDismiss = { candidateToDiscard = null },
            onConfirm = {
                candidateToDiscard = null
                onDiscard(candidate.id)
            },
        )
    }
}

@Composable
private fun PendingReceiptsHeader(count: Int) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Comprobantes pendientes",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.primaryText,
        )
        Surface(
            shape = if (count == 0) CircleShape else RoundedCornerShape(50),
            color = if (count == 0) scheme.surfaceContainerHigh else palette.incomeBg,
            contentColor = if (count == 0) scheme.onSurfaceVariant else palette.incomeText,
            modifier = Modifier.semantics { contentDescription = "$count comprobantes pendientes" },
        ) {
            Text(
                text = if (count == 0) "0" else "$count pendientes",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyReceiptsState(
    onHowToShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(104.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (palette.isDark) scheme.secondaryContainer else palette.incomeBg,
            border = BorderStroke(1.dp, if (palette.isDark) scheme.outlineVariant else palette.incomeBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = palette.primaryDeep,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = "No tienes comprobantes pendientes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = palette.primaryText,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Los comprobantes que compartas desde Yape aparecerán aquí para su revisión.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.secondaryMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = onHowToShare,
            modifier = Modifier.heightIn(min = 56.dp).testTag("receipt_how_to_share_empty"),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = palette.surfaceCard,
                contentColor = palette.primaryDeep,
            ),
        ) {
            Icon(Icons.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("¿Cómo compartir desde Yape?", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ReceiptCandidateCard(
    candidate: ReceiptCandidate,
    isBusy: Boolean,
    onReview: () -> Unit,
    onDiscard: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    val isIncome = candidate.isIncome
    val counterpart = candidate.recipient?.takeIf(String::isNotBlank) ?: "Contraparte pendiente de completar"
    val amount = candidate.amountMinor?.let(::formatReceiptAmount)
    val directionLabel = when (isIncome) {
        true -> "Te yapearon"
        false -> "Yapeaste"
        null -> "Dirección por confirmar"
    }
    val directionColor = when (isIncome) {
        true -> palette.incomeText
        false -> palette.expenseText
        null -> palette.secondaryMuted
    }
    val directionBackground = when (isIncome) {
        true -> palette.incomeBg
        false -> palette.expenseBg
        null -> scheme.surfaceContainerHigh
    }
    val directionBorder = when (isIncome) {
        true -> palette.incomeBorder
        false -> palette.expenseBorder
        null -> scheme.outlineVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("receipt_candidate_${candidate.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceCard),
        border = BorderStroke(1.dp, palette.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (palette.isDark) scheme.secondaryContainer else palette.incomeBg,
                    border = BorderStroke(1.dp, if (palette.isDark) scheme.outlineVariant else palette.incomeBorder),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = receiptInitials(candidate.recipient),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryDeep,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (isIncome) {
                            true -> "REMITENTE"
                            false -> "DESTINATARIO"
                            null -> "CONTRAPARTE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryMuted,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = counterpart,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ReceiptLong,
                    contentDescription = "Comprobante Yape leído localmente",
                    tint = palette.primaryDeep,
                    modifier = Modifier.size(22.dp),
                )
            }

            HorizontalReceiptDivider()

            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (isIncome) {
                                true -> "Monto recibido"
                                false -> "Monto enviado"
                                null -> "Monto"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.secondaryMuted,
                        )
                        Text(
                            text = amount ?: "Pendiente de completar",
                            style = if (amount == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (amount == null) palette.secondaryMuted else palette.primaryText,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = directionBackground,
                        contentColor = directionColor,
                        border = BorderStroke(1.dp, directionBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = when (isIncome) {
                                    true -> Icons.Filled.ArrowDownward
                                    false -> Icons.Filled.NorthEast
                                    null -> Icons.Filled.ReceiptLong
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(directionLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReceiptMetadataItem(
                        icon = Icons.Filled.Tag,
                        label = "N.º operación",
                        value = candidate.reference?.takeIf(String::isNotBlank) ?: "No detectado",
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier.width(1.dp).height(42.dp).background(scheme.outlineVariant),
                    )
                    ReceiptMetadataItem(
                        icon = Icons.Filled.Schedule,
                        label = "Fecha y hora",
                        value = candidate.occurredAt?.let(::formatReceiptDate) ?: "Pendiente",
                        modifier = Modifier.weight(1f),
                    )
                }

                Button(
                    onClick = onReview,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_review_${candidate.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryDeep,
                        contentColor = palette.onPrimaryDeep,
                    ),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Revisar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = onDiscard,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("receipt_discard_${candidate.id}"),
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.secondaryMuted),
                ) {
                    Text("Descartar", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun ReceiptMetadataItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCalmEmeraldColors()
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = palette.secondaryMuted, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = palette.secondaryMuted)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.primaryText,
        )
    }
}

@Composable
private fun HorizontalReceiptDivider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    )
}

@Composable
private fun ReceiptDiscardConfirmationDialog(
    candidate: ReceiptCandidate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    val counterpart = candidate.recipient?.takeIf(String::isNotBlank) ?: "esta contraparte"
    val amount = candidate.amountMinor?.let(::formatReceiptAmount)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("receipt_discard_dialog"),
            shape = RoundedCornerShape(30.dp),
            color = palette.surfaceCard,
            shadowElevation = 20.dp,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(104.dp),
                    shape = CircleShape,
                    color = scheme.errorContainer.copy(alpha = 0.55f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(78.dp),
                            shape = CircleShape,
                            color = scheme.errorContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = scheme.error,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "¿Descartar comprobante?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = palette.primaryText,
                )
                Text(
                    text = buildAnnotatedString {
                        append("Este comprobante")
                        amount?.let {
                            append(" de ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.primaryText)) { append(it) }
                        }
                        append(" de ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.primaryText)) { append(counterpart) }
                        append(" se eliminará de la lista de pendientes.")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.secondaryMuted,
                    textAlign = TextAlign.Center,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.incomeBg,
                    contentColor = palette.incomeText,
                    border = BorderStroke(1.dp, palette.incomeBorder),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "Tus saldos y movimientos no se verán afectados",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_confirm_discard"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Sí, descartar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = palette.primaryText,
                    ),
                ) {
                    Text("Cancelar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
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
        text = {
            Text(
                "Kipu analizará localmente la imagen compartida para proponer un movimiento. " +
                    "No guardará la imagen ni el texto reconocido. Revisa y confirma el movimiento antes de registrarlo.",
            )
        },
        confirmButton = { Button(onClick = onAccept) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Cancelar") } },
    )
}

private fun receiptInitials(name: String?): String {
    val normalized = name.orEmpty().substringAfterLast('-').trim()
    val words = normalized.split(Regex("\\s+")).filter(String::isNotBlank)
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase(Locale.ROOT)
        words.isNotEmpty() -> words.first().take(2).uppercase(Locale.ROOT)
        else -> "YP"
    }
}

private fun formatReceiptAmount(amountMinor: Long): String =
    "S/ ${BigDecimal.valueOf(amountMinor, 2).setScale(2).toPlainString()}"

private fun formatReceiptDate(timestamp: Long): String =
    LIMA_FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(LIMA_ZONE))

private val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")
private val LIMA_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "PE"))
