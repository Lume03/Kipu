package com.kipu.app.feature.capture.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.feature.capture.domain.CaptureCandidateRecord
import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** HU-51 inbox: pending captured movements. Nothing here changes the ledger. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureInboxScreen(
    state: CaptureInboxUiState,
    onBack: () -> Unit,
    onOpenCandidate: (candidateId: String) -> Unit,
    onDiscard: (candidateId: String) -> Unit,
    onOpenPermissions: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCalmEmeraldColors()
    val snackbar = remember { SnackbarHostState() }
    var toDiscard by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Capturas por revisar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surfaceCard,
                    titleContentColor = palette.primaryText,
                    navigationIconContentColor = palette.primaryText,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("capture_inbox_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "automation") {
                        CaptureAutomationCard(state.automation, onOpenPermissions)
                    }
                    if (state.pendingAssociations.isNotEmpty()) {
                        item(key = "associations_header") {
                            Text("Pagos por asociar", style = MaterialTheme.typography.titleMedium, color = palette.primaryText, fontWeight = FontWeight.SemiBold)
                        }
                        items(state.pendingAssociations, key = { "association_${it.id}" }) { candidate ->
                            OutlinedButton(
                                onClick = { onOpenCandidate(candidate.id) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("capture_associate_${candidate.id}"),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(candidate.merchantSourceText ?: candidate.provider.displayName())
                                    Text("${formatCaptureAmount(candidate.amountMinor, candidate.currency)} · Elegir pago esperado")
                                }
                            }
                        }
                    }
                    item(key = "header") { CaptureInboxHeader(state.candidates.size) }
                    if (state.loadFailed) {
                        item(key = "error") {
                            InboxNotice(
                                text = "No se pudieron cargar las capturas. Vuelve a abrir esta pantalla.",
                                warning = true,
                            )
                        }
                    }
                    if (state.candidates.isEmpty() && !state.loadFailed) {
                        item(key = "empty") { CaptureInboxEmptyState(state.automation, onOpenPermissions) }
                    }
                    items(state.candidates, key = CaptureCandidateRecord::id) { candidate ->
                        CaptureCandidateCard(
                            candidate = candidate,
                            enabled = !state.isBusy,
                            onReview = { onOpenCandidate(candidate.id) },
                            onDiscard = { toDiscard = candidate.id },
                        )
                    }
                }
            }
        }
    }

    toDiscard?.let { id ->
        CaptureDiscardDialog(
            onDismiss = { toDiscard = null },
            onConfirm = {
                toDiscard = null
                onDiscard(id)
            },
        )
    }
}

@Composable
private fun CaptureInboxHeader(count: Int) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Movimientos detectados",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.primaryText,
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = if (count == 0) scheme.surfaceContainerHigh else palette.incomeBg,
            contentColor = if (count == 0) scheme.onSurfaceVariant else palette.incomeText,
            modifier = Modifier.semantics { contentDescription = "$count capturas pendientes de revisión" },
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

/** "Captura automática" status: derived from the verified gate and leads to the existing permissions screen. */
@Composable
private fun CaptureAutomationCard(status: CaptureAutomationStatus, onOpenPermissions: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val (title, body, action) = when (status) {
        CaptureAutomationStatus.CHECKING -> Triple("Captura automática", "Comprobando el estado…", null)
        CaptureAutomationStatus.ACTIVE -> Triple(
            "Captura automática activa",
            "Kipu detecta movimientos en las notificaciones de tus bancos y billeteras compatibles. Nada se registra sin tu revisión, salvo gastos de alta confianza con cuenta o billetera.",
            "Administrar",
        )
        CaptureAutomationStatus.PREMIUM_REQUIRED -> Triple(
            "Captura automática en pausa",
            "Tu plan Premium no está vigente. Puedes convertir las capturas pendientes en registros manuales; no se leerán nuevas notificaciones.",
            "Ver configuración",
        )
        CaptureAutomationStatus.SETUP_REQUIRED -> Triple(
            "Captura automática desactivada",
            "Falta el permiso de notificaciones o tu consentimiento. Actívalo para que Kipu proponga movimientos desde tus notificaciones bancarias.",
            "Configurar captura",
        )
        CaptureAutomationStatus.NO_SESSION -> Triple(
            "Captura automática",
            "Inicia sesión y desbloquea Kipu para ver tus capturas.",
            null,
        )
    }
    val active = status == CaptureAutomationStatus.ACTIVE
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) palette.incomeBg else palette.surfaceCard,
        border = BorderStroke(1.dp, if (active) palette.incomeBorder else palette.borderSubtle),
        modifier = Modifier.fillMaxWidth().testTag("capture_automation_card"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (active) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (active) palette.incomeText else palette.primaryDeep,
                    modifier = Modifier.size(22.dp),
                )
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
            if (action != null) {
                OutlinedButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("capture_open_permissions"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun InboxNotice(text: String, warning: Boolean = false) {
    val palette = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (warning) palette.warningBg else palette.surfaceCard,
        border = BorderStroke(1.dp, if (warning) palette.warningBorder else palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (warning) Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = palette.warningText, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warning) palette.warningText else palette.secondaryMuted)
        }
    }
}

