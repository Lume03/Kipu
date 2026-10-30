package com.kipu.app.feature.receipts.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit
import com.kipu.app.feature.receipts.domain.ReceiptReviewQueue
import com.kipu.app.feature.receipts.domain.RegisteredYapeReceipt
import com.kipu.app.feature.receipts.domain.missingDataCount
import com.kipu.app.feature.receipts.domain.pendingReceipts
import com.kipu.app.feature.receipts.domain.receiptBalanceEffect
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Receipt inbox in its three states (empty, pending, caught up); the financial editor remains the only place that records a movement. */
@OptIn(ExperimentalMaterial3Api::class)
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
    registered: List<RegisteredYapeReceipt> = emptyList(),
    onUpload: () -> Unit = {},
    onStartReview: () -> Unit = { candidates.firstOrNull()?.let { onReview(it.id) } },
    onHistory: () -> Unit = {},
    onMovement: (String) -> Unit = {},
    newCandidateId: String? = null,
    onBottomBarHeightChanged: (Dp) -> Unit = {},
) {
    val palette = rememberCalmEmeraldColors()
    val density = LocalDensity.current
    val pending = pendingReceipts(candidates)
    var candidateToDiscard by remember { mutableStateOf<ReceiptCandidate?>(null) }
    Column(modifier.fillMaxSize()) {
        when {
            pending.isNotEmpty() -> {
                val ready = pending.count { it.missingDataCount == 0 }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Por revisar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                                color = palette.primaryText, letterSpacing = (-.5).sp)
                            ReceiptCountBadge(pending.size)
                        }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReceiptStateChip("$ready listos", ChipTone.Ready, readyChipIcon())
                            if (ready < pending.size) ReceiptStateChip("${pending.size - ready} con datos faltantes", ChipTone.Warning, warningChipIcon())
                        }
                    }
                    Column(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(palette.surfaceCard)
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(22.dp))) {
                        pending.forEachIndexed { index, candidate ->
                            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart && !isBusy) candidateToDiscard = candidate
                                false // The row stays until the user confirms the discard.
                            })
                            SwipeToDismissBox(
                                state = dismiss, enableDismissFromStartToEnd = false, enableDismissFromEndToStart = !isBusy,
                                backgroundContent = {
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd) {
                                        Icon(Icons.Filled.DeleteOutline, "Descartar comprobante", tint = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                },
                            ) {
                                ReceiptInboxRow(candidate, newCandidateId == candidate.id, onClick = { onReview(candidate.id) }, enabled = !isBusy,
                                    onDiscard = { candidateToDiscard = candidate })
                            }
                            if (index < pending.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderSubtle))
                        }
                    }
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.SwipeLeft, null, Modifier.size(16.dp), tint = palette.secondaryMuted)
                        Text("Desliza a la izquierda para descartar.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                    }
                    if (ready > 0) ReceiptBalanceCard(ready, receiptBalanceEffect(pending))
                    Spacer(Modifier.height(4.dp))
                }
                Surface(modifier = Modifier.onSizeChanged { onBottomBarHeightChanged(with(density) { it.height.toDp() }) },
                    color = palette.surfaceCard, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = onStartReview, enabled = !isBusy, shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_start_review")) {
                            Text("Empezar a revisar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp).clip(CircleShape).background(palette.onPrimaryDeep.copy(alpha = .22f)).padding(horizontal = 7.dp),
                                contentAlignment = Alignment.Center) {
                                Text(pending.size.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        TextButton(onClick = onUpload, enabled = !isBusy, modifier = Modifier.heightIn(min = 48.dp).testTag("receipt_upload_link")) {
                            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                            Text("Subir una imagen", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            registered.isEmpty() -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ReceiptEmptyHero(onHowToShare)
                    ReceiptTrustCard()
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReceiptSectionLabel("¿TIENES UNA CAPTURA?", Modifier.padding(horizontal = 4.dp))
                    ReceiptUploadRow(onUpload, !isBusy)
                }
            }
            else -> {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(palette.tealTint(.16f), palette.tealTint(.06f))))
                        .border(1.dp, palette.tealTint(.3f), RoundedCornerShape(24.dp)).padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(56.dp).shadow(8.dp, CircleShape, spotColor = palette.primaryDeep).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF12998C)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CheckCircle, null, Modifier.size(30.dp), tint = Color.White)
                        }
                        Column {
                            Text("Estás al día", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                            Text("No tienes comprobantes por revisar.", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted,
                                modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    Column {
                        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ReceiptSectionLabel("REGISTRADOS DESDE YAPE", Modifier.weight(1f))
                            TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Ver todos", fontWeight = FontWeight.Bold)
                                Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp))
                            }
                        }
                        Column(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(palette.surfaceCard)
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(20.dp))) {
                            val shown = registered.take(3)
                            shown.forEachIndexed { index, receipt ->
                                ReceiptInboxRow(receipt.candidate, false, { onMovement(receipt.transactionId) }, registered = true)
                                if (index < shown.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderSubtle))
                            }
                        }
                    }
                    ReceiptShareNudge(onHowToShare)
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) { ReceiptUploadRow(onUpload, !isBusy) }
            }
        }
    }
    candidateToDiscard?.let { candidate ->
        ReceiptDiscardConfirmationDialog(candidate, onDismiss = { candidateToDiscard = null }, onConfirm = {
            candidateToDiscard = null; onDiscard(candidate.id)
        })
    }
}

