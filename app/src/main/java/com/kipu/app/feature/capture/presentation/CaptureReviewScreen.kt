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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.feature.capture.domain.CaptureInstrumentKind
import com.kipu.app.feature.capture.domain.CreditMode
import com.kipu.app.feature.movements.presentation.DuplicateWarningDialog
import com.kipu.app.feature.categories.presentation.components.MerchantPickerBottomSheet
import com.kipu.app.feature.categories.presentation.components.MerchantPickerViewModel
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.Instant
import java.time.ZoneOffset

private enum class CapturePicker { CATEGORY, INSTRUMENT, MERCHANT }

/**
 * Review and edit of one captured movement (HU-51, HU-48). Leaving without confirming changes nothing:
 * every edit lives in the ViewModel until [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val palette = rememberCalmEmeraldColors()
    val merchantPicker: MerchantPickerViewModel = hiltViewModel()
    val merchantPickerState by merchantPicker.uiState.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf<CapturePicker?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Revisar captura", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver sin cambios")
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.notFound || state.candidate == null -> CaptureUnavailable(onBack)
                state.resolvedForChoice != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Movimiento registrado", style = MaterialTheme.typography.titleLarge)
                    Text("Elige a qué pago esperado corresponde. Puedes volver a la bandeja y asociarlo después.")
                    OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver a la bandeja") }
                }
                else -> ReviewForm(
                    state = state,
                    onDirection = onDirection,
                    onAmount = onAmount,
                    onCurrency = onCurrency,
                    merchantLabel = merchantPickerState.catalogEntries.firstOrNull { it.id.value == state.merchantId }?.name,
                    onPickMerchant = { picker = CapturePicker.MERCHANT },
                    onClearMerchant = { onMerchant("") },
                    onCreditMode = onCreditMode,
                    onInstallments = onInstallments,
                    onPickCategory = { picker = CapturePicker.CATEGORY },
                    onPickInstrument = { picker = CapturePicker.INSTRUMENT },
                    onPickDate = { showDatePicker = true },
                    onConfirm = onConfirm,
                    onConvertToManual = onConvertToManual,
                    onDiscard = { showDiscard = true },
                    onOpenPermissions = onOpenPermissions,
                )
            }
        }
    }

    when (picker) {
        CapturePicker.MERCHANT -> MerchantPickerBottomSheet(
            state = merchantPickerState.copy(selectedMerchant = merchantPickerState.catalogEntries.firstOrNull { it.id.value == state.merchantId }),
            onQueryChange = merchantPicker::onQueryChanged,
            onSelectMerchant = { onMerchant(it.id.value); merchantPicker.onQueryChanged(""); picker = null },
            onSetProvisionalText = {},
            onClearSelection = { onMerchant(""); merchantPicker.onQueryChanged(""); picker = null },
            onDismiss = { merchantPicker.onQueryChanged(""); picker = null },
            title = "Comercio",
            subtitle = "Elige un comercio del cat?logo o deja la selecci?n vac?a",
            allowProvisionalText = false,
            selectedMerchantIds = setOfNotNull(state.merchantId),
        )
        CapturePicker.CATEGORY -> PickerSheet(
            title = "Categoría",
            emptyText = "No hay categorías disponibles para este tipo de movimiento.",
            options = state.categories.map { it.label to it.id },
            selectedKey = state.categoryId,
            onSelect = { id ->
                onCategory(id)
                picker = null
            },
            onDismiss = { picker = null },
        )
        CapturePicker.INSTRUMENT -> PickerSheet(
            title = "Cuenta o tarjeta",
            emptyText = "No tienes cuentas ni tarjetas activas.",
            options = state.instruments
                .filter { state.direction == CaptureDirection.EXPENSE || it.kind != CaptureInstrumentKind.CREDIT_CARD }
                .map { "${it.label} · ${it.currency}" to "${it.kind.name}:${it.id}" },
            selectedKey = state.selectedInstrument?.let { "${it.kind.name}:${it.id}" },
            onSelect = { key ->
                state.instruments.firstOrNull { "${it.kind.name}:${it.id}" == key }?.let(onInstrument)
                picker = null
            },
            onDismiss = { picker = null },
        )
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
        )
    }

    if (state.resolvedForChoice != null) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Elige un pago esperado") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Este pago coincide con varios pagos esperados, elige cuál")
                    if (state.expectedChoices.isEmpty()) Text("No hay pagos esperados disponibles. Puedes elegir Ninguno o volver más tarde.")
                    state.expectedChoices.forEach { (payment, label) ->
                        TextButton(enabled = !state.isSaving, onClick = { onChooseExpected(payment.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${label} · ${payment.dueDate} · ${formatCaptureAmount(payment.expectedAmountMinor, payment.currency)}")
                        }
                    }
                    state.generalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !state.isSaving, onClick = { onChooseExpected(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ninguno") } },
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

private fun utcMidnightOf(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(CAPTURE_ZONE).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun CaptureUnavailable(onBack: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("capture_unavailable"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Esta captura ya no está disponible", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = palette.primaryText)
        Spacer(Modifier.size(10.dp))
        Text(
            "Pudo haberse registrado, descartado o vencido. Revisa tus movimientos o vuelve a la bandeja.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.secondaryMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) { Text("Volver") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewForm(
    state: CaptureReviewUiState,
    onDirection: (CaptureDirection) -> Unit,
    onAmount: (String) -> Unit,
    onCurrency: (String) -> Unit,
    merchantLabel: String?,
    onPickMerchant: () -> Unit,
    onClearMerchant: () -> Unit,
    onCreditMode: (CreditMode) -> Unit,
    onInstallments: (String) -> Unit,
    onPickCategory: () -> Unit,
    onPickInstrument: () -> Unit,
    onPickDate: () -> Unit,
    onConfirm: () -> Unit,
    onConvertToManual: () -> Unit,
    onDiscard: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    val candidate = requireNotNull(state.candidate)
    val categoryLabel = state.categories.firstOrNull { it.id == state.categoryId }?.label
    val instrument = state.selectedInstrument
    val dateIsInferred = candidate.occurredAtInferred && !state.dateEdited
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Provider, confidence and explainable reasons (HU-47).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceChip("Detectado en ${candidate.provider.displayName()}")
                ConfidenceChip(candidate.confidence)
            }
            if (state.reasons.isNotEmpty()) {
                Text("Por qué conviene revisarla", style = MaterialTheme.typography.labelLarge, color = palette.secondaryMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.reasons.forEach { ReasonChip(it.label) }
                }
            }
        }

        AccessBanner(state, onConvertToManual, onOpenPermissions)

        state.generalError?.let { NoticeCard(it, warning = true, modifier = Modifier.testTag("capture_review_error")) }

        // Direction
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.direction == CaptureDirection.EXPENSE,
                onClick = { onDirection(CaptureDirection.EXPENSE) },
                label = { Text("Gasto") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("capture_direction_expense"),
            )
            FilterChip(
                selected = state.direction == CaptureDirection.INCOME,
                onClick = { onDirection(CaptureDirection.INCOME) },
                label = { Text("Ingreso") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("capture_direction_income"),
            )
        }

        // Amount (+ currency only when the notification did not say it)
        OutlinedTextField(
            value = state.amountText,
            onValueChange = onAmount,
            label = { Text(if (state.currency != null) "Monto (${currencySymbol(state.currency)})" else "Monto") },
            isError = state.amountError != null,
            supportingText = { state.amountError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("capture_amount"),
        )
        if (candidate.currency == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PEN" to "Soles (S/)", "USD" to "Dólares (US$)").forEach { (code, label) ->
                    FilterChip(
                        selected = state.currency == code,
                        onClick = { onCurrency(code) },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        // Date with inferred badge (HU-46 esc. 3)
        FieldRow(
            label = "Fecha",
            value = formatCaptureDay(state.occurredAt),
            icon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailing = if (dateIsInferred) ({ ReasonChip("Dato inferido") }) else null,
            onClick = onPickDate,
            description = "Fecha ${formatCaptureDay(state.occurredAt)}${if (dateIsInferred) ", dato inferido de la hora de la notificación" else ""}. Cambiar fecha",
            tag = "capture_date",
        )

        OutlinedTextField(
            value = candidate.merchantSourceText.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Texto detectado") },
            modifier = Modifier.fillMaxWidth().testTag("capture_detected_text"),
        )
        FieldRow(
            label = "Comercio",
            value = merchantLabel ?: if (state.merchantId != null) "Comercio seleccionado" else "Sin comercio",
            valueMuted = state.merchantId == null,
            onClick = onPickMerchant,
            description = "Comercio: ${merchantLabel ?: if (state.merchantId != null) "seleccionado" else "sin elegir"}. Cambiar comercio",
            tag = "capture_merchant",
        )
        if (state.merchantId != null) {
            TextButton(onClick = onClearMerchant, modifier = Modifier.heightIn(min = 48.dp)) { Text("Quitar comercio") }
        }

        FieldRow(
            label = "Categoría",
            value = categoryLabel ?: "Elegir categoría",
            valueMuted = categoryLabel == null,
            error = state.categoryError,
            onClick = onPickCategory,
            description = "Categoría: ${categoryLabel ?: "sin elegir"}. Cambiar categoría",
            tag = "capture_category",
        )

        FieldRow(
            label = "Cuenta o tarjeta",
            value = instrument?.let { "${it.label} · ${it.currency}" } ?: "Elegir cuenta o tarjeta",
            valueMuted = instrument == null,
            error = state.instrumentError
                ?: if (state.instrumentChoiceRequired) "Varias cuentas o tarjetas coinciden. Elige una para continuar." else null,
            onClick = onPickInstrument,
            description = "Instrumento: ${instrument?.label ?: "sin elegir"}. Cambiar cuenta o tarjeta",
            tag = "capture_instrument",
        )

        // Credit mode only for credit cards (HU-48 esc. 4)
        if (state.showCreditMode) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("capture_credit_mode")) {
                Text("Forma de pago con tarjeta de crédito", style = MaterialTheme.typography.labelLarge, color = palette.secondaryMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.creditMode == CreditMode.DIRECT,
                        onClick = { onCreditMode(CreditMode.DIRECT) },
                        label = { Text("Directo") },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("capture_credit_direct"),
                    )
                    FilterChip(
                        selected = state.creditMode == CreditMode.INSTALLMENTS,
                        onClick = { onCreditMode(CreditMode.INSTALLMENTS) },
                        label = { Text("En cuotas") },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("capture_credit_installments"),
                    )
                }
                if (state.creditMode == CreditMode.INSTALLMENTS) {
                    OutlinedTextField(
                        value = state.installmentsText,
                        onValueChange = onInstallments,
                        label = { Text("Número de cuotas (2 a 36)") },
                        isError = state.installmentsError != null,
                        supportingText = { Text(state.installmentsError ?: "Sin interés") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("capture_installments"),
                    )
                }
            }
        }

        if (state.canConfirm) {
            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("capture_confirm"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
            ) {
                Text(
                    if (state.manualMode) "Guardar movimiento" else "Confirmar movimiento",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        TextButton(
            onClick = onDiscard,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("capture_discard"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Descartar captura", style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "Nada se registra hasta que confirmes. Si sales de esta pantalla, la captura sigue pendiente.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryMuted,
        )
    }
}

@Composable
private fun AccessBanner(state: CaptureReviewUiState, onConvertToManual: () -> Unit, onOpenPermissions: () -> Unit) {
    when {
        state.manualMode -> NoticeCard(
            "Registro manual: usaste los datos ya detectados. No se leerán nuevas notificaciones.",
            modifier = Modifier.testTag("capture_manual_banner"),
        )
        state.canConvertToManual -> Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("capture_access_banner")) {
            NoticeCard(
                if (state.automation == CaptureAutomationStatus.PREMIUM_REQUIRED) {
                    "Tu plan Premium no está vigente, así que la captura automática está en pausa. Conservamos esta captura para que la registres manualmente."
                } else {
                    "La captura automática está desactivada (permiso o consentimiento). Conservamos esta captura para que la registres manualmente."
                },
                warning = true,
            )
            Button(
                onClick = onConvertToManual,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("capture_convert_manual"),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Convertir a manual", fontWeight = FontWeight.SemiBold) }
            if (state.automation == CaptureAutomationStatus.SETUP_REQUIRED) {
                OutlinedButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Configurar captura") }
            }
        }
        state.automation == CaptureAutomationStatus.NO_SESSION ->
            NoticeCard("Desbloquea Kipu para confirmar esta captura.", warning = true)
    }
}

@Composable
private fun NoticeCard(text: String, warning: Boolean = false, modifier: Modifier = Modifier) {
    val palette = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (warning) palette.warningBg else palette.surfaceCard,
        border = BorderStroke(1.dp, if (warning) palette.warningBorder else palette.borderSubtle),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (warning) Icons.Filled.WarningAmber else Icons.Filled.Info,
                contentDescription = null,
                tint = if (warning) palette.warningText else palette.primaryDeep,
                modifier = Modifier.size(20.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warning) palette.warningText else palette.secondaryMuted)
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    description: String,
    tag: String,
    valueMuted: Boolean = false,
    error: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = rememberCalmEmeraldColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.surfaceCard,
            border = BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(tag),
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                icon?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = palette.secondaryMuted)
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (valueMuted) palette.secondaryMuted else palette.primaryText,
                    )
                }
                trailing?.invoke()
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = palette.secondaryMuted)
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    emptyText: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    KipuBottomSheet(onDismissRequest = onDismiss, containerColor = palette.background) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = palette.primaryText,
        )
        if (options.isEmpty()) {
            Text(emptyText, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options, key = { it.second }) { (label, key) ->
                    val selected = key == selectedKey
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { onSelect(key) }
                            .semantics { contentDescription = if (selected) "$label, seleccionado" else label }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) palette.primaryDeep else palette.primaryText,
                        )
                        if (selected) Text("âœ“", color = palette.primaryDeep, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
