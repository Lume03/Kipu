package com.kipu.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    onNewAccount: () -> Unit,
    viewModel: ReceiptFlowViewModel = hiltViewModel(),
    movementViewModel: QuickMovementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var reviewedType by rememberSaveable { mutableStateOf(MovementType.EXPENSE) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Comprobantes Yape") }, navigationIcon = {
            IconButton(onClick = { if (state.selected != null) viewModel.closeEditor() else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let {
                Text(it, Modifier.padding(16.dp).testTag("receipt_feedback"), style = MaterialTheme.typography.bodyMedium)
            }
            val selected = state.selected
            if (selected != null) {
                ReceiptMetadataEditor(selected, state.busy, onContinue = { edit, type ->
                    reviewedType = type
                    viewModel.edit(edit)
                })
            } else if (sharedUri != null && !state.importConsumed) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Recibimos una imagen compartida", style = MaterialTheme.typography.titleLarge)
                    Text("Autoriza a Kipu a leer este comprobante Yape en tu dispositivo. El OCR local está disponible en Kipu Free. La imagen no se subirá ni se conservará.")
                    Text("La imagen no acredita por sí sola un pago. Revisarás los datos antes de registrar; tu saldo no cambiará durante la lectura.")
                    Button(onClick = { viewModel.import(sharedUri) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().testTag("receipt_authorize_ocr")) { Text("Autorizar OCR local de Yape") }
                    OutlinedButton(onClick = viewModel::manual, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuar sin OCR")
                    }
                }
            } else {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::manual, enabled = !state.busy) { Text("Registro manual") }
                    TextButton(onClick = onHistory) { Text("Ver movimientos") }
                }
                Text("Comparte una imagen desde Yape para leerla aquí. Nada se registra automáticamente.", Modifier.padding(16.dp))
                ReceiptReviewScreen(
                    candidates = state.candidates,
                    onReview = viewModel::select,
                    onEdit = viewModel::editCandidate,
                    onDiscard = viewModel::discard,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    val candidate = state.selected
    if (state.readyForMovement && candidate != null) {
        LaunchedEffect(candidate.id) {
            movementViewModel.prepareReceiptDraft(
                candidateId = candidate.id, amountMinor = candidate.amountMinor,
                occurredAt = candidate.occurredAt, recipient = candidate.recipient,
                movementType = reviewedType,
                onConfirm = { command -> viewModel.confirm(candidate.id, command) },
            )
        }
        QuickMovementBottomSheet(
            viewModel = movementViewModel,
            onDismissRequest = {
                movementViewModel.resetForm()
                viewModel.closeMovement()
            },
            onSaved = { viewModel.closeEditor() },
            onMessage = viewModel::message,
            onNavigateToNewAccount = onNewAccount,
        )
    }
}

@Composable
private fun ReceiptMetadataEditor(
    candidate: ReceiptCandidate,
    busy: Boolean,
    onContinue: (ReceiptCandidateEdit, MovementType) -> Unit,
) {
    val lima = remember { ZoneId.of("America/Lima") }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Revisa y corrige el candidato", style = MaterialTheme.typography.titleLarge)
        Text("Comprueba si enviaste o recibiste el pago. Completa lo que no pudo leerse. Aún no hay movimiento ni cambio de saldo.")
        Text("Al confirmar, elige tu billetera Yape o su cuenta afiliada en soles.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == MovementType.EXPENSE, onClick = { type = MovementType.EXPENSE }, label = { Text("Envié · gasto") })
            FilterChip(selected = type == MovementType.INCOME, onClick = { type = MovementType.INCOME }, label = { Text("Recibí · ingreso") })
        }
        OutlinedTextField(amount, { amount = it }, label = { Text("Monto en soles") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("receipt_amount"), singleLine = true)
        OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("receipt_date"), singleLine = true)
        OutlinedTextField(time, { time = it }, label = { Text("Hora de Perú (HH:mm, 24 h)") }, modifier = Modifier.fillMaxWidth().testTag("receipt_time"), singleLine = true)
        OutlinedTextField(recipient, { recipient = it }, label = { Text("Destinatario o remitente") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(reference, { reference = it }, label = { Text("Nro. de operación (no código de seguridad)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("La referencia ayuda a evitar duplicados. Pagos diferentes con el mismo monto no son necesariamente el mismo pago.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val minor = MoneyInputParser.parseMinorUnits(amount)
            val occurredAt = runCatching { LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(lima).toInstant().toEpochMilli() }.getOrNull()
            if (minor == null || minor <= 0 || occurredAt == null || type == null) {
                error = "Elige ingreso o gasto e ingresa un monto positivo, fecha y hora válidos."
            } else {
                error = null
                onContinue(ReceiptCandidateEdit(minor, occurredAt, recipient.takeIf(String::isNotBlank), reference.takeIf(String::isNotBlank), type == MovementType.INCOME), requireNotNull(type))
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("receipt_review_continue")) {
            Text("Elegir cuenta y revisar movimiento")
        }
    }
}
