package com.kipu.app.feature.capture.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.capture.domain.CaptureCandidateRecord
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.feature.capture.domain.CaptureInstrumentKind
import com.kipu.app.feature.capture.domain.CaptureReason
import com.kipu.app.ui.component.KipuChipTone
import com.kipu.app.ui.component.KipuStatusChip
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

private enum class InboxFilter(val label: String) { ALL("Todas"), MISSING("Faltan datos"), READY("Solo confirmar") }

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
    onOpenHowItWorks: () -> Unit = {},
) {
    val palette = rememberCalmEmeraldColors()
    val snackbar = remember { SnackbarHostState() }
    var toDiscard by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(InboxFilter.ALL) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }

    val missingIds = remember(state.candidates) { state.candidates.filter { it.missingFields().isNotEmpty() }.map { it.id }.toSet() }
    val visible = when (filter) {
        InboxFilter.ALL -> state.candidates
        InboxFilter.MISSING -> state.candidates.filter { it.id in missingIds }
        InboxFilter.READY -> state.candidates.filterNot { it.id in missingIds }
    }
    val counts = mapOf(
        InboxFilter.ALL to state.candidates.size,
        InboxFilter.MISSING to missingIds.size,
        InboxFilter.READY to state.candidates.size - missingIds.size,
    )
    val paused = state.automation == CaptureAutomationStatus.PREMIUM_REQUIRED || state.automation == CaptureAutomationStatus.SETUP_REQUIRED

    Scaffold(
        modifier = modifier,
        containerColor = palette.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(data, shape = RoundedCornerShape(14.dp), containerColor = palette.primaryText, contentColor = palette.surfaceCard)
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Capturas por revisar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPermissions, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configurar captura automática", tint = palette.primaryDeep)
                    }
                    IconButton(onClick = onOpenHowItWorks, modifier = Modifier.size(48.dp).testTag("capture_how_it_works_help")) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "Cómo funciona la captura automática", tint = palette.primaryDeep)
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
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.primaryDeep)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("capture_inbox_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "automation") {
                        CaptureAutomationCard(state.automation, onOpenPermissions, Modifier.animateItem())
                    }
                    if (state.pendingAssociations.isNotEmpty()) {
                        item(key = "associations_header") {
                            Column(Modifier.animateItem().padding(top = 6.dp)) {
                                Text("Pagos por asociar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.primaryText)
                                Text("Ya están registrados; tu saldo no cambia.", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
                            }
                        }
                        items(state.pendingAssociations, key = { "association_${it.id}" }) { candidate ->
                            AssociationCard(candidate, onClick = { onOpenCandidate(candidate.id) }, modifier = Modifier.animateItem())
                        }
                    }
                    if (state.candidates.isNotEmpty()) {
                        item(key = "header") {
                            CaptureInboxHeader(
                                count = state.candidates.size,
                                subtitle = if (paused) "Se conservan. Conviértelas a manual para registrarlas." else "Tu saldo no cambia hasta que confirmes.",
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "filters") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.animateItem()) {
                                items(InboxFilter.entries.toList(), key = { it.name }) { option ->
                                    FilterPill(option.label, counts.getValue(option), option == filter) { filter = option }
                                }
                            }
                        }
                    }
                    if (state.loadFailed) {
                        item(key = "error") {
                            CaptureNotice(
                                tone = CaptureNoticeTone.WARNING,
                                title = "No se pudieron cargar",
                                body = "Vuelve a abrir esta pantalla.",
                            )
                        }
                    }
                    when {
                        state.candidates.isEmpty() && !state.loadFailed -> item(key = "empty") {
                            CaptureInboxEmptyState(state.automation, onOpenHowItWorks, Modifier.animateItem())
                        }
                        visible.isEmpty() && state.candidates.isNotEmpty() -> item(key = "empty_filter") {
                            FilterEmptyState(filter, Modifier.animateItem()) { filter = InboxFilter.MISSING.takeIf { filter == InboxFilter.READY } ?: InboxFilter.ALL }
                        }
                    }
                    visible.groupBy { captureRelativeDay(it.occurredAt ?: it.createdAt) }.forEach { (day, group) ->
                        item(key = "day_$day") {
                            Text(
                                day.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.secondaryMuted,
                                modifier = Modifier.animateItem().padding(start = 4.dp, top = 4.dp),
                            )
                        }
                        items(group, key = CaptureCandidateRecord::id) { candidate ->
                            CaptureCandidateCard(
                                candidate = candidate,
                                paused = paused,
                                enabled = !state.isBusy,
                                onReview = { onOpenCandidate(candidate.id) },
                                onDiscard = { toDiscard = candidate.id },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    toDiscard?.let { id ->
        val candidate = state.candidates.firstOrNull { it.id == id }
        CaptureDiscardDialog(
            onDismiss = { toDiscard = null },
            onConfirm = {
                toDiscard = null
                onDiscard(id)
            },
            merchant = candidate?.merchantSourceText?.takeIf(String::isNotBlank),
            amount = candidate?.let { formatCaptureAmount(it.amountMinor, it.currency) },
        )
    }
}

@Composable
private fun CaptureInboxHeader(count: Int, subtitle: String, modifier: Modifier = Modifier) {
    val palette = rememberCalmEmeraldColors()
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Por revisar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(palette.incomeBg, CircleShape)
                .semantics { contentDescription = "$count capturas pendientes de revisión" },
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.primaryDeep)
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) palette.primaryDeep else palette.surfaceCard,
        border = if (selected) null else BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.heightIn(min = 44.dp).semantics { this.selected = selected },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (selected) palette.onPrimaryDeep else palette.primaryText)
            Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (selected) palette.onPrimaryDeep.copy(alpha = 0.75f) else palette.secondaryMuted)
        }
    }
}