/** What the "listos" would do to the balance; computed locally from complete receipts only. */
@Composable
private fun ReceiptBalanceCard(ready: Int, effect: java.math.BigInteger) {
    val palette = rememberCalmEmeraldColors()
    val positive = effect.signum() >= 0
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.tealTint(.07f)).border(1.dp, palette.tealTint(.22f), RoundedCornerShape(18.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.AccountBalanceWallet, null, Modifier.size(22.dp), tint = palette.primaryDeep)
        Column(Modifier.weight(1f)) {
            Text("Si confirmas los $ready listos", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = palette.primaryText)
            Text("tu saldo cambiaría en", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
        }
        Text((if (positive) "+" else "−") + "S/ " + BigDecimal(effect.abs(), 2).toPlainString(), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold, color = if (positive) palette.incomeText else palette.expenseText, maxLines = 1)
    }
}

@Composable
internal fun ReceiptInboxRow(candidate: ReceiptCandidate, isNew: Boolean, onClick: () -> Unit,
    enabled: Boolean = true, registered: Boolean = false, onDiscard: (() -> Unit)? = null, solid: Boolean = true) {
    val palette = rememberCalmEmeraldColors()
    val missing = candidate.missingDataCount
    val tone = when (candidate.isIncome) { true -> palette.incomeText; false -> palette.expenseText; null -> palette.secondaryMuted }
    val compact = registered
    Row(Modifier.fillMaxWidth().background(if (solid) palette.surfaceCard else Color.Transparent).background(if (isNew) palette.tealTint(.1f) else Color.Transparent)
        .drawBehind { if (isNew) drawRect(palette.primaryDeep, Offset.Zero, Size(4.dp.toPx(), size.height)) }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag("receipt_candidate_${candidate.id}")
        .semantics { onDiscard?.let { discard -> customActions = listOf(CustomAccessibilityAction("Descartar comprobante") { discard(); true }) } }
        .heightIn(min = if (compact) 68.dp else 80.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val named = !candidate.recipient.isNullOrBlank()
        Box(Modifier.size(if (compact) 40.dp else 44.dp).clip(CircleShape).background(if (named) palette.tealTint(.14f) else palette.pillTrack), contentAlignment = Alignment.Center) {
            if (named) Text(receiptInitials(candidate.recipient), fontWeight = FontWeight.ExtraBold, color = palette.primaryDeep,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall)
            else Icon(Icons.Filled.Person, null, Modifier.size(22.dp), tint = palette.secondaryMuted)
        }
        Column(Modifier.weight(1f)) {
            Text(candidate.recipient?.takeIf(String::isNotBlank) ?: "Sin contraparte", fontWeight = FontWeight.Bold, color = palette.primaryText,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (missing > 0 && !registered) {
                Text("Faltan $missing datos", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = palette.warningText,
                    modifier = Modifier.padding(top = 3.dp))
            } else {
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (!registered) Icon(receiptTypeIcon(candidate.isIncome), null, Modifier.size(14.dp), tint = tone)
                    Text(candidate.occurredAt?.let { receiptWhen(it, registered) } ?: "Fecha por completar", style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryMuted, maxLines = 2)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            candidate.amountMinor?.let {
                Text((if (candidate.isIncome == true) "+" else if (candidate.isIncome == false) "−" else "") + formatReceiptAmount(it),
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = tone, maxLines = 1)
            }
            if (!registered) when {
                isNew -> ReceiptStateChip("Nuevo", ChipTone.Solid)
                missing == 0 -> ReceiptStateChip("Listo", ChipTone.Ready, readyChipIcon())
                else -> ReceiptStateChip("Completar", ChipTone.Warning)
            }
        }
        if (!registered) Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp), tint = palette.secondaryMuted.copy(alpha = .8f))
    }
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

private val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")
private val MONTHS = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

/** "Hoy · 12:41" and "Ayer · 20:03" for registered rows, "24 Oct · 12:41" for pending ones. */
private fun receiptWhen(timestamp: Long, relative: Boolean): String {
    val at = Instant.ofEpochMilli(timestamp).atZone(LIMA_ZONE)
    val time = String.format(Locale.ROOT, "%02d:%02d", at.hour, at.minute)
    val today = LocalDate.now(LIMA_ZONE)
    val day = at.toLocalDate()
    return when {
        relative && day == today -> "Hoy · $time"
        relative && day == today.minusDays(1) -> "Ayer · $time"
        else -> "${at.dayOfMonth} ${MONTHS[at.monthValue - 1]} · $time"
    }
}

/** "1 de 3" chip in the top bar while a queue is being reviewed. */
@Composable
fun ReceiptQueuePosition(queue: ReceiptReviewQueue) {
    val palette = rememberCalmEmeraldColors()
    Box(Modifier.padding(end = 12.dp).heightIn(min = 30.dp).clip(RoundedCornerShape(50)).background(palette.tealTint(.14f)).padding(horizontal = 12.dp)
        .testTag("receipt_queue_position"), contentAlignment = Alignment.Center) {
        Text(queue.position, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = palette.primaryDeep)
    }
}

/** One segment per receipt in the queue; the ones already reviewed and the current one are filled. */
@Composable
fun ReceiptQueueSegments(queue: ReceiptReviewQueue) {
    val palette = rememberCalmEmeraldColors()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(queue.ids.size) { index ->
            Box(Modifier.weight(1f).height(5.dp).background(if (index <= queue.index) palette.primaryDeep else palette.borderSubtle, RoundedCornerShape(3.dp)))
        }
    }
}
