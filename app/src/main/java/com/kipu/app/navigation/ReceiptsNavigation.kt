package com.kipu.app.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.feature.receipts.presentation.ReceiptSavedContent
import com.kipu.app.feature.receipts.presentation.ReceiptShareGuide
import com.kipu.app.feature.receipts.presentation.ReceiptImportNoticeSheet
import com.kipu.app.feature.receipts.presentation.ReceiptQueuePosition
import com.kipu.app.feature.receipts.presentation.ReceiptQueueSegments
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.compose.composable
import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.presentation.QuickMovementBottomSheet
import com.kipu.app.feature.movements.presentation.QuickMovementViewModel
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit
import com.kipu.app.feature.receipts.presentation.ReceiptReviewScreen
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val RECEIPTS_ROUTE = "receipts"
private const val RECEIPT_IMPORT_ROUTE = "receipts?uri={uri}"

fun NavController.navigateToReceiptImport(uri: String) {
    // Each Android share is a new consent/import session, even if its provider reuses a URI.
    navigate("receipts?uri=${Uri.encode(uri)}")
}

fun NavGraphBuilder.receiptDestinations(navController: NavController) {
    composable(RECEIPT_IMPORT_ROUTE, arguments = listOf(navArgument("uri") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    })) { entry ->
        ReceiptFlowScreen(
            sharedUri = entry.arguments?.getString("uri"),
            onBack = { navController.popBackStack() },
            onHistory = { navController.navigate(MOVEMENTS_HISTORY_ROUTE) },
            onMovement = { navController.navigateToMovementEditor(it) },
            onNewAccount = { navController.navigateToAccountForm() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptFlowScreen(
    sharedUri: String?,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onMovement: (String) -> Unit,
    onNewAccount: () -> Unit,
    viewModel: ReceiptFlowViewModel = hiltViewModel(),
    movementViewModel: QuickMovementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var reviewedType by rememberSaveable { mutableStateOf(MovementType.EXPENSE) }
    var showShareHelp by rememberSaveable { mutableStateOf(false) }
    val palette = rememberCalmEmeraldColors()
    val candidate = state.selected
    val importUri = state.pendingImportUri ?: sharedUri
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var inboxBottomBarHeight by remember { mutableStateOf(0.dp) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val details = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                        (if (name >= 0) cursor.getString(name) else null) to
                            (if (size >= 0 && !cursor.isNull(size)) cursor.getLong(size) else null)
                    }
                }.getOrNull()
            }
            viewModel.requestImport(uri.toString(), details?.first, details?.second)
        }
    }
    val upload = { picker.launch("image/*") }
    LaunchedEffect(state.addedEventId) {
        state.addedEventId?.let { id ->
            viewModel.consumeAddedEvent()
            if (snackbar.showSnackbar("Comprobante agregado", "Ver", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) viewModel.select(id)
        }
    }
    BackHandler(enabled = candidate != null || state.savedCandidate != null) { viewModel.closeEditor() }
    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom =
            if (candidate == null && state.savedCandidate == null && state.candidates.isNotEmpty()) inboxBottomBarHeight else 0.dp)) },
        topBar = {
            TopAppBar(
                title = { Text(if (candidate != null) "Revisar comprobante" else if (state.savedCandidate != null) "Registrado" else "Comprobantes Yape",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { if (candidate != null || state.savedCandidate != null) viewModel.closeEditor() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (candidate != null) {
                        state.queue?.let { queue ->
                            ReceiptQueuePosition(queue)
                        }
                    } else if (state.candidates.isNotEmpty() || state.registered.isNotEmpty()) {
                        IconButton(onClick = { showShareHelp = true }, modifier = Modifier.size(48.dp).testTag("receipt_share_help_top_bar")) {
                            Icon(Icons.Filled.HelpOutline, "Ayuda para compartir desde Yape")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background,
                    titleContentColor = palette.primaryText, navigationIconContentColor = palette.primaryText),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { message ->
                ReceiptFeedbackBanner(message, warningTitle = state.warningTitle, onDismiss = viewModel::dismissMessage,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("receipt_feedback"))
            }
            when {
                candidate != null -> {
                    state.queue?.let { queue ->
                        ReceiptQueueSegments(queue)
                    }
                    ReceiptMetadataEditor(candidate, state.busy, onContinue = { edit, type -> reviewedType = type; viewModel.edit(edit) },
                        modifier = Modifier.weight(1f), showNotice = state.message == null,
                        onDiscard = { viewModel.discard(candidate.id); viewModel.closeEditor() })
                }
                state.savedCandidate != null -> {
                    val saved = requireNotNull(state.savedCandidate)
                    ReceiptSavedContent(saved, pendingCount = com.kipu.app.feature.receipts.domain.pendingReceipts(state.candidates).size,
                        onNext = viewModel::reviewNext, onMovement = state.savedMovementId?.let { id -> { onMovement(id) } }, onInbox = viewModel::closeEditor)
                }
                importUri != null && !state.importConsumed -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Recibimos una imagen compartida", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        ReceiptInfoNotice("Autoriza a Kipu a leer este comprobante Yape en tu dispositivo. El OCR local está disponible en Kipu Free. La imagen no se subirá ni se conservará.", prominent = true)
                        Text("La imagen no acredita por sí sola un pago. Revisarás los datos antes de registrar; tu saldo no cambiará durante la lectura.", color = palette.secondaryMuted)
                        Button(onClick = { viewModel.import(importUri) }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_authorize_ocr")) { Text("Autorizar OCR local de Yape") }
                        OutlinedButton(onClick = viewModel::cancelImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Cancelar") }
                    }
                }
                else -> ReceiptReviewScreen(candidates = state.candidates, onReview = viewModel::select,
                    onEdit = viewModel::editCandidate, onDiscard = viewModel::discard,
                    onHowToShare = { showShareHelp = true }, isBusy = state.busy,
                    modifier = Modifier.weight(1f), registered = state.registered, onUpload = upload,
                    onStartReview = viewModel::startReview, onHistory = onHistory, onMovement = onMovement,
                    newCandidateId = state.newCandidateId, onBottomBarHeightChanged = { inboxBottomBarHeight = it })
            }
        }
    }
    if (showShareHelp) Dialog(onDismissRequest = { showShareHelp = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ReceiptShareGuide(onDismiss = { showShareHelp = false })
    }
    state.notice?.let { notice ->
        ReceiptImportNoticeSheet(notice = notice, onDismiss = viewModel::dismissNotice,
            onPickAnother = { viewModel.dismissNotice(); upload() }, onReview = viewModel::reviewDuplicate,
            onMovement = { val id = state.duplicateMovementId; viewModel.dismissNotice(); if (id != null) onMovement(id) else onHistory() },
            onRestore = viewModel::restoreDuplicate, candidate = state.duplicateCandidate,
            fileName = state.fileName, fileSize = state.fileSize, busy = state.busy)
    }
    if (state.readyForMovement && candidate != null) {
        LaunchedEffect(candidate.id) {
            movementViewModel.prepareReceiptDraft(candidateId = candidate.id, amountMinor = candidate.amountMinor,
                occurredAt = candidate.occurredAt, recipient = candidate.recipient, movementType = reviewedType,
                onConfirm = { command -> viewModel.confirm(candidate.id, command) })
        }
        QuickMovementBottomSheet(viewModel = movementViewModel,
            onDismissRequest = { movementViewModel.resetForm(); viewModel.closeMovement() },
            onSaved = { viewModel.movementSaved() }, onMessage = viewModel::message, onNavigateToNewAccount = onNewAccount)
    }
}

@Composable
internal fun ReceiptFeedbackBanner(
    message: String,
    modifier: Modifier = Modifier,
    warningTitle: String? = null,
    onDismiss: () -> Unit = {},
) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    val isSuccess = message.startsWith("Lectura local terminada.")
    val isError = message.contains("no se pudo", ignoreCase = true) || message.contains("desbloquea", ignoreCase = true)
    val background = when {
        warningTitle != null -> palette.warningBg
        isSuccess -> palette.incomeBg
        isError -> scheme.errorContainer
        else -> scheme.secondaryContainer
    }
    val foreground = when {
        warningTitle != null -> palette.warningText
        isSuccess -> palette.incomeText
        isError -> scheme.onErrorContainer
        else -> scheme.onSecondaryContainer
    }
    val title = warningTitle ?: if (isSuccess) "Lectura local terminada" else null
    val body = if (isSuccess) message.substringAfter(". ", message) else message

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, if (warningTitle != null) palette.warningBorder else if (isSuccess) palette.incomeBorder else scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = background) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (warningTitle != null) Icons.Filled.WarningAmber else if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        contentDescription = null,
                        tint = foreground,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar aviso")
            }
        }
    }
}