/** "Captura automática" status: derived from the verified gate and leads to the existing permissions screen. */
@Composable
private fun CaptureAutomationCard(status: CaptureAutomationStatus, onOpenPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val palette = rememberCalmEmeraldColors()
    when (status) {
        CaptureAutomationStatus.PREMIUM_REQUIRED -> CaptureNotice(
            tone = CaptureNoticeTone.WARNING,
            title = "Captura automática en pausa",
            body = "Tu plan Premium no está vigente. No se leerán nuevas notificaciones.",
            actionLabel = "Ver configuración",
            onAction = onOpenPermissions,
            modifier = modifier.testTag("capture_automation_card"),
        )
        CaptureAutomationStatus.SETUP_REQUIRED -> CaptureNotice(
            tone = CaptureNoticeTone.WARNING,
            title = "Captura automática desactivada",
            body = "Falta el permiso de notificaciones o tu consentimiento.",
            actionLabel = "Configurar captura",
            onAction = onOpenPermissions,
            modifier = modifier.testTag("capture_automation_card"),
        )
        else -> {
            val active = status == CaptureAutomationStatus.ACTIVE
            val (title, body) = when (status) {
                CaptureAutomationStatus.ACTIVE -> "Captura automática activa" to "Los gastos de alta confianza se registran solos. El resto espera aquí."
                CaptureAutomationStatus.NO_SESSION -> "Captura automática" to "Desbloquea Kipu para ver tus capturas."
                else -> "Captura automática" to "Comprobando el estado…"
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = palette.surfaceCard,
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(enabled = active, role = Role.Button, onClick = onOpenPermissions)
                    .testTag("capture_automation_card"),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusDot(active)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.primaryText)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
                    }
                    if (active) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = palette.secondaryMuted)
                }
            }
        }
    }
}

/** Soft pulsing dot: "listening" without being noisy. */
@Composable
private fun StatusDot(active: Boolean) {
    val palette = rememberCalmEmeraldColors()
    val pulse = rememberInfiniteTransition(label = "statusDot")
    val scale by pulse.animateFloat(1f, if (active) 1.35f else 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "statusDotScale")
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(18.dp).scale(scale).background((if (active) palette.incomeEmerald else palette.secondaryMuted).copy(alpha = 0.18f), CircleShape))
        Box(Modifier.size(10.dp).background(if (active) palette.incomeEmerald else palette.secondaryMuted, CircleShape))
    }
}

