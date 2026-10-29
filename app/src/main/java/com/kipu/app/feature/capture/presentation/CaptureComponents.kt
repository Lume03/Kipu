package com.kipu.app.feature.capture.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.feature.capture.domain.CaptureProvider
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.KipuChipTone
import com.kipu.app.ui.component.KipuStatusChip
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Official mark of the bank or wallet that sent the notification (initials only if the asset is missing). */
@Composable
internal fun CaptureProviderLogo(provider: CaptureProvider, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    BrandLogo(
        brand = KipuBrand.from(provider.displayName()),
        fallbackName = provider.displayName(),
        contentDescription = null,
        size = size,
        modifier = modifier,
    )
}

/** Confidence is said in text and repeated with signal bars, so it never relies on color alone. */
@Composable
internal fun ConfidenceChip(level: CaptureConfidenceLevel, modifier: Modifier = Modifier) {
    val palette = rememberCalmEmeraldColors()
    val (tone, filled, barColor) = when (level) {
        CaptureConfidenceLevel.HIGH -> Triple(KipuChipTone.POSITIVE, 3, palette.incomeEmerald)
        CaptureConfidenceLevel.MEDIUM -> Triple(KipuChipTone.WARNING, 2, palette.warningAmber)
        CaptureConfidenceLevel.LOW -> Triple(KipuChipTone.DANGER, 1, palette.expenseText)
    }
    KipuStatusChip(
        text = level.displayName(),
        tone = tone,
        modifier = modifier.semantics { contentDescription = level.displayName() },
        leading = { SignalBars(filled, barColor, palette.borderSubtle) },
    )
}

@Composable
private fun SignalBars(filled: Int, color: Color, track: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(6.dp, 9.dp, 12.dp).forEachIndexed { index, height ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(height)
                    .background(if (index < filled) color else track, RoundedCornerShape(2.dp)),
            )
        }
    }
}

enum class CaptureNoticeTone { INFO, WARNING, ERROR }

/** Banner for access, duplicate and save states. The optional action uses the banner's own color. */
@Composable
internal fun CaptureNotice(
    tone: CaptureNoticeTone,
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val palette = rememberCalmEmeraldColors()
    val scheme = MaterialTheme.colorScheme
    val (bg, border, fg) = when (tone) {
        CaptureNoticeTone.INFO -> Triple(palette.incomeBg, palette.incomeBorder, palette.primaryDeep)
        CaptureNoticeTone.WARNING -> Triple(palette.warningBg, palette.warningBorder, palette.warningText)
        CaptureNoticeTone.ERROR -> Triple(palette.expenseBg, palette.expenseBorder, palette.expenseText)
    }
    val icon = when (tone) {
        CaptureNoticeTone.INFO -> Icons.Outlined.Info
        CaptureNoticeTone.WARNING -> Icons.Outlined.WarningAmber
        CaptureNoticeTone.ERROR -> Icons.Outlined.ErrorOutline
    }
    Surface(shape = RoundedCornerShape(18.dp), color = bg, border = BorderStroke(1.dp, border), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg) }
                Text(body, style = MaterialTheme.typography.bodyMedium, color = if (tone == CaptureNoticeTone.INFO) palette.primaryText else fg)
                if (actionLabel != null && onAction != null) {
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onAction,
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tone == CaptureNoticeTone.ERROR) scheme.error else if (tone == CaptureNoticeTone.WARNING) palette.warningText else palette.primaryDeep,
                                contentColor = if (tone == CaptureNoticeTone.ERROR) scheme.onError else if (tone == CaptureNoticeTone.WARNING) palette.surfaceCard else palette.onPrimaryDeep,
                            ),
                        ) { Text(actionLabel, fontWeight = FontWeight.Bold) }
                        if (secondaryLabel != null && onSecondary != null) {
                            androidx.compose.material3.TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 44.dp)) {
                                Text(secondaryLabel, fontWeight = FontWeight.Bold, color = fg)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Confirmation shared by the inbox and the review screen; names what is being discarded. */
@Composable
internal fun CaptureDiscardDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    merchant: String? = null,
    amount: String? = null,
) {
    val palette = rememberCalmEmeraldColors()
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
                Surface(modifier = Modifier.size(80.dp), shape = CircleShape, color = palette.expenseBg) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = palette.expenseText, modifier = Modifier.size(36.dp))
                    }
                }
                Text("¿Descartar captura?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = palette.primaryText, textAlign = TextAlign.Center)
                Text(
                    text = buildAnnotatedString {
                        if (merchant != null) {
                            append("La captura de ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.primaryText)) { append(merchant) }
                            if (amount != null) {
                                append(" por ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.primaryText)) { append(amount) }
                            }
                            append(" saldrá de tus pendientes y se borrará el texto detectado.")
                        } else {
                            append("Saldrá de tus pendientes y se borrará el texto detectado.")
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.secondaryMuted,
                    textAlign = TextAlign.Center,
                )
                Surface(shape = CircleShape, color = palette.incomeBg, border = BorderStroke(1.dp, palette.incomeBorder)) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = palette.incomeText, modifier = Modifier.size(18.dp))
                        Text("Tus saldos y movimientos no cambian", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = palette.incomeText)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("capture_confirm_discard"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C), contentColor = Color.White),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sí, descartar", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.borderSubtle),
                ) { Text("Cancelar", fontWeight = FontWeight.SemiBold, color = palette.primaryText) }
            }
        }
    }
}
