package com.kipu.app.feature.statements.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Sell
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.categories.presentation.parseHexColor
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.statements.domain.StatementImportLine
import com.kipu.app.feature.statements.domain.StatementLineKind
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val Money = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

/** 1,234.50 — grouped so the long statement totals stay readable. */
internal fun statementPlainAmount(minor: Long): String = Money.format(BigDecimal.valueOf(minor, 2))

internal fun statementSymbol(currency: Currency) = if (currency == Currency.PEN) "S/ " else "US$ "

/** Net of the given lines in one currency: refunds and card payments lower the total. */
internal fun statementCurrencyTotal(rows: List<StatementImportLine>, currency: Currency): String {
    val net = rows.fold(BigDecimal.ZERO) { sum, line ->
        sum + BigDecimal.valueOf(line.amountMinor).let { if (line.isCredit()) -it else it }
    }
    val value = net.movePointLeft(2)
    return (if (value.signum() < 0) "−" else "") + statementSymbol(currency) + Money.format(value.abs())
}

private val Months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
internal fun statementShortDay(date: java.time.LocalDate) = "${date.dayOfMonth} ${Months[date.monthValue - 1]}"

internal fun StatementImportLine.isCredit() = kind == StatementLineKind.REFUND || kind == StatementLineKind.CARD_PAYMENT

/** Soft tinted tile with the category icon; amber dashed when the movement still has no category. */
@Composable
internal fun StatementCategoryTile(category: CategoryOption?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val palette = rememberCalmEmeraldColors()
    val radius = size * .32f
    if (category == null) {
        val amber = if (palette.isDark) Color(0xFFFCD34D) else Color(0xFF8A4B00)
        Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(amber.copy(alpha = .12f)).drawBehind {
            drawRoundRect(amber.copy(alpha = .7f), cornerRadius = CornerRadius(radius.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Sell, null, Modifier.size(size * .5f), tint = amber) }
    } else {
        val tint = category.color?.let { runCatching { parseHexColor(it) }.getOrNull() } ?: palette.primaryDeep
        Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(resolveCategoryIcon(category.icon), null, Modifier.size(size * .5f), tint = tint)
        }
    }
}

/** Bank mark used while there is a single supported issuer; text only, never a real logo asset. */
@Composable
internal fun StatementBankTile(issuer: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * .28f)).background(Color(0xFF002A8D)), contentAlignment = Alignment.Center) {
        Text(issuer.take(3).uppercase(Locale.ROOT), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * .27f).sp)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = size * .13f).size(size * .6f, 3.dp).background(Color(0xFFFF7800), RoundedCornerShape(2.dp)))
    }
}

@Composable
internal fun StatementChip(text: String, bg: Color, fg: Color, border: Color, icon: ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, border, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon?.let { Icon(it, null, Modifier.size(12.dp), tint = fg) }
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

internal data class StatementTone(val bg: Color, val fg: Color, val border: Color)

@Composable
internal fun statementTone(name: String): StatementTone {
    val dark = rememberCalmEmeraldColors().isDark
    return when (name) {
        "ok" -> StatementTone(if (dark) Color(0xFF0A301D) else Color(0xFFDCFCE7), if (dark) Color(0xFF86EFAC) else Color(0xFF166534), if (dark) Color(0xFF14532D) else Color(0xFFA7E3BC))
        "warn" -> StatementTone(if (dark) Color(0xFF3A2A0B) else Color(0xFFFEF3C7), if (dark) Color(0xFFFCD34D) else Color(0xFF8A4B00), if (dark) Color(0xFF6B4A12) else Color(0xFFF3D58A))
        "bad" -> StatementTone(if (dark) Color(0xFF3B1216) else Color(0xFFFDECEC), if (dark) Color(0xFFFCA5A5) else Color(0xFFB42F2F), if (dark) Color(0xFF7F1D1D) else Color(0xFFF0B4B4))
        "violet" -> StatementTone(if (dark) Color(0xFF2A1A44) else Color(0xFFF0E4F7), if (dark) Color(0xFFD8B4FE) else Color(0xFF6E2A8C), if (dark) Color(0xFF4A2A70) else Color(0xFFE0CBEE))
        "blue" -> StatementTone(if (dark) Color(0xFF11284A) else Color(0xFFE6EEFA), if (dark) Color(0xFF93C5FD) else Color(0xFF1E4E9E), if (dark) Color(0xFF1E3A8A) else Color(0xFFC5D7F2))
        "teal" -> rememberCalmEmeraldColors().let { StatementTone(it.primaryDeep.copy(alpha = .12f), it.primaryDeep, it.primaryDeep.copy(alpha = .3f)) }
        else -> rememberCalmEmeraldColors().let { StatementTone(it.pillTrack, it.secondaryMuted, it.borderSubtle) }
    }
}

@Composable
internal fun StatementToneChip(text: String, tone: String, icon: ImageVector? = null) {
    val t = statementTone(tone)
    StatementChip(text, t.bg, t.fg, t.border, icon)
}

@Composable
internal fun StatementKindChip(kind: StatementLineKind, installments: Int?) {
    val tone = when (kind) {
        StatementLineKind.REFUND, StatementLineKind.CARD_PAYMENT -> "ok"
        StatementLineKind.INTEREST -> "warn"
        StatementLineKind.INSURANCE -> "blue"
        StatementLineKind.FEE -> "violet"
        StatementLineKind.UNKNOWN -> "bad"
        else -> "gray"
    }
    StatementToneChip(kind.label(installments), tone)
}

/** Generic answer sheet: icon tile, short title, one line of body, optional extra block and two actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatementAnswerSheet(
    icon: ImageVector,
    tone: String,
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancelar",
    tag: String,
    modifier: Modifier = Modifier,
    secondary: Pair<String, () -> Unit>? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val palette = rememberCalmEmeraldColors()
    val t = statementTone(tone)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surfaceCard, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), modifier = modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(t.bg), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(32.dp), tint = t.fg)
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    color = palette.primaryText, modifier = Modifier.padding(horizontal = 8.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = palette.secondaryMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp))
                extra?.let { Spacer(Modifier.size(2.dp)); it() }
            }
            Spacer(Modifier.size(20.dp))
            Button(onClick = onPrimary, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (tone == "violet") t.fg else palette.primaryDeep,
                    contentColor = if (tone == "violet") Color.White else palette.onPrimaryDeep),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            secondary?.let { (label, action) ->
                TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(label, fontWeight = FontWeight.Bold, color = palette.primaryDeep)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(dismissLabel, fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
            }
        }
    }
}

/** The PDF the user chose: name and size, so every sheet about it says which file it is about. */
@Composable
internal fun StatementFileChip(name: String?, size: Long?) {
    val palette = rememberCalmEmeraldColors()
    val bad = statementTone("bad")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.pillTrack).padding(10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(bad.bg), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Description, null, Modifier.size(22.dp), tint = bad.fg)
        }
        Column(Modifier.weight(1f)) {
            Text(name ?: "Estado de cuenta.pdf", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, color = palette.primaryText)
            Text("PDF" + (size?.let { " · ${statementFileSize(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
        }
    }
}

internal fun statementFileSize(bytes: Long): String = if (bytes >= 1_048_576) String.format(Locale.forLanguageTag("es-PE"), "%.1f MB", bytes / 1_048_576.0)
else "${(bytes / 1024).coerceAtLeast(1)} KB"

internal val CheckIcon: ImageVector get() = Icons.Filled.Check
internal val DotShape = CircleShape
