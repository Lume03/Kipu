package com.kipu.app.feature.receipts.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptImportNotice
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Edit

/** Short answer to an import that did not end in a new pending receipt; resolved without leaving the inbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptImportNoticeSheet(
    notice: ReceiptImportNotice,
    onDismiss: () -> Unit,
    onPickAnother: () -> Unit,
    onReview: () -> Unit,
    onMovement: () -> Unit,
    onRestore: () -> Unit,
    candidate: ReceiptCandidate? = null,
    fileName: String? = null,
    fileSize: Long? = null,
    busy: Boolean = false,
) {
    val palette = rememberCalmEmeraldColors()
    val duplicate = notice in listOf(ReceiptImportNotice.ALREADY_PENDING, ReceiptImportNotice.ALREADY_REGISTERED, ReceiptImportNotice.ALREADY_DISCARDED)
    val look = noticeLook(notice)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surfaceCard, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("receipt_import_notice_${notice.name}")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(look.tint(palette.isDark)), contentAlignment = Alignment.Center) {
                    Icon(look.icon, null, Modifier.size(32.dp), tint = look.ink(palette.isDark))
                }
                Text(look.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    color = palette.primaryText, modifier = Modifier.padding(horizontal = 8.dp))
                Text(look.body, style = MaterialTheme.typography.bodyLarge, color = palette.secondaryMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp))
                Spacer(Modifier.size(2.dp))
                when {
                    notice == ReceiptImportNotice.UNREADABLE -> NoticeTips()
                    duplicate && candidate != null -> NoticeReceipt(candidate, notice)
                    !fileName.isNullOrBlank() -> NoticeFile(fileName, fileSize, notice == ReceiptImportNotice.UNSUPPORTED_FORMAT)
                }
            }
            Spacer(Modifier.size(20.dp))
            Button(onClick = when (notice) {
                ReceiptImportNotice.ALREADY_PENDING -> onReview
                ReceiptImportNotice.ALREADY_REGISTERED -> onMovement
                ReceiptImportNotice.ALREADY_DISCARDED -> onRestore
                else -> onPickAnother
            }, enabled = !busy, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("receipt_notice_primary")) {
                if (!duplicate) { Icon(Icons.Filled.Image, null, Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)) }
                else if (notice == ReceiptImportNotice.ALREADY_DISCARDED) { Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)) }
                Text(look.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (duplicate) TextButton(onClick = onPickAnother, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Elegir otra imagen", fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (duplicate) "Cerrar" else "Cancelar", fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
            }
        }
    }
}

private class NoticeLook(val icon: ImageVector, val title: String, val body: String, val primary: String, private val amber: Boolean, private val green: Boolean = false, private val teal: Boolean = false) {
    fun tint(dark: Boolean): Color = when {
        green -> if (dark) Color(0xFF0A301D) else Color(0xFFDCFCE7)
        teal -> if (dark) Color(0xFF0B3B36) else Color(0xFFE1F4F0)
        amber -> if (dark) Color(0xFF3A2A0B) else Color(0xFFFEF3C7)
        else -> if (dark) Color(0xFF1F2A3D) else Color(0xFFEEF2F6)
    }
    fun ink(dark: Boolean): Color = when {
        green -> if (dark) Color(0xFF86EFAC) else Color(0xFF166534)
        teal -> if (dark) Color(0xFF5EEAD4) else Color(0xFF0F766E)
        amber -> if (dark) Color(0xFFFCD34D) else Color(0xFF8A4B00)
        else -> if (dark) Color(0xFFCBD5E1) else Color(0xFF475569)
    }
}

private fun noticeLook(notice: ReceiptImportNotice) = when (notice) {
    ReceiptImportNotice.NOT_YAPE -> NoticeLook(Icons.Filled.ReceiptLong, "No parece un comprobante de Yape",
        "Solo podemos leer los comprobantes que Yape genera al yapear o recibir un pago. La imagen no se guardó.", "Elegir otra imagen", amber = true)
    ReceiptImportNotice.UNREADABLE -> NoticeLook(Icons.Filled.WarningAmber, "No pudimos leer la imagen", "Prueba con otra. Para que se lea mejor:",
        "Elegir otra imagen", amber = true)
    ReceiptImportNotice.UNSUPPORTED_FORMAT -> NoticeLook(Icons.Filled.Description, "Ese archivo no se puede leer",
        "Sube una imagen en formato PNG, JPG o WebP.", "Elegir otra imagen", amber = false)
    ReceiptImportNotice.ALREADY_PENDING -> NoticeLook(Icons.Filled.AccessTime, "Ese comprobante ya está por revisar",
        "No lo agregamos otra vez para no duplicarlo.", "Ir al comprobante", amber = false, teal = true)
    ReceiptImportNotice.ALREADY_REGISTERED -> NoticeLook(Icons.Filled.CheckCircle, "Ese comprobante ya lo registraste",
        "Tu saldo ya lo incluye, así que no hace falta hacer nada.", "Ver movimiento", amber = false, green = true)
    ReceiptImportNotice.ALREADY_DISCARDED -> NoticeLook(Icons.Filled.DeleteOutline, "Lo descartaste antes",
        "Descartaste este comprobante hace poco. ¿Quieres agregarlo de nuevo a «Por revisar»?", "Agregar de nuevo", amber = false)
}

@Composable
private fun NoticeTips() {
    val palette = rememberCalmEmeraldColors()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.background).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Que el comprobante se vea completo", "Sin recortes, reflejos ni zoom", "Mejor una captura de pantalla que una foto").forEach { tip ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(28.dp).background(palette.tealTint(.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = palette.primaryDeep)
                }
                Text(tip, style = MaterialTheme.typography.bodyMedium, color = palette.primaryText)
            }
        }
    }
}

@Composable
private fun NoticeFile(name: String, size: Long?, document: Boolean) {
    val palette = rememberCalmEmeraldColors()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.pillTrack).padding(10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(palette.borderSubtle), contentAlignment = Alignment.Center) {
            Icon(if (document) Icons.Filled.Description else Icons.Filled.Image, null, Modifier.size(22.dp), tint = palette.secondaryMuted)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, color = palette.primaryText)
            val type = name.substringAfterLast('.', "Imagen").uppercase()
            Text(type + (size?.let { " · ${formatFileSize(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
        }
    }
}

@Composable
private fun NoticeReceipt(candidate: ReceiptCandidate, notice: ReceiptImportNotice) {
    val palette = rememberCalmEmeraldColors()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.pillTrack)) {
        ReceiptInboxRow(candidate, false, onClick = {}, registered = true, enabled = false, solid = false)
        candidate.reference?.takeIf { it.isNotBlank() }?.let {
            Text("N.º $it", Modifier.padding(start = 66.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
        }
    }
}

private fun formatFileSize(bytes: Long): String = if (bytes >= 1_048_576) {
    String.format(java.util.Locale.forLanguageTag("es-PE"), "%.1f MB", bytes / 1_048_576.0)
} else "${(bytes / 1024).coerceAtLeast(1)} KB"
