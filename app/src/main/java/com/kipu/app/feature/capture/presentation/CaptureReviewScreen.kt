package com.kipu.app.feature.capture.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.South
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.feature.capture.domain.CaptureInstrumentKind
import com.kipu.app.feature.capture.domain.CaptureReason
import com.kipu.app.feature.capture.domain.CreditMode
import com.kipu.app.feature.categories.presentation.components.MerchantPickerBottomSheet
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import com.kipu.app.feature.categories.presentation.components.MerchantPickerViewModel
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.movements.presentation.DuplicateWarningDialog
import com.kipu.app.feature.movements.presentation.PastelCategoryPickerBottomSheet
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuAmountCard
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.KipuChipTone
import com.kipu.app.ui.component.KipuFieldGroup
import com.kipu.app.ui.component.KipuFieldRow
import com.kipu.app.ui.component.KipuFieldRowDivider
import com.kipu.app.ui.component.KipuIconTile
import com.kipu.app.ui.component.KipuSegmentedControl
import com.kipu.app.ui.component.KipuStatusChip
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.Instant
import java.time.ZoneOffset

private enum class CapturePicker { CATEGORY, INSTRUMENT, MERCHANT, EVIDENCE, CONFIDENCE }

/** One of the five data a capture needs, as the progress bar shows it. */
private enum class FieldStatus { READY, VERIFY, MISSING }