@Composable
private fun CaptureInboxEmptyState(status: CaptureAutomationStatus, onOpenPermissions: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 28.dp).testTag("capture_inbox_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (palette.isDark) scheme.secondaryContainer else palette.incomeBg,
            border = BorderStroke(1.dp, if (palette.isDark) scheme.outlineVariant else palette.incomeBorder),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "No tienes capturas por revisar",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = palette.primaryText,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (status == CaptureAutomationStatus.ACTIVE) {
                "Cuando llegue una notificación compatible de tu banco o billetera, el movimiento aparecerá aquí para tu revisión."
            } else {
                "Activa la captura automática para recibir propuestas de movimientos desde tus notificaciones bancarias."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = palette.secondaryMuted,
            textAlign = TextAlign.Center,
        )
        if (status == CaptureAutomationStatus.SETUP_REQUIRED) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpenPermissions,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
            ) { Text("Configurar captura", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureCandidateCard(
    candidate: CaptureCandidateRecord,
    enabled: Boolean,
    onReview: () -> Unit,
    onDiscard: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val amount = formatCaptureAmount(candidate.amountMinor, candidate.currency)
    val directionLabel = when (candidate.direction) {
        CaptureDirection.EXPENSE -> "Gasto"
        CaptureDirection.INCOME -> "Ingreso"
        null -> "Por confirmar"
    }
    val title = candidate.merchantSourceText?.takeIf(String::isNotBlank) ?: "Comercio sin identificar"
    val amountColor = when (candidate.direction) {
        CaptureDirection.EXPENSE -> palette.expenseText
        CaptureDirection.INCOME -> palette.incomeText
        null -> palette.secondaryMuted
    }
    val summary = buildString {
        append("$directionLabel de ${amount ?: "monto pendiente"} en $title, detectado en ${candidate.provider.displayName()}, ")
        append(candidate.confidence.displayName().lowercase())
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("capture_candidate_${candidate.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceCard),
        border = BorderStroke(1.dp, palette.borderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onReview)
                    .semantics { contentDescription = summary },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceChip(candidate.provider.displayName())
                    ConfidenceChip(candidate.confidence)
                    Spacer(Modifier.weight(1f))
                    Text(directionLabel, style = MaterialTheme.typography.labelMedium, color = palette.secondaryMuted)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = amount ?: "Monto pendiente de completar",
                    style = if (amount == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (amount == null) palette.secondaryMuted else amountColor,
                )
                candidate.occurredAt?.let {
                    Text(
                        text = formatCaptureDateTime(it) + if (candidate.occurredAtInferred) " · dato inferido" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryMuted,
                    )
                }
                if (candidate.confidenceReasons.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        candidate.confidenceReasons.forEach { reason -> ReasonChip(reason.label) }
                    }
                }
            }
            Button(
                onClick = onReview,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("capture_review_${candidate.id}"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Revisar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = onDiscard,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("capture_discard_${candidate.id}"),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.secondaryMuted),
            ) { Text("Descartar", style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable
internal fun SourceChip(text: String) {
    val palette = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = palette.primaryText,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Confidence is conveyed by text as well as color, so it never relies on color alone. */
@Composable
internal fun ConfidenceChip(level: CaptureConfidenceLevel) {
    val palette = rememberCalmEmeraldColors()
    val (bg, fg, border) = when (level) {
        CaptureConfidenceLevel.HIGH -> Triple(palette.incomeBg, palette.incomeText, palette.incomeBorder)
        CaptureConfidenceLevel.MEDIUM -> Triple(palette.warningBg, palette.warningText, palette.warningBorder)
        CaptureConfidenceLevel.LOW -> Triple(palette.expenseBg, palette.expenseText, palette.expenseBorder)
    }
    val label = when (level) {
        CaptureConfidenceLevel.HIGH -> "Alta"
        CaptureConfidenceLevel.MEDIUM -> "Media"
        CaptureConfidenceLevel.LOW -> "Baja"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.semantics { contentDescription = level.displayName() },
    ) {
        Text("Confianza $label", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ReasonChip(text: String) {
    val palette = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = palette.secondaryMuted)
    }
}

@Composable
internal fun CaptureDiscardDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("capture_discard_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = palette.surfaceCard,
            shadowElevation = 20.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(modifier = Modifier.size(72.dp), shape = CircleShape, color = scheme.errorContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(34.dp))
                    }
                }
                Text("¿Descartar captura?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = palette.primaryText, textAlign = TextAlign.Center)
                Text(
                    "Se quitará de tus pendientes y se borrará el texto detectado. Tus saldos y movimientos no cambian.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.secondaryMuted,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("capture_confirm_discard"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                ) { Text("Sí, descartar", fontWeight = FontWeight.SemiBold) }
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Cancelar") }
            }
        }
    }
}
