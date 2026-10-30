package com.kipu.app.feature.statements.presentation

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.receipts.presentation.tealTint
import com.kipu.app.feature.statements.domain.StatementImport
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Importar movimientos": the one place that groups the two local-reading imports (a Yape receipt image and a card
 * statement PDF) together with the statements left half-reviewed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportMovementsScreen(
    onBack: () -> Unit,
    onYape: () -> Unit,
    onReview: (String) -> Unit,
    onPlans: () -> Unit,
    viewModel: StatementImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = rememberCalmEmeraldColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) viewModel.documentPicked(null) else scope.launch {
            val details = withContext(Dispatchers.IO) { fileDetails(context, uri) }
            viewModel.documentPicked(uri, details?.first, details?.second)
        }
    }
    LaunchedEffect(state.pickDocument) {
        if (state.pickDocument) { viewModel.pickerLaunched(); picker.launch(arrayOf("application/pdf")) }
    }
    // A resumed draft has no scan to celebrate; a fresh import waits for the scan overlay below.
    LaunchedEffect(state.openImportId, state.scan) {
        val id = state.openImportId
        if (id != null && state.scan == null) { viewModel.consumeNavigation(); onReview(id) }
    }
    ImportMovementsContent(state, onBack, onYape, onPlans,
        onPick = viewModel::requestImport, onResume = viewModel::resumeImport, onAcceptConsent = viewModel::acceptConsent, onCancelConsent = viewModel::cancelConsent,
        onPassword = viewModel::passwordChanged, onSubmitPassword = viewModel::submitPassword, onCancelPassword = viewModel::cancelPassword,
        onDismissNotice = viewModel::dismissNotice, onDismissPremium = viewModel::dismissPremium,
        onScanFinished = {
            val id = state.openImportId
            viewModel.clearScan()
            if (id != null) { viewModel.consumeNavigation(); onReview(id) }
        })
}

