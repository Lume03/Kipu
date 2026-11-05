package com.kipu.app.feature.widgets.ui

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.kipu.app.feature.movements.presentation.resolveCatalogIcon
import com.kipu.app.feature.widgets.data.DueRow
import com.kipu.app.ui.component.KipuBrand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The icon the person picked for a budget or category, from the same catalog the app screens use. */
internal fun budgetIcon(iconId: String?): ImageVector = iconId?.let(::resolveCatalogIcon) ?: Icons.Rounded.PieChart

internal enum class Level { OK, WARN, OVER }

internal fun levelOf(percent: Int) = when {
    percent >= 100 -> Level.OVER
    percent >= 80 -> Level.WARN
    else -> Level.OK
}

internal fun Level.fill() = when (this) { Level.OK -> WColors.brand; Level.WARN -> WColors.warn; Level.OVER -> WColors.danger }
internal fun Level.ink() = when (this) { Level.OK -> WColors.brandInk; Level.WARN -> WColors.warn; Level.OVER -> WColors.danger }
internal fun Level.bitmapColor() = when (this) { Level.OK -> WColors.BM_BRAND; Level.WARN -> WColors.BM_AMBER; Level.OVER -> 0xFFE5484D.toInt() }

internal fun softOf(color: Int, alpha: Int = 0x33): Int = (alpha shl 24) or (color and 0x00FFFFFF)

/** Tinted tile with a vector icon: mid-tone glyph over a translucent version of the same color. */
internal fun tile(vector: ImageVector, sizePx: Int, color: Int): Bitmap = WidgetBitmaps.iconBadge(vector, sizePx, color, softOf(color))

/** Bank or wallet logo when Kipu bundles one; otherwise the payee's initials. */
@Composable
internal fun payeeBitmap(brand: KipuBrand?, title: String, sizePx: Int): Bitmap {
    val context = LocalContext.current
    val logo = brand?.logoRes
    if (brand != null && logo != null) return WidgetBitmaps.logo(context, logo, sizePx, brand.fullBleed)
    val initials = title.split(" ", "·", "-").filter { it.isNotBlank() && it.first().isLetterOrDigit() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "•" }
    return WidgetBitmaps.monogram(initials, sizePx, softOf(WColors.BM_BLUE), WColors.BM_BLUE)
}

private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale("es", "PE"))

internal fun whenText(millis: Long?, zone: ZoneId, today: LocalDate): String {
    if (millis == null) return ""
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val day = at.toLocalDate()
    return when (day) {
        today -> "hoy ${clock.format(at)}"
        today.minusDays(1) -> "ayer ${clock.format(at)}"
        else -> day.dayMonthText()
    }
}

/** A payment as one line: logo, what it is and when, and the amount. */
@Composable
internal fun DueLine(due: DueRow, today: LocalDate, hide: Boolean, logoDp: Dp = 36.dp, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val target = if (due.isCard) "card/${due.destinationId}" else "debt/${due.destinationId}"
    Row(modifier.fillMaxWidth().clickable(WidgetLinks.open(context, target)), verticalAlignment = Alignment.CenterVertically) {
        Pic(payeeBitmap(due.brand, due.title, px(logoDp.value.toInt())), logoDp)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Strong(due.title, 13f, maxLines = 1)
            Text3(due, today)
        }
        Spacer(GlanceModifier.width(6.dp))
        Strong(due.amount.text(hide, decimals = true), 13f, if (due.overdue) WColors.danger else WColors.ink, maxLines = 1)
    }
}

@Composable
private fun Text3(due: DueRow, today: LocalDate) {
    val text = "${due.date.short()} · ${daysUntil(due.date, today)}"
    if (due.overdue) Strong(text, 11.5f, WColors.danger, maxLines = 1) else Muted(text, 11.5f)
}

/** Friendly empty state: what the widget will show and how to get there. */
@Composable
internal fun EmptyCard(title: String, body: String, target: String, cta: String) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Strong(title, 16f, maxLines = 2)
        Spacer(GlanceModifier.height(4.dp))
        Muted(body, 12f, maxLines = 3)
        Spacer(GlanceModifier.height(10.dp))
        ActionChip(cta, WidgetLinks.open(context, target))
    }
}

@Composable
internal fun CenteredNumber(ring: Bitmap, size: Dp, top: String, bottom: String? = null, topSize: Float = 22f) {
    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.size(size)) {
        Pic(ring, size)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Strong(top, topSize, maxLines = 1)
            if (bottom != null) Muted(bottom, 10.5f)
        }
    }
}