@Composable
private fun AssociationCard(candidate: CaptureCandidateRecord, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = rememberCalmEmeraldColors()
    val amount = formatCaptureAmount(candidate.amountMinor, candidate.currency)
    val sign = when (candidate.direction) {
        CaptureDirection.EXPENSE -> "−"
        CaptureDirection.INCOME -> "+"
        null -> ""
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.incomeBg,
        border = BorderStroke(1.dp, palette.incomeBorder),
        modifier = modifier.fillMaxWidth().testTag("capture_associate_${candidate.id}"),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(palette.surfaceCard, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SyncAlt, contentDescription = null, tint = palette.primaryDeep)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(candidate.merchantSourceText ?: candidate.provider.displayName(), amount?.let { "$sign$it" }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Elige a qué pago esperado corresponde.", style = MaterialTheme.typography.bodySmall, color = palette.incomeText)
            }
            Button(
                onClick = onClick,
                modifier = Modifier.heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
            ) { Text("Elegir", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CaptureInboxEmptyState(status: CaptureAutomationStatus, onOpenHowItWorks: () -> Unit, modifier: Modifier = Modifier) {
    EmptyBlock(
        icon = Icons.Outlined.NotificationsActive,
        title = "Todo al día",
        body = if (status == CaptureAutomationStatus.ACTIVE) {
            "Cuando llegue una notificación compatible de tu banco o billetera, aparecerá aquí."
        } else {
            "Activa la captura automática para recibir propuestas desde tus notificaciones."
        },
        action = "¿Cómo funciona?",
        onAction = onOpenHowItWorks,
        actionIcon = Icons.Outlined.HelpOutline,
        actionTag = "capture_empty_how_it_works",
        modifier = modifier.testTag("capture_inbox_empty"),
    )
}

@Composable
private fun FilterEmptyState(filter: InboxFilter, modifier: Modifier = Modifier, onSwitch: () -> Unit) {
    val (title, body, action) = when (filter) {
        InboxFilter.READY -> Triple("Nada listo para confirmar", "Todas necesitan algún dato más.", "Ver las que faltan datos")
        InboxFilter.MISSING -> Triple("No falta ningún dato", "Todas están listas para confirmar.", "Ver todas")
        InboxFilter.ALL -> Triple("Sin capturas", "", "Ver todas")
    }
    EmptyBlock(Icons.Outlined.FilterList, title, body, action, onSwitch, modifier)
}

@Composable
private fun EmptyBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionTag: String? = null,
) {
    val palette = rememberCalmEmeraldColors()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(palette.pillTrack, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = palette.secondaryMuted, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = palette.primaryText)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = palette.secondaryMuted, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.incomeBorder),
            ) {
                if (actionIcon != null) {
                    Icon(actionIcon, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(action, fontWeight = FontWeight.Bold, color = palette.primaryDeep)
            }
        }
    }
}

/** The one chip that says why this capture is special, besides confidence. */
@Composable
private fun KindChip(candidate: CaptureCandidateRecord) {
    val reasons = candidate.confidenceReasons
    when {
        CaptureReason.POSSIBLE_DUPLICATE in reasons -> KipuStatusChip("Posible duplicado", KipuChipTone.WARNING)
        candidate.instrumentAmbiguous -> KipuStatusChip(
            listOfNotNull("Elegir cuenta", candidate.cardLast4?.let { "····$it" }).joinToString(" · "),
            KipuChipTone.WARNING,
        )
        candidate.instrumentKind == CaptureInstrumentKind.CREDIT_CARD -> KipuStatusChip("Tarjeta de crédito", KipuChipTone.NEUTRAL)
        candidate.direction == CaptureDirection.INCOME -> KipuStatusChip("Ingreso", KipuChipTone.POSITIVE)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureCandidateCard(
    candidate: CaptureCandidateRecord,
    paused: Boolean,
    enabled: Boolean,
    onReview: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCalmEmeraldColors()
    val amount = formatCaptureAmount(candidate.amountMinor, candidate.currency)
    val missing = candidate.missingFields()
    val title = candidate.merchantSourceText?.takeIf(String::isNotBlank) ?: "Comercio sin identificar"
    val (sign, amountColor) = when (candidate.direction) {
        CaptureDirection.EXPENSE -> "−" to palette.expenseText
        CaptureDirection.INCOME -> "+" to palette.incomeText
        null -> "" to palette.primaryText
    }
    val directionLabel = when (candidate.direction) {
        CaptureDirection.EXPENSE -> "Gasto"
        CaptureDirection.INCOME -> "Ingreso"
        null -> "Movimiento"
    }
    val whenLabel = captureRelativeDateTime(candidate.occurredAt ?: candidate.createdAt)
    val summary = "$directionLabel de ${amount ?: "monto pendiente"} en $title, ${candidate.provider.displayName()}, $whenLabel, " +
        candidate.confidence.displayName().lowercase() + if (missing.isNotEmpty()) ", faltan ${missing.size} datos" else ""
    val primaryReason = candidate.confidenceReasons.firstOrNull()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = palette.surfaceCard,
        border = BorderStroke(if (missing.isNotEmpty()) 1.5.dp else 1.dp, if (missing.isNotEmpty()) palette.warningBorder else palette.borderSubtle),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth().animateContentSize().testTag("capture_candidate_${candidate.id}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReview)
                    .semantics(mergeDescendants = true) { contentDescription = summary },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CaptureProviderLogo(candidate.provider, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${candidate.provider.displayName()} · $whenLabel", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
                    }
                    Text(
                        text = amount?.let { "$sign$it" } ?: "Sin monto",
                        style = if (amount == null) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (amount == null) palette.secondaryMuted else amountColor,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConfidenceChip(candidate.confidence)
                    if (missing.isNotEmpty()) {
                        KipuStatusChip(if (missing.size == 1) "Falta 1 dato" else "Faltan ${missing.size} datos", KipuChipTone.WARNING)
                    } else {
                        KindChip(candidate)
                    }
                    if (candidate.occurredAtInferred) KipuStatusChip("Fecha inferida", KipuChipTone.NEUTRAL)
                    if (paused) KipuStatusChip("En pausa", KipuChipTone.NEUTRAL)
                }
                if (primaryReason != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = palette.secondaryMuted, modifier = Modifier.size(18.dp))
                        Text(primaryReason.label, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted, modifier = Modifier.weight(1f, fill = false))
                        val more = candidate.confidenceReasons.size - 1
                        if (more > 0) KipuStatusChip("+$more", KipuChipTone.NEUTRAL)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = enabled,
                    modifier = Modifier.size(width = 56.dp, height = 52.dp).testTag("capture_discard_${candidate.id}"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Descartar captura", tint = palette.secondaryMuted)
                }
                Button(
                    onClick = onReview,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("capture_review_${candidate.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                ) {
                    Icon(if (missing.isNotEmpty()) Icons.Outlined.Edit else Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (missing.isNotEmpty()) "Completar" else "Revisar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