/**
 * Review and edit of one captured movement (HU-51, HU-48). Same anatomy as "Editar movimiento"
 * (amount card + grouped rows), plus where the data came from and how sure Kipu is. Leaving without
 * confirming changes nothing: every edit lives in the ViewModel until [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureReviewScreen(
    state: CaptureReviewUiState,
    onBack: () -> Unit,
    onDirection: (CaptureDirection) -> Unit,
    onAmount: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onDate: (Long) -> Unit,
    onMerchant: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onInstrument: (CaptureInstrumentChoice) -> Unit,
    onCreditMode: (CreditMode) -> Unit,
    onInstallments: (String) -> Unit,
    onConfirm: () -> Unit,
    onConfirmDespiteSimilar: () -> Unit,
    onDismissSimilar: () -> Unit,
    onConvertToManual: () -> Unit,
    onDiscard: () -> Unit,
    onOpenPermissions: () -> Unit,
    onChooseExpected: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val merchantPicker: MerchantPickerViewModel = hiltViewModel()
    val merchantPickerState by merchantPicker.uiState.collectAsStateWithLifecycle()
    CaptureReviewContent(
        state = state,
        onBack = onBack,
        onDirection = onDirection,
        onAmount = onAmount,
        onCurrency = onCurrency,
        onDate = onDate,
        onMerchant = onMerchant,
        onCategory = onCategory,
        onInstrument = onInstrument,
        onCreditMode = onCreditMode,
        onInstallments = onInstallments,
        onConfirm = onConfirm,
        onConfirmDespiteSimilar = onConfirmDespiteSimilar,
        onDismissSimilar = onDismissSimilar,
        onConvertToManual = onConvertToManual,
        onDiscard = onDiscard,
        onOpenPermissions = onOpenPermissions,
        onChooseExpected = onChooseExpected,
        modifier = modifier,
        merchantPickerState = merchantPickerState,
        onMerchantQueryChanged = merchantPicker::onQueryChanged,
    )
}

/** Stateless review content for previews and device screenshot evidence. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptureReviewContent(
    state: CaptureReviewUiState,
    onBack: () -> Unit,
    onDirection: (CaptureDirection) -> Unit,
    onAmount: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onDate: (Long) -> Unit,
    onMerchant: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onInstrument: (CaptureInstrumentChoice) -> Unit,
    onCreditMode: (CreditMode) -> Unit,
    onInstallments: (String) -> Unit,
    onConfirm: () -> Unit,
    onConfirmDespiteSimilar: () -> Unit,
    onDismissSimilar: () -> Unit,
    onConvertToManual: () -> Unit,
    onDiscard: () -> Unit,
    onOpenPermissions: () -> Unit,
    onChooseExpected: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
    merchantPickerState: MerchantPickerState,
    onMerchantQueryChanged: (String) -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val merchantLabel = merchantPickerState.catalogEntries.firstOrNull { it.id.value == state.merchantId }?.name
    var picker by rememberSaveable { mutableStateOf<CapturePicker?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val reviewing = state.candidate != null && !state.notFound && state.resolvedForChoice == null && !state.isLoading

    Scaffold(
        modifier = modifier,
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.resolvedForChoice != null) "Asociar pago" else "Revisar captura",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver sin cambios")
                    }
                },
                actions = {
                    if (reviewing) {
                        IconButton(
                            onClick = { showDiscard = true },
                            enabled = !state.isSaving,
                            modifier = Modifier.size(48.dp).testTag("capture_discard"),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Descartar captura", tint = palette.secondaryMuted)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surfaceCard,
                    titleContentColor = palette.primaryText,
                    navigationIconContentColor = palette.primaryText,
                ),
            )
        },
        bottomBar = {
            if (reviewing) ReviewFooter(state, onConfirm, onConvertToManual)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.primaryDeep)
                state.resolvedForChoice != null -> ExpectedPaymentChoice(state, onChooseExpected, onBack)
                state.notFound || state.candidate == null -> CaptureUnavailable(onBack)
                else -> ReviewForm(
                    state = state,
                    merchantLabel = merchantLabel,
                    onDirection = onDirection,
                    onAmount = onAmount,
                    onCurrency = onCurrency,
                    onPickMerchant = { picker = CapturePicker.MERCHANT },
                    onPickCategory = { picker = CapturePicker.CATEGORY },
                    onPickInstrument = { picker = CapturePicker.INSTRUMENT },
                    onInstrument = onInstrument,
                    onPickDate = { showDatePicker = true },
                    onShowEvidence = { picker = CapturePicker.EVIDENCE },
                    onShowConfidence = { picker = CapturePicker.CONFIDENCE },
                    onCreditMode = onCreditMode,
                    onInstallments = onInstallments,
                    onRetry = onConfirm,
                    onDiscard = { showDiscard = true },
                    onOpenPermissions = onOpenPermissions,
                )
            }
        }
    }

    when (picker) {
        CapturePicker.MERCHANT -> MerchantPickerBottomSheet(
            state = merchantPickerState.copy(selectedMerchant = merchantPickerState.catalogEntries.firstOrNull { it.id.value == state.merchantId }),
            onQueryChange = onMerchantQueryChanged,
            onSelectMerchant = { onMerchant(it.id.value); onMerchantQueryChanged(""); picker = null },
            onSetProvisionalText = {},
            onClearSelection = { onMerchant(""); onMerchantQueryChanged(""); picker = null },
            onDismiss = { onMerchantQueryChanged(""); picker = null },
            title = "Comercio",
            subtitle = "Elige un comercio del catálogo o déjalo vacío",
            allowProvisionalText = false,
            selectedMerchantIds = setOfNotNull(state.merchantId),
        )
        // Same picker as "Editar movimiento", so categories look and behave identically.
        CapturePicker.CATEGORY -> PastelCategoryPickerBottomSheet(
            categories = state.categories.map { it.toOption() },
            selectedCategoryId = state.categoryId,
            onSelect = { onCategory(it.id); picker = null },
            onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
            onDismiss = { picker = null },
            directSelectionMode = true,
            allowCreate = false,
            subtitle = if (state.direction == CaptureDirection.INCOME) "Selecciona una categoría para tu ingreso" else "Selecciona una categoría para tu gasto",
            modifier = Modifier.testTag("capture_category_sheet"),
        )
        CapturePicker.INSTRUMENT -> InstrumentPickerSheet(
            instruments = state.instruments.filter { state.direction == CaptureDirection.EXPENSE || it.kind != CaptureInstrumentKind.CREDIT_CARD },
            selected = state.selectedInstrument,
            onSelect = { onInstrument(it); picker = null },
            onDismiss = { picker = null },
        )
        CapturePicker.EVIDENCE -> state.candidate?.let { candidate ->
            EvidenceSheet(candidate.provider.displayName(), candidate.evidenceSnippet ?: candidate.merchantSourceText) { picker = null }
        }
        CapturePicker.CONFIDENCE -> state.candidate?.let { candidate ->
            ConfidenceSheet(candidate.confidence, state.reasons) { picker = null }
        }
        null -> Unit
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnightOf(state.occurredAt))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            val day = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                            onDate(withCaptureDay(state.occurredAt, day))
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
        ) { DatePicker(state = pickerState) }
    }

    if (showDiscard) {
        CaptureDiscardDialog(
            onDismiss = { showDiscard = false },
            onConfirm = {
                showDiscard = false
                onDiscard()
            },
            merchant = state.candidate?.merchantSourceText?.takeIf(String::isNotBlank),
            amount = parseCaptureAmountMinor(state.amountText)?.let { formatCaptureAmount(it, state.currency) },
        )
    }

    state.similarTransaction?.let { existing ->
        DuplicateWarningDialog(
            onConfirm = onConfirmDespiteSimilar,
            onDismiss = onDismissSimilar,
            existing = existing,
            accountAlias = { id -> state.instruments.firstOrNull { it.id == id }?.label },
        )
    }
}

private fun CaptureCategoryChoice.toOption() = CategoryOption(
    id = id,
    name = name,
    icon = icon,
    categoryType = type,
    parentCategoryId = parentId,
    parentName = parentName,
    color = color,
)

private fun utcMidnightOf(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(CAPTURE_ZONE).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Live status of the five data, from what the user has now (not what the notification said). */
private fun CaptureReviewUiState.fieldStatuses(): List<Pair<String, FieldStatus>> {
    val dateInferred = candidate?.occurredAtInferred == true && !dateEdited
    return listOf(
        "tipo" to FieldStatus.READY,
        "monto" to if (parseCaptureAmountMinor(amountText) != null && currency != null) FieldStatus.READY else FieldStatus.MISSING,
        "fecha" to if (dateInferred) FieldStatus.VERIFY else FieldStatus.READY,
        "categoría" to if (direction == CaptureDirection.INCOME || categoryId != null) FieldStatus.READY else FieldStatus.MISSING,
        "cuenta" to if (selectedInstrument != null) FieldStatus.READY else FieldStatus.MISSING,
    )
}

