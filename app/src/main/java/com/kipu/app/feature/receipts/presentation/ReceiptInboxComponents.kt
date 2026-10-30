package com.kipu.app.feature.receipts.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Brand teal used by the hero; intentionally the same in light and dark so the portada stays recognisable. */
internal val ReceiptHeroTop = Color(0xFF0F766E)
internal val ReceiptHeroBottom = Color(0xFF0A5650)
private val YapeViolet = Color(0xFF6E2A8C)
private val YapeVioletTint = Color(0xFFF0E4F7)

/** Soft teal tint that follows the theme (E1F4F0 in light). */
internal fun CalmEmeraldColors.tealTint(alpha: Float = .12f): Color = primaryDeep.copy(alpha = alpha)

/** Section label such as "REGISTRADOS DESDE YAPE". */
@Composable
internal fun ReceiptSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
        letterSpacing = .6.sp, color = rememberCalmEmeraldColors().secondaryMuted)
}

/** The portada of the empty state: a receipt travels to Kipu, one clear button teaches how. */
@Composable
internal fun ReceiptEmptyHero(onHowToShare: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(ReceiptHeroTop, Color(0xFF0B5F58), ReceiptHeroBottom)))) {
        QuipuCords(Modifier.align(Alignment.TopEnd).size(210.dp, 190.dp))
        Column(Modifier.padding(20.dp)) {
            ReceiptTravelArt(Modifier.fillMaxWidth().height(132.dp))
            Text("Pasa tus Yapes a Kipu en 3 toques", Modifier.padding(top = 16.dp), color = Color.White,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, lineHeight = 30.sp)
            Text("Comparte el comprobante desde Yape y Kipu lo lee por ti.", Modifier.padding(top = 8.dp),
                color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyLarge)
            Surface(onClick = onHowToShare, shape = RoundedCornerShape(16.dp), color = Color.White, contentColor = Color(0xFF0B5F58),
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth().heightIn(min = 54.dp).testTag("receipt_how_to_share_empty")) {
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayCircleOutline, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Ver cómo compartir", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Faint hanging cords: the quipu motif of the brand, kept very low contrast behind the copy. */
@Composable
private fun QuipuCords(modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 210f
        val cord = Color.White.copy(alpha = .16f)
        val bead = Color.White.copy(alpha = .22f)
        val stroke = Stroke(2.4f * u, cap = StrokeCap.Round)
        fun cordPath(x0: Float, c1: Offset, c2: Offset, end: Offset) = Path().apply {
            moveTo(x0 * u, 0f); cubicTo(c1.x * u, c1.y * u, c2.x * u, c2.y * u, end.x * u, end.y * u)
        }
        drawPath(cordPath(38f, Offset(38f, 42f), Offset(60f, 62f), Offset(54f, 118f)), cord, style = stroke)
        drawPath(cordPath(92f, Offset(92f, 34f), Offset(80f, 74f), Offset(98f, 126f)), cord, style = stroke)
        drawPath(cordPath(146f, Offset(146f, 52f), Offset(166f, 74f), Offset(154f, 160f)), cord, style = stroke)
        drawPath(cordPath(190f, Offset(190f, 30f), Offset(178f, 62f), Offset(184f, 96f)), cord, style = stroke)
        listOf(Triple(54f, 118f, 4.5f), Triple(98f, 126f, 5f), Triple(154f, 160f, 4.5f), Triple(184f, 96f, 3.8f),
            Triple(41f, 52f, 3f), Triple(90f, 58f, 3f), Triple(149f, 64f, 3f)).forEach { (x, y, r) ->
            drawCircle(bead, r * u, Offset(x * u, y * u))
        }
    }
}

/** Tilted Yape receipt → dotted arc → Kipu logo tile. */
@Composable
private fun ReceiptTravelArt(modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val startX = 138.dp.toPx()
            val endX = size.width - 12.dp.toPx() - 84.dp.toPx() - 6.dp.toPx()
            val y = 46.dp.toPx()
            val arc = Path().apply { moveTo(startX, y); cubicTo(startX + (endX - startX) * .3f, y - 30.dp.toPx(), startX + (endX - startX) * .7f, y - 26.dp.toPx(), endX, y + 2.dp.toPx()) }
            drawPath(arc, Color.White.copy(alpha = .85f), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7.dp.toPx()))))
            val head = Path().apply { moveTo(endX - 9.dp.toPx(), y - 9.dp.toPx()); lineTo(endX + 2.dp.toPx(), y + 3.dp.toPx()); lineTo(endX - 13.dp.toPx(), y + 6.dp.toPx()) }
            drawPath(head, Color.White.copy(alpha = .9f), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Column(Modifier.offset(14.dp, 8.dp).size(110.dp, 118.dp).rotate(-6f)
            .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFF042824), spotColor = Color(0xFF042824))
            .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Yape", Modifier.clip(RoundedCornerShape(50)).background(YapeVioletTint).padding(horizontal = 9.dp, vertical = 1.dp),
                color = YapeViolet, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold)
            Box(Modifier.size(24.dp).background(YapeViolet, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = Color.White)
            }
            Text("S/ 25.00", color = Color(0xFF2A1235), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("Carlos M.", color = Color(0xFF5B4A66), fontSize = 11.sp, lineHeight = 14.sp)
        }
        Image(painterResource(R.drawable.kipu_logo), "Kipu", Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = 28.dp).size(84.dp).rotate(4f)
            .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Color(0xFF042824), spotColor = Color(0xFF042824)))
    }
}