/** Stateless body of the hub so every state can be drawn and tested without a ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportMovementsContent(
    state: StatementImportUiState,
    onBack: () -> Unit,
    onYape: () -> Unit,
    onPlans: () -> Unit,
    onPick: () -> Unit,
    onResume: (String) -> Unit,
    onAcceptConsent: () -> Unit,
    onCancelConsent: () -> Unit,
    onPassword: (String) -> Unit,
    onSubmitPassword: () -> Unit,
    onCancelPassword: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissPremium: () -> Unit,
    onScanFinished: () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    Scaffold(containerColor = palette.background, topBar = {
        TopAppBar(title = { Text("Importar movimientos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background, titleContentColor = palette.primaryText,
                navigationIconContentColor = palette.primaryText))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Elige qué quieres traer a Kipu. Todo se lee en tu teléfono.", style = MaterialTheme.typography.bodyLarge, color = palette.secondaryMuted)
            ImportOption(onClick = onYape, icon = Icons.Filled.ReceiptLong, tile = "violet", title = "Comprobante de Yape", badge = "Gratis" to "teal",
                body = "Comparte o sube una captura y Kipu la convierte en un movimiento.", modifier = Modifier.testTag("import_option_yape"))
            Surface(shape = RoundedCornerShape(22.dp), color = palette.surfaceCard, border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle),
                modifier = Modifier.fillMaxWidth().testTag("statement_import_entry")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OptionHeader(Icons.Filled.Description, "bad", "Estado de cuenta de tarjeta", "Premium" to "violet")
                    Text("Trae todos los movimientos de un ciclo de una vez. Sirve para cargar compras anteriores o al agregar una tarjeta nueva.",
                        style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatementToneChip("PDF", "gray", Icons.Filled.Description)
                        StatementToneChip("Acepta contraseña", "gray", Icons.Filled.Lock)
                    }
                    if (state.premiumLocked) Button(onClick = onPlans, shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6E2A8C), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Filled.WorkspacePremium, null, Modifier.size(20.dp)); Spacer(Modifier.width(9.dp))
                        Text("Ver Premium", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    } else Button(onClick = onPick, enabled = !state.busy, shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("statement_pick_pdf")) {
                        Icon(Icons.Filled.FileUpload, null, Modifier.size(20.dp)); Spacer(Modifier.width(9.dp))
                        Text("Elegir un PDF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (state.pending.isNotEmpty()) {
                Text("PARA RETOMAR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp, color = palette.secondaryMuted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 0.dp))
                state.pending.forEach { statement -> PendingStatementCard(statement, enabled = !state.busy, onResume = { onResume(statement.id) }) }
            }
            Spacer(Modifier.weight(1f, fill = false))
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VerifiedUser, null, Modifier.size(16.dp), tint = palette.secondaryMuted)
                Spacer(Modifier.width(8.dp))
                Text("Tus archivos no se suben ni se guardan.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
            }
        }
    }

    if (state.showConsent) StatementConsentSheet(onAccept = onAcceptConsent, onCancel = onCancelConsent)
    if (state.showPassword) StatementPasswordDialog(state, onPassword, onSubmitPassword, onCancelPassword)
    if (state.lockedOut) StatementAnswerSheet(Icons.Filled.Lock, "bad", "Demasiados intentos",
        "Por seguridad cerramos el archivo. Vuelve a elegirlo e inténtalo con calma.", "Elegir el PDF otra vez",
        onPrimary = { onDismissNotice(); onPick() }, onDismiss = onDismissNotice, tag = "statement_locked",
        extra = { StatementFileChip(state.fileName, state.fileSize) })
    state.notice?.let { notice -> StatementNoticeSheet(notice, state, onPick = { onDismissNotice(); onPick() }, onDismiss = onDismissNotice) }
    if (state.showPremium) StatementPremiumSheet(onDismiss = onDismissPremium, onPlans = { onDismissPremium(); onPlans() })
    if (state.reading || (state.scan != null && state.openImportId != null)) StatementScanOverlay(state.scan, onFinished = onScanFinished)
}

private fun fileDetails(context: android.content.Context, uri: Uri): Pair<String?, Long?>? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val size = cursor.getColumnIndex(OpenableColumns.SIZE)
        (if (name >= 0) cursor.getString(name) else null) to (if (size >= 0 && !cursor.isNull(size)) cursor.getLong(size) else null)
    }
}.getOrNull()

@Composable
private fun OptionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: String, title: String, badge: Pair<String, String>) {
    val palette = rememberCalmEmeraldColors()
    val t = statementTone(tone)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(t.bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(26.dp), tint = t.fg) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
            StatementToneChip(badge.first, badge.second, if (badge.second == "violet") Icons.Filled.WorkspacePremium else null)
        }
    }
}

@Composable
private fun ImportOption(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, tile: String, title: String, badge: Pair<String, String>, body: String,
    modifier: Modifier = Modifier) {
    val palette = rememberCalmEmeraldColors()
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = palette.surfaceCard, border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle),
        modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { OptionHeader(icon, tile, title, badge) }
                Icon(Icons.Filled.ChevronRight, null, tint = palette.secondaryMuted)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
        }
    }
}

@Composable
private fun PendingStatementCard(statement: StatementImport, enabled: Boolean, onResume: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    Surface(shape = RoundedCornerShape(22.dp), color = palette.surfaceCard, border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth().testTag("statement_pending_${statement.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatementBankTile(statement.issuer, size = 46.dp)
                Column(Modifier.weight(1f)) {
                    Text("${statement.issuer} •••• ${statement.cardLastFour}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                    Text("Ciclo ${statementShortDay(statement.cycleStart)} – ${statementShortDay(statement.cycleEnd)}", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatementToneChip("Sin registrar", "warn", Icons.Filled.AccessTime)
            }
            Button(onClick = onResume, enabled = enabled, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(19.dp)); Spacer(Modifier.width(9.dp))
                Text("Retomar revisión", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementConsentSheet(onAccept: () -> Unit, onCancel: () -> Unit) {
    StatementAnswerSheet(Icons.Filled.VerifiedUser, "teal", "Lectura privada en tu teléfono",
        "El PDF y su contraseña se procesan solo en este teléfono: no se guardan ni se envían. Solo se guardan y sincronizan los movimientos que confirmes.",
        "Entendido, elegir PDF", onPrimary = onAccept, onDismiss = onCancel, tag = "statement_consent")
}

/** Password prompt: names the file, counts attempts and never keeps the secret anywhere but the field. */
@Composable
private fun StatementPasswordDialog(state: StatementImportUiState, onPassword: (String) -> Unit, onSubmit: () -> Unit, onCancel: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    var visible by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = palette.surfaceCard, shadowElevation = 20.dp,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("statement_password_dialog")) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(palette.tealTint()), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(24.dp), tint = palette.primaryDeep)
                    }
                    Text("Este PDF tiene contraseña", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                }
                Text("Escríbela para abrirlo en este teléfono. No la guardamos.", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted)
                StatementFileChip(state.fileName, state.fileSize)
                OutlinedTextField(value = state.password, onValueChange = onPassword, singleLine = true, label = { Text("Contraseña") },
                    modifier = Modifier.fillMaxWidth().testTag("statement_password"), shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Filled.Lock, null, Modifier.size(20.dp)) },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    isError = state.wrongPassword,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = palette.primaryDeep),
                    supportingText = {
                        if (state.wrongPassword) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.WarningAmber, null, Modifier.size(14.dp))
                            Text("Contraseña incorrecta. Te quedan ${state.attemptsLeft} ${if (state.attemptsLeft == 1) "intento" else "intentos"}.")
                        } else Text("Si no la recuerdas, mira el correo con el que el banco te envió el estado.")
                    },
                    trailingIcon = { IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Ocultar contraseña" else "Mostrar contraseña")
                    } })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", fontWeight = FontWeight.Bold, color = palette.secondaryMuted) }
                    Button(onClick = onSubmit, enabled = state.password.isNotEmpty(), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("statement_password_open")) { Text("Abrir", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun StatementNoticeSheet(notice: StatementImportNotice, state: StatementImportUiState, onPick: () -> Unit, onDismiss: () -> Unit) {
    val file: @Composable () -> Unit = { StatementFileChip(state.fileName, state.fileSize) }
    when (notice) {
        StatementImportNotice.NOT_STATEMENT -> StatementAnswerSheet(Icons.Filled.Description, "warn", "No parece un estado de cuenta",
            "Solo leemos los estados de tarjeta que te envía el banco en PDF. El archivo no se guardó.", "Elegir otro PDF", onPrimary = onPick, onDismiss = onDismiss,
            tag = "statement_notice_not_statement", extra = file)
        StatementImportNotice.UNSUPPORTED_BANK -> StatementAnswerSheet(Icons.Filled.Description, "gray", "Aún no leemos este banco",
            "Detectamos un estado de cuenta, pero de un banco que Kipu todavía no reconoce. Por ahora admitimos estados BCP Visa.", "Elegir otro PDF", onPrimary = onPick,
            onDismiss = onDismiss, tag = "statement_notice_unsupported", extra = file)
        StatementImportNotice.UNREADABLE -> StatementAnswerSheet(Icons.Filled.WarningAmber, "warn", "No pudimos leer el PDF",
            "Parece una imagen escaneada o no tiene texto. Para que se lea bien usa el PDF original que descargas del banco, completo y sin fotos.", "Elegir otro PDF",
            onPrimary = onPick, onDismiss = onDismiss, tag = "statement_notice_unreadable", extra = file)
        StatementImportNotice.ALREADY_IMPORTED -> StatementAnswerSheet(Icons.Filled.AccessTime, "teal", "Este estado ya lo importaste",
            "Para no duplicar compras, no lo agregamos otra vez. Revisa tus movimientos o elige otro estado.", "Elegir otro PDF", onPrimary = onPick, onDismiss = onDismiss,
            dismissLabel = "Cerrar", tag = "statement_notice_already", extra = file)
    }
}

@Composable
internal fun StatementPremiumSheet(onDismiss: () -> Unit, onPlans: () -> Unit) {
    val palette = rememberCalmEmeraldColors()
    val violet = statementTone("violet")
    StatementAnswerSheet(Icons.Filled.WorkspacePremium, "violet", "Importa estados de cuenta con Premium",
        "Carga compras anteriores o una tarjeta nueva en segundos, sin escribir cada movimiento.", "Ver planes", onPrimary = onPlans, onDismiss = onDismiss,
        dismissLabel = "Ahora no", tag = "statement_premium") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Trae un ciclo completo de una vez", "Funciona con PDF con contraseña", "Detecta intereses, seguros y devoluciones").forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).background(violet.bg, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = violet.fg)
                    }
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = palette.primaryText)
                }
            }
        }
    }
}