/** "No se pudo guardar. Puedes reintentar…" → title + body, so the banner does not repeat itself. */
private fun splitErrorMessage(message: String): Pair<String?, String> {
    val cut = message.indexOf(". ")
    return if (message.startsWith("No se pudo") && cut > 0) {
        message.substring(0, cut) to message.substring(cut + 2) + if (message.startsWith("No se pudo guardar")) " Tu saldo no cambió." else ""
    } else {
        null to message
    }
}

private fun List<String>.joinNatural(): String =
    if (size <= 1) joinToString() else dropLast(1).joinToString(", ") + " y " + last()

@Composable
private fun ReviewForm(
    state: CaptureReviewUiState,
    merchantLabel: String?,
    onDirection: (CaptureDirection) -> Unit,
    onAmount: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onPickMerchant: () -> Unit,
    onPickCategory: () -> Unit,
    onPickInstrument: () -> Unit,
    onInstrument: (CaptureInstrumentChoice) -> Unit,
    onPickDate: () -> Unit,
    onShowEvidence: () -> Unit,
    onShowConfidence: () -> Unit,
    onCreditMode: (CreditMode) -> Unit,
    onInstallments: (String) -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val candidate = requireNotNull(state.candidate)
    val editable = state.canConfirm && !state.isSaving
    val formAlpha by animateFloatAsState(if (state.canConvertToManual) 0.55f else 1f, label = "formAlpha")
    val isDuplicate = CaptureReason.POSSIBLE_DUPLICATE in state.reasons

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccessBanner(state, onOpenPermissions)

        AnimatedVisibility(state.generalError != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val (errorTitle, errorBody) = splitErrorMessage(state.generalError.orEmpty())
            CaptureNotice(
                tone = CaptureNoticeTone.ERROR,
                title = errorTitle,
                body = errorBody,
                actionLabel = if (state.canConfirm) "Reintentar" else null,
                onAction = onRetry,
                modifier = Modifier.testTag("capture_review_error"),
            )
        }

        SourceCard(state, onShowEvidence, onShowConfidence)

        AnimatedVisibility(isDuplicate && state.canConfirm) {
            CaptureNotice(
                tone = CaptureNoticeTone.WARNING,
                title = "Parece un movimiento ya registrado",
                body = "Si es el mismo pago, descarta esta captura.",
                actionLabel = "Descartar",
                onAction = onDiscard,
            )
        }
        AnimatedVisibility(state.isCredit && !isDuplicate, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            CaptureNotice(tone = CaptureNoticeTone.INFO, body = "Las compras con tarjeta de crédito siempre se confirman.")
        }

        Column(Modifier.alpha(formAlpha), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KipuSegmentedControl(
                options = listOf(CaptureDirection.EXPENSE, CaptureDirection.INCOME),
                selected = state.direction,
                onSelect = onDirection,
                label = { if (it == CaptureDirection.EXPENSE) "Gasto" else "Ingreso" },
                enabled = editable && !state.isCredit,
                icon = { if (it == CaptureDirection.EXPENSE) Icons.Rounded.NorthEast else Icons.Rounded.South },
                selectedColors = { if (it == CaptureDirection.EXPENSE) palette.expenseBg to palette.expenseText else palette.incomeBg to palette.incomeText },
                tag = { if (it == CaptureDirection.EXPENSE) "capture_direction_expense" else "capture_direction_income" },
            )

            AmountSection(state, editable, onAmount, onCurrency)

            KipuFieldGroup {
                val dateInferred = candidate.occurredAtInferred && !state.dateEdited
                KipuFieldRow(
                    icon = Icons.Outlined.CalendarToday,
                    iconBg = palette.incomeBg,
                    iconTint = palette.primaryDeep,
                    label = "Fecha",
                    value = captureDateRowLabel(state.occurredAt),
                    onClick = onPickDate,
                    tag = "capture_date",
                    enabled = editable,
                    modified = state.dateEdited,
                    badge = if (dateInferred) ({ KipuStatusChip("Inferida", KipuChipTone.WARNING) }) else null,
                    hint = if (dateInferred) "Se tomó de la hora del aviso. Verifica el día." else null,
                    hintColor = palette.warningText,
                    description = "Fecha ${captureDateRowLabel(state.occurredAt)}${if (dateInferred) ", inferida" else ""}. Cambiar fecha",
                )
                KipuFieldRowDivider()
                val merchantChanged = state.merchantId != candidate.merchantId
                KipuFieldRow(
                    icon = Icons.Outlined.Storefront,
                    iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    label = "Comercio",
                    value = merchantLabel ?: if (state.merchantId != null) "Comercio elegido" else "Elegir comercio",
                    valueMuted = state.merchantId == null,
                    onClick = onPickMerchant,
                    tag = "capture_merchant",
                    enabled = editable,
                    modified = merchantChanged,
                    badge = when {
                        merchantChanged -> null
                        state.merchantId != null -> ({ KipuStatusChip("Reconocido", KipuChipTone.POSITIVE) })
                        else -> ({ KipuStatusChip("Opcional", KipuChipTone.NEUTRAL) })
                    },
                    description = "Comercio: ${merchantLabel ?: "sin elegir"}. Cambiar comercio",
                )
                KipuFieldRowDivider()
                val categoryLabel = state.categories.firstOrNull { it.id == state.categoryId }?.label?.replace(" > ", " › ")
                val categoryChanged = state.categoryId != candidate.categoryId
                val categoryMissing = state.categoryId == null && state.direction == CaptureDirection.EXPENSE
                KipuFieldRow(
                    icon = Icons.Outlined.Category,
                    iconBg = palette.warningBg,
                    iconTint = palette.warningText,
                    label = "Categoría" + if (state.direction == CaptureDirection.EXPENSE) " *" else "",
                    value = categoryLabel ?: "Elegir categoría",
                    valueMuted = categoryLabel == null,
                    onClick = onPickCategory,
                    tag = "capture_category",
                    enabled = editable,
                    modified = categoryChanged && state.categoryId != null,
                    badge = when {
                        categoryChanged && state.categoryId != null -> null
                        categoryMissing -> ({ KipuStatusChip("Falta", KipuChipTone.WARNING) })
                        categoryLabel != null -> ({ KipuStatusChip("Sugerida", KipuChipTone.POSITIVE) })
                        else -> null
                    },
                    error = state.categoryError,
                    description = "Categoría: ${categoryLabel ?: "sin elegir"}. Cambiar categoría",
                )
                KipuFieldRowDivider()
                InstrumentRow(state, editable, onPickInstrument)
            }

            AnimatedVisibility(state.instrumentChoiceRequired, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                AmbiguousInstrumentChoices(state, editable, onInstrument, onPickInstrument)
            }

            AnimatedVisibility(state.showCreditMode, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                CreditModeSection(state, editable, onCreditMode, onInstallments)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AccessBanner(state: CaptureReviewUiState, onOpenPermissions: () -> Unit) {
    when {
        state.manualMode -> CaptureNotice(
            tone = CaptureNoticeTone.INFO,
            title = "Registro manual",
            body = "Usamos los datos ya detectados. Revisa y guarda.",
            modifier = Modifier.testTag("capture_manual_banner"),
        )
        state.canConvertToManual -> CaptureNotice(
            tone = CaptureNoticeTone.WARNING,
            title = if (state.automation == CaptureAutomationStatus.PREMIUM_REQUIRED) "Captura automática en pausa" else "Captura automática desactivada",
            body = if (state.automation == CaptureAutomationStatus.PREMIUM_REQUIRED) {
                "Tu plan Premium no está vigente. Conservamos esta captura para que la registres manualmente."
            } else {
                "Falta permiso o consentimiento. Puedes registrarla manualmente."
            },
            actionLabel = if (state.automation == CaptureAutomationStatus.SETUP_REQUIRED) "Configurar captura" else null,
            onAction = onOpenPermissions,
            modifier = Modifier.testTag("capture_access_banner"),
        )
        state.automation == CaptureAutomationStatus.NO_SESSION ->
            CaptureNotice(tone = CaptureNoticeTone.WARNING, title = "Sesión bloqueada", body = "Desbloquea Kipu para confirmar esta captura.")
    }
}

/** Where the movement came from, how sure Kipu is, and how many of the five data are ready. */
@Composable
private fun SourceCard(state: CaptureReviewUiState, onShowEvidence: () -> Unit, onShowConfidence: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val candidate = requireNotNull(state.candidate)
    val statuses = state.fieldStatuses()
    val ready = statuses.count { it.second != FieldStatus.MISSING }
    val missing = statuses.filter { it.second == FieldStatus.MISSING }.map { it.first }
    val verify = statuses.filter { it.second == FieldStatus.VERIFY }.map { it.first }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.surfaceCard,
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CaptureProviderLogo(candidate.provider, 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Detectado en ${candidate.provider.displayName()} · ${captureRelativeDateTime(candidate.occurredAt ?: candidate.createdAt)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText,
                    )
                    ConfidenceChip(
                        candidate.confidence,
                        Modifier.clickable(role = Role.Button, onClick = onShowConfidence).testTag("capture_confidence"),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.pillTrack, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onShowEvidence)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("capture_detected_text"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TEXTO DETECTADO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
                    Text(
                        candidate.merchantSourceText?.takeIf(String::isNotBlank) ?: "Sin texto reconocible",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText,
                    )
                }
                Icon(Icons.Outlined.Visibility, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ver", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = palette.primaryDeep)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statuses.forEach { (_, status) ->
                        val color by animateColorAsState(
                            when (status) {
                                FieldStatus.READY -> palette.incomeEmerald
                                FieldStatus.VERIFY -> palette.warningAmber
                                FieldStatus.MISSING -> palette.borderSubtle
                            },
                            label = "progressSegment",
                        )
                        Box(Modifier.weight(1f).height(6.dp).background(color, CircleShape))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("$ready de ${statuses.size} datos listos", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
                    AnimatedContent(
                        targetState = when {
                            missing.isNotEmpty() -> "Falta: ${missing.joinNatural()}" to palette.warningText
                            verify.isNotEmpty() -> "Verifica: ${verify.joinNatural()}" to palette.warningText
                            else -> "Todo listo" to palette.incomeText
                        },
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "progressLabel",
                    ) { (text, color) ->
                        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountSection(
    state: CaptureReviewUiState,
    editable: Boolean,
    onAmount: (String) -> Unit,
    onCurrency: (String) -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val candidate = requireNotNull(state.candidate)
    val parsed = parseCaptureAmountMinor(state.amountText)
    val modified = parsed != null && candidate.amountMinor != null && parsed != candidate.amountMinor
    KipuAmountCard(
        label = "Monto",
        symbol = currencySymbol(state.currency),
        amountText = state.amountText,
        onAmountChanged = onAmount,
        amountColor = if (state.direction == CaptureDirection.INCOME) palette.incomeText else palette.expenseText,
        inputTag = "capture_amount",
        enabled = editable,
        accent = when {
            state.amountError != null -> palette.expenseText
            candidate.amountMinor == null && parsed == null -> palette.warningBorder
            modified -> palette.primaryDeep
            else -> null
        },
        chip = {
            when {
                modified -> KipuStatusChip("Modificado", KipuChipTone.POSITIVE)
                candidate.amountMinor == null && parsed == null -> KipuStatusChip("Falta", KipuChipTone.WARNING)
                candidate.amountMinor != null -> KipuStatusChip("Detectado", KipuChipTone.POSITIVE)
                else -> KipuStatusChip("Ingresado", KipuChipTone.NEUTRAL)
            }
        },
    ) {
        val note = state.amountError ?: if (candidate.amountMinor == null && parsed == null) "No se pudo leer. Escríbelo como aparece en tu banco." else null
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = if (state.amountError != null) palette.expenseText else palette.warningText)
        }
        if (candidate.currency == null) {
            Spacer(Modifier.height(10.dp))
            KipuSegmentedControl(
                options = listOf("PEN", "USD"),
                selected = state.currency.orEmpty(),
                onSelect = onCurrency,
                label = { if (it == "PEN") "Soles (S/)" else "Dólares (US$)" },
                enabled = editable,
            )
        }
    }
}

@Composable
private fun InstrumentRow(state: CaptureReviewUiState, editable: Boolean, onPickInstrument: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val candidate = requireNotNull(state.candidate)
    val instrument = state.selectedInstrument
    val brand = instrument?.brandKey?.let(KipuBrand::from)
    val detected = instrument != null && when (instrument.kind) {
        CaptureInstrumentKind.CREDIT_CARD -> instrument.id == candidate.cardId
        else -> instrument.id == candidate.accountId || instrument.accountId == candidate.accountId
    }
    KipuFieldRow(
        icon = instrument.icon(),
        iconBg = palette.transferBg,
        iconTint = palette.transferBlue,
        label = "Cuenta o tarjeta",
        value = instrument?.let { "${it.label} · ${it.currency}" } ?: "Elegir cuenta o tarjeta",
        valueMuted = instrument == null,
        onClick = onPickInstrument,
        tag = "capture_instrument",
        enabled = editable,
        modified = instrument != null && !detected,
        badge = when {
            instrument != null && !detected -> null
            instrument != null -> ({ KipuStatusChip("Detectada", KipuChipTone.POSITIVE) })
            state.instrumentChoiceRequired -> ({ KipuStatusChip("Elige una", KipuChipTone.WARNING) })
            else -> ({ KipuStatusChip("Falta", KipuChipTone.WARNING) })
        },
        error = state.instrumentError,
        leading = if (brand != null && instrument != null) {
            { BrandLogo(brand = brand, fallbackName = instrument.label, contentDescription = null, size = 40.dp) }
        } else {
            null
        },
        description = "Cuenta o tarjeta: ${instrument?.label ?: "sin elegir"}. Cambiar",
    )
}

private fun CaptureInstrumentChoice?.icon(): ImageVector = when (this?.kind) {
    CaptureInstrumentKind.CREDIT_CARD -> Icons.Outlined.CreditCard
    CaptureInstrumentKind.WALLET -> Icons.Outlined.AccountBalanceWallet
    else -> Icons.Outlined.AccountBalance
}

/** HU-46 esc. 2: several instruments share the last 4 digits; Kipu never picks one for the user. */
@Composable
private fun AmbiguousInstrumentChoices(
    state: CaptureReviewUiState,
    editable: Boolean,
    onInstrument: (CaptureInstrumentChoice) -> Unit,
    onPickInstrument: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val last4 = state.candidate?.cardLast4
    val matches = last4?.let { digits -> state.instruments.filter { it.label.contains(digits) } }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Varias cuentas o tarjetas coinciden${last4?.let { " con ····$it" } ?: ""}. Elige una.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.warningText,
        )
        matches.forEach { choice ->
            InstrumentOptionCard(choice, selected = false, enabled = editable, highlight = true) { onInstrument(choice) }
        }
        TextButton(onClick = onPickInstrument, enabled = editable, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (matches.isEmpty()) "Elegir cuenta o tarjeta" else "Elegir otra cuenta o tarjeta", fontWeight = FontWeight.Bold, color = palette.primaryDeep)
        }
    }
}

@Composable
private fun InstrumentOptionCard(
    choice: CaptureInstrumentChoice,
    selected: Boolean,
    enabled: Boolean,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val border by animateColorAsState(
        when {
            selected -> palette.primaryDeep
            highlight -> palette.warningBorder
            else -> palette.borderSubtle
        },
        label = "instrumentBorder",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(palette.surfaceCard, RoundedCornerShape(16.dp))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InstrumentLeading(choice)
        Column(Modifier.weight(1f)) {
            Text(choice.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
            Text(
                listOf(
                    when (choice.kind) {
                        CaptureInstrumentKind.CREDIT_CARD -> "Crédito"
                        CaptureInstrumentKind.WALLET -> "Billetera"
                        CaptureInstrumentKind.DEBIT_ACCOUNT -> "Débito"
                    },
                    choice.currency,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryMuted,
            )
        }
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = palette.primaryDeep))
    }
}

@Composable
private fun InstrumentLeading(choice: CaptureInstrumentChoice) {
    val palette = rememberCalmEmeraldColors()
    val brand = choice.brandKey?.let(KipuBrand::from)
    if (brand != null) {
        BrandLogo(brand = brand, fallbackName = choice.label, contentDescription = null, size = 40.dp)
    } else {
        KipuIconTile(choice.icon(), palette.transferBg, palette.transferBlue)
    }
}

@Composable
private fun CreditModeSection(
    state: CaptureReviewUiState,
    editable: Boolean,
    onCreditMode: (CreditMode) -> Unit,
    onInstallments: (String) -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.surfaceCard,
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth().testTag("capture_credit_mode"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Forma de pago", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
            KipuSegmentedControl(
                options = listOf(CreditMode.DIRECT, CreditMode.INSTALLMENTS),
                selected = state.creditMode,
                onSelect = onCreditMode,
                label = { if (it == CreditMode.DIRECT) "Directo" else "En cuotas" },
                enabled = editable,
                tag = { if (it == CreditMode.DIRECT) "capture_credit_direct" else "capture_credit_installments" },
            )
            AnimatedVisibility(state.creditMode == CreditMode.INSTALLMENTS, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                InstallmentsStepper(state, editable, onInstallments)
            }
        }
    }
}

@Composable
private fun InstallmentsStepper(state: CaptureReviewUiState, editable: Boolean, onInstallments: (String) -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val count = state.installmentsText.toIntOrNull()
    val total = parseCaptureAmountMinor(state.amountText)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Número de cuotas", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted, modifier = Modifier.weight(1f))
            KipuStatusChip("Sin interés", KipuChipTone.NEUTRAL)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton(Icons.Rounded.Remove, "Menos cuotas", editable && (count ?: 2) > 2) {
                onInstallments(((count ?: 3) - 1).coerceIn(2, 36).toString())
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .border(BorderStroke(1.dp, if (state.installmentsError != null) palette.expenseText else palette.borderSubtle), RoundedCornerShape(14.dp))
                    .testTag("capture_installments"),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(count, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "installments") { value ->
                    Text(value?.toString() ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = palette.primaryText)
                }
            }
            StepButton(Icons.Rounded.Add, "Más cuotas", editable && (count ?: 0) < 36) {
                onInstallments(((count ?: 1) + 1).coerceIn(2, 36).toString())
            }
        }
        state.installmentsError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.expenseText) }
        if (count != null && count in 2..36 && total != null) {
            val base = total / count
            val last = total - base * (count - 1)
            val parts = (List(count - 1) { base } + last).take(4).map { formatCaptureAmount(it, state.currency).orEmpty() }
            Surface(shape = RoundedCornerShape(14.dp), color = palette.pillTrack, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "$count cuotas: " + parts.joinToString(" · ") + if (count > 4) " …" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText,
                    )
                    Text("Suman ${formatCaptureAmount(total, state.currency)}, el monto de la compra.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                }
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.borderSubtle),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) palette.primaryDeep else palette.secondaryMuted)
    }
}