/** Three short guarantees, as icon + two lines, instead of a green banner. */
@Composable
internal fun ReceiptTrustCard(modifier: Modifier = Modifier) {
    val palette = rememberCalmEmeraldColors()
    Row(modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(palette.surfaceCard)
        .border(1.dp, palette.borderSubtle, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Triple(Icons.Filled.Lock, "Se lee en", "tu teléfono"), Triple(Icons.Filled.CheckCircle, "Tú confirmas", "cada uno"),
            Triple(Icons.Filled.AccountBalanceWallet, "Tu saldo no", "cambia solo")).forEach { (icon, first, second) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(palette.tealTint()), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = palette.primaryDeep)
                }
                Text(first, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = palette.primaryText, textAlign = TextAlign.Center)
                Text(second, Modifier.offset(y = (-6).dp), style = MaterialTheme.typography.labelLarge, color = palette.secondaryMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun ReceiptActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean = true,
    modifier: Modifier = Modifier, dashed: Boolean = false) {
    val palette = rememberCalmEmeraldColors()
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.fillMaxWidth().heightIn(min = 68.dp).clip(shape)
        .then(if (dashed) Modifier.background(palette.tealTint(.07f)).drawBehind {
            drawRoundRect(palette.primaryDeep.copy(alpha = .45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
        } else Modifier.background(palette.surfaceCard).border(1.dp, palette.borderSubtle, shape))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(palette.tealTint()), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = palette.primaryDeep)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.primaryText)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = palette.secondaryMuted)
    }
}

@Composable
internal fun ReceiptUploadRow(onClick: () -> Unit, enabled: Boolean) =
    ReceiptActionRow(Icons.Filled.PhotoLibrary, "Subir una imagen", "Una captura o foto del comprobante", onClick, enabled,
        Modifier.testTag("receipt_upload"))

@Composable
internal fun ReceiptShareNudge(onClick: () -> Unit) =
    ReceiptActionRow(Icons.Filled.Share, "¿Otro pago por Yape?", "Toca Compartir en Yape y elige Kipu.", onClick, dashed = true,
        modifier = Modifier.testTag("receipt_share_nudge"))

/** Count pill used next to "Por revisar"; solid teal. */
@Composable
internal fun ReceiptCountBadge(count: Int) {
    val palette = rememberCalmEmeraldColors()
    Box(Modifier.defaultMinSize(minWidth = 34.dp, minHeight = 34.dp).clip(RoundedCornerShape(17.dp)).background(palette.primaryDeep).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(count.toString(), color = palette.onPrimaryDeep, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun ReceiptStateChip(text: String, tone: ChipTone, icon: ImageVector? = null) {
    val palette = rememberCalmEmeraldColors()
    val (bg, fg, border) = when (tone) {
        ChipTone.Ready -> Triple(palette.incomeBg, palette.incomeText, palette.incomeBorder)
        ChipTone.Warning -> Triple(palette.warningBg, palette.warningText, palette.warningBorder)
        ChipTone.Solid -> Triple(palette.primaryDeep, palette.onPrimaryDeep, palette.primaryDeep)
        ChipTone.Neutral -> Triple(palette.pillTrack, palette.secondaryMuted, palette.borderSubtle)
    }
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, border, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon?.let { Icon(it, null, Modifier.size(12.dp), tint = fg) }
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

internal enum class ChipTone { Ready, Warning, Solid, Neutral }

internal fun readyChipIcon(): ImageVector = Icons.Filled.Check
internal fun warningChipIcon(): ImageVector = Icons.Filled.WarningAmber
internal fun receiptTypeIcon(income: Boolean?): ImageVector = when (income) { true -> Icons.Filled.ArrowDownward; false -> Icons.Filled.NorthEast; null -> Icons.Filled.ReceiptLong }
internal fun forwardIcon(): ImageVector = Icons.AutoMirrored.Filled.ArrowForward