@Composable
private fun ReceiptInfoNotice(
    text: String,
    prominent: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    val background = if (palette.isDark) scheme.secondaryContainer else palette.incomeBg
    val foreground = if (palette.isDark) scheme.onSecondaryContainer else palette.primaryText
    val border = if (palette.isDark) scheme.outlineVariant else palette.incomeBorder

    if (prominent) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = background,
            contentColor = foreground,
            border = BorderStroke(1.dp, border),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = palette.primaryDeep,
                    modifier = Modifier.padding(top = 2.dp).size(22.dp),
                )
                Text(text, style = MaterialTheme.typography.bodyLarge, color = foreground)
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = palette.primaryDeep,
                modifier = Modifier.padding(top = 2.dp).size(19.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
        }
    }
}

@Composable
internal fun ReceiptMetadataEditor(
    candidate: ReceiptCandidate,
    busy: Boolean,
    onContinue: (ReceiptCandidateEdit, MovementType) -> Unit,
    modifier: Modifier = Modifier,
    showNotice: Boolean = true,
    onDiscard: (() -> Unit)? = null,
) {
    val lima = remember { ZoneId.of("America/Lima") }
    val context = LocalContext.current
    val timestamp = candidate.occurredAt?.let { java.time.Instant.ofEpochMilli(it).atZone(lima) }
    var amount by rememberSaveable(candidate.id) { mutableStateOf(candidate.amountMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()) }
    var date by rememberSaveable(candidate.id) { mutableStateOf(timestamp?.toLocalDate()?.toString().orEmpty()) }
    var time by rememberSaveable(candidate.id) { mutableStateOf(timestamp?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty()) }
    var recipient by rememberSaveable(candidate.id) { mutableStateOf(candidate.recipient.orEmpty()) }
    var reference by rememberSaveable(candidate.id) { mutableStateOf(candidate.reference.orEmpty()) }
    var type by rememberSaveable(candidate.id) { mutableStateOf(when (candidate.isIncome) {
        true -> MovementType.INCOME
        false -> MovementType.EXPENSE
        null -> null
    }) }
    var error by rememberSaveable(candidate.id) { mutableStateOf<String?>(null) }
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showNotice) ReceiptInfoNotice(
                text = "Revisa los datos antes de confirmar. Tu saldo aún no cambia.",
                prominent = true,
            )

            onDiscard?.let { discard ->
                TextButton(onClick = discard, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Descartar comprobante", color = scheme.error)
                }
            }
            ReceiptDirectionSelector(
                selectedType = type,
                onSelect = { type = it },
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceCard),
                border = BorderStroke(1.dp, palette.borderSubtle),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Datos del comprobante",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.primaryText,
                            )
                            Text(
                                "Verifica o edita la información reconocida.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.secondaryMuted,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (palette.isDark) scheme.secondaryContainer else palette.incomeBg,
                            contentColor = palette.primaryDeep,
                            border = BorderStroke(1.dp, if (palette.isDark) scheme.outlineVariant else palette.incomeBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("YAPE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    ReceiptEditorField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = "Monto",
                        icon = null,
                        prefix = "S/",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth().testTag("receipt_amount"),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReceiptEditorField(
                            value = date,
                            onValueChange = { date = it },
                            label = "Fecha",
                            icon = Icons.Filled.CalendarToday,
                            onPick = {
                                val initial = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now(lima))
                                android.app.DatePickerDialog(context, { _, year, month, day ->
                                    date = LocalDate.of(year, month + 1, day).toString()
                                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                            },
                            displayValue = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(date),
                            modifier = Modifier.weight(1f).testTag("receipt_date"),
                        )
                        ReceiptEditorField(
                            value = time,
                            onValueChange = { time = it },
                            label = "Hora",
                            icon = Icons.Filled.AccessTime,
                            onPick = {
                                val initial = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.now(lima))
                                android.app.TimePickerDialog(context, { _, hour, minute ->
                                    time = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm"))
                                }, initial.hour, initial.minute, true).show()
                            },
                            modifier = Modifier.weight(1f).testTag("receipt_time"),
                        )
                    }

                    ReceiptEditorField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = if (type == MovementType.INCOME) "Remitente" else "Destinatario",
                        icon = Icons.Filled.PersonOutline,
                        modifier = Modifier.fillMaxWidth().testTag("receipt_counterpart"),
                    )

                    ReceiptEditorField(
                        value = reference,
                        onValueChange = { reference = it },
                        label = "N.º de operación",
                        icon = Icons.Filled.Tag,
                        modifier = Modifier.fillMaxWidth().testTag("receipt_reference"),
                    )

                    Text(
                        "La referencia ayuda a detectar duplicados. Operaciones distintas con el mismo monto pueden ser válidas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryMuted,
                    )

                    error?.let { message ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = scheme.errorContainer,
                            contentColor = scheme.onErrorContainer,
                        ) {
                            Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().imePadding(),
            color = palette.surfaceCard,
            contentColor = palette.primaryText,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, palette.borderSubtle),
        ) {
            Button(
                onClick = {
                    val minor = MoneyInputParser.parseMinorUnits(amount)
                    val occurredAt = runCatching {
                        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                            .atZone(lima).toInstant().toEpochMilli()
                    }.getOrNull()
                    if (minor == null || minor <= 0 || occurredAt == null || type == null) {
                        error = "Elige si enviaste o recibiste e ingresa un monto positivo, fecha y hora válidos."
                    } else {
                        error = null
                        onContinue(
                            ReceiptCandidateEdit(
                                minor,
                                occurredAt,
                                recipient.takeIf(String::isNotBlank),
                                reference.takeIf(String::isNotBlank),
                                type == MovementType.INCOME,
                            ),
                            requireNotNull(type),
                        )
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 56.dp).testTag("receipt_review_continue"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryDeep,
                    contentColor = palette.onPrimaryDeep,
                ),
            ) {
                Text("Continuar y elegir cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ReceiptDirectionSelector(
    selectedType: MovementType?,
    onSelect: (MovementType) -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (palette.isDark) scheme.surfaceContainer else Color(0xFFEFF3F7), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ReceiptDirectionOption(
            label = "Envié · Gasto",
            icon = Icons.Filled.NorthEast,
            selected = selectedType == MovementType.EXPENSE,
            income = false,
            onClick = { onSelect(MovementType.EXPENSE) },
            modifier = Modifier.weight(1f),
        )
        ReceiptDirectionOption(
            label = "Recibí · Ingreso",
            icon = Icons.Filled.ArrowDownward,
            selected = selectedType == MovementType.INCOME,
            income = true,
            onClick = { onSelect(MovementType.INCOME) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReceiptDirectionOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    income: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    val color = if (!selected) palette.secondaryMuted else if (income) palette.incomeText else palette.expenseText
    val background = if (!selected) Color.Transparent else if (income) palette.incomeBg else palette.expenseBg
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .background(background, RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .semantics { stateDescription = if (selected) "Seleccionado" else "No seleccionado" }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun ReceiptEditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onPick: (() -> Unit)? = null,
    displayValue: String = value,
) {
    val palette = rememberCalmEmeraldColors()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
        Box {
            OutlinedTextField(
                value = displayValue,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                readOnly = onPick != null,
                placeholder = { Text(when (label) { "Fecha" -> "DD/MM/AAAA"; "Hora" -> "HH:mm"; "Monto" -> "0.00"; "N.º de operación" -> "Ej. 05862147"; else -> "Nombre de la contraparte" }, maxLines = 1) },
                leadingIcon = icon?.let { vector ->
                    { Icon(vector, contentDescription = null, tint = palette.secondaryMuted) }
                },
                prefix = prefix?.let { currency -> { Text("$currency ", fontWeight = FontWeight.SemiBold, color = palette.secondaryMuted) } },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            if (onPick != null) Box(Modifier.matchParentSize().clickable(role = Role.Button, onClickLabel = "Elegir $label", onClick = onPick))
        }
    }
}