/** Fixed footer: what is still missing, the main action and the "nothing is saved yet" reassurance. */
@Composable
private fun ReviewFooter(state: CaptureReviewUiState, onConfirm: () -> Unit, onConvertToManual: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val missing = state.fieldStatuses().filter { it.second == FieldStatus.MISSING }.map { it.first }
    val installments = state.installmentsText.toIntOrNull()?.takeIf { state.isCredit && state.creditMode == CreditMode.INSTALLMENTS && it in 2..36 }
    val hint: Pair<String, Color>? = when {
        state.canConvertToManual || !state.canConfirm -> null
        missing.isNotEmpty() -> "Falta: ${missing.joinNatural()}" to palette.warningText
        CaptureReason.POSSIBLE_DUPLICATE in state.reasons -> "Te preguntaremos si guardar de todos modos" to palette.warningText
        installments != null -> "Plan de $installments cuotas sin interés" to palette.incomeText
        else -> null
    }
    Surface(color = palette.surfaceCard, shadowElevation = 8.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(hint != null) {
                hint?.let { (text, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(if (color == palette.incomeText) Icons.Outlined.CheckCircle else Icons.Outlined.Info, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                    }
                }
            }
            val convert = state.canConvertToManual
            Button(
                onClick = if (convert) onConvertToManual else onConfirm,
                enabled = (convert || state.canConfirm) && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag(if (convert) "capture_convert_manual" else "capture_confirm"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryDeep,
                    contentColor = palette.onPrimaryDeep,
                    disabledContainerColor = if (state.isSaving) palette.primaryDeep.copy(alpha = 0.7f) else palette.pillTrack,
                    disabledContentColor = if (state.isSaving) palette.onPrimaryDeep else palette.secondaryMuted,
                ),
            ) {
                AnimatedContent(
                    targetState = when {
                        state.isSaving -> null
                        convert -> "Convertir a manual"
                        state.manualMode -> "Guardar movimiento"
                        installments != null -> "Confirmar compra en $installments cuotas"
                        else -> "Confirmar movimiento"
                    },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "confirmLabel",
                ) { label ->
                    if (label == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = palette.onPrimaryDeep, strokeWidth = 2.dp)
                            Text("Guardando…", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!state.canConvertToManual) {
                Text(
                    "Nada se registra hasta que confirmes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CaptureUnavailable(onBack: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("capture_unavailable"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(80.dp).background(palette.pillTrack, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = palette.secondaryMuted, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Esta captura ya no está disponible", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = palette.primaryText)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pudo registrarse, descartarse o vencer.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.secondaryMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
        ) { Text("Volver a la bandeja", fontWeight = FontWeight.Bold) }
    }
}

/** The movement is already registered; only the matching expected payment is left to choose. */
@Composable
private fun ExpectedPaymentChoice(state: CaptureReviewUiState, onChooseExpected: (String?) -> Unit, onBack: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val candidate = state.resolvedForChoice
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CaptureNotice(
                tone = CaptureNoticeTone.INFO,
                title = "Movimiento registrado",
                body = "Coincide con varios pagos esperados. Elige cuál es; tu saldo no cambia.",
            )
        }
        if (candidate != null) {
            item {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CaptureProviderLogo(candidate.provider, 40.dp)
                    Text(
                        listOfNotNull(candidate.merchantSourceText, formatCaptureAmount(candidate.amountMinor, candidate.currency)).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText,
                    )
                }
            }
        }
        state.generalError?.let { item { CaptureNotice(tone = CaptureNoticeTone.ERROR, body = it) } }
        if (state.expectedChoices.isEmpty()) {
            item { Text("No hay pagos esperados disponibles.", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted) }
        }
        items(state.expectedChoices, key = { it.first.id }) { (payment, label) ->
            Surface(
                onClick = { onChooseExpected(payment.id) },
                enabled = !state.isSaving,
                shape = RoundedCornerShape(16.dp),
                color = palette.surfaceCard,
                border = BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KipuIconTile(Icons.Outlined.Schedule, palette.incomeBg, palette.primaryDeep)
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
                        Text("Vence ${payment.dueDate}", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                    }
                    Text(formatCaptureAmount(payment.expectedAmountMinor, payment.currency).orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.primaryText)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { onChooseExpected(null) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.borderSubtle),
            ) { Text("Ninguno de estos", fontWeight = FontWeight.Bold, color = palette.primaryText) }
        }
        item {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Decidir después", color = palette.secondaryMuted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetScaffold(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val palette = rememberCalmEmeraldColors()
    KipuBottomSheet(onDismissRequest = onDismiss, containerColor = palette.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.primaryText, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = palette.secondaryMuted)
                }
            }
            content()
        }
    }
}

@Composable
private fun EvidenceSheet(provider: String, snippet: String?, onDismiss: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    SheetScaffold("Texto detectado", onDismiss) {
        Text("Lo que Kipu leyó del aviso de $provider.", style = MaterialTheme.typography.bodyLarge, color = palette.secondaryMuted)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.surfaceCard,
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                snippet?.takeIf(String::isNotBlank) ?: "Sin texto guardado.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = palette.primaryText,
            )
        }
        SheetFact(Icons.Outlined.Lock, "Se guarda enmascarado: números de 5 o más dígitos se ocultan.")
        SheetFact(Icons.Outlined.Schedule, "Se borra al registrar o descartar, o a los 7 días.")
        SheetFact(Icons.Outlined.Visibility, "Ningún dato sensible sale de tu teléfono.")
        SheetCloseButton(onDismiss)
    }
}

@Composable
private fun ConfidenceSheet(level: CaptureConfidenceLevel, reasons: List<CaptureReason>, onDismiss: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    SheetScaffold("Qué significa la confianza", onDismiss) {
        listOf(
            CaptureConfidenceLevel.HIGH to "Datos completos y claros. Con cuenta o billetera se registra solo.",
            CaptureConfidenceLevel.MEDIUM to "Leímos lo principal, pero algo conviene revisarlo.",
            CaptureConfidenceLevel.LOW to "Faltan datos clave. Complétalos antes de confirmar.",
        ).forEach { (option, text) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (option == level) palette.surfaceCard else Color.Transparent, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConfidenceChip(option)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.primaryText, modifier = Modifier.weight(1f))
            }
        }
        if (reasons.isNotEmpty()) {
            HorizontalDivider(color = palette.borderSubtle)
            Text("En esta captura", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
            reasons.forEach { SheetFact(Icons.Outlined.Info, it.label) }
        }
        SheetCloseButton(onDismiss)
    }
}

@Composable
private fun SheetFact(icon: ImageVector, text: String) {
    val palette = rememberCalmEmeraldColors()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.primaryText)
    }
}

@Composable
private fun SheetCloseButton(onDismiss: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
    ) { Text("Entendido", fontWeight = FontWeight.Bold) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstrumentPickerSheet(
    instruments: List<CaptureInstrumentChoice>,
    selected: CaptureInstrumentChoice?,
    onSelect: (CaptureInstrumentChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    SheetScaffold("Cuenta o tarjeta", onDismiss) {
        if (instruments.isEmpty()) {
            Text("No tienes cuentas ni tarjetas activas.", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
        } else {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(instruments, key = { "${it.kind.name}:${it.id}" }) { choice ->
                    val isSelected = selected?.kind == choice.kind && selected.id == choice.id
                    Box(Modifier.semantics { contentDescription = if (isSelected) "${choice.label}, seleccionada" else choice.label }) {
                        InstrumentOptionCard(choice, selected = isSelected, enabled = true) { onSelect(choice) }
                    }
                }
            }
        }
    }
}
