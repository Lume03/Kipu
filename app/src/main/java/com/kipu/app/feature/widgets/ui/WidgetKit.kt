package com.kipu.app.feature.widgets.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as FixedColor
import com.kipu.app.MainActivity
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.data.WidgetState
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Kipu palette for widgets. Text and surfaces follow the system day/night; bitmaps use mid-tones that read on both. */
internal object WColors {
    private fun c(day: Long, night: Long) = ColorProvider(day = Color(day), night = Color(night))

    val surface = c(0xFFFFFFFF, 0xFF182225)
    val tile = c(0xFFF0F5F4, 0xFF223034)
    val track = c(0xFFDDE7E5, 0xFF2F4146)
    val ink = c(0xFF0F172A, 0xFFEEF5F4)
    val muted = c(0xFF475569, 0xFFA9BAB8)
    val brand = c(0xFF0F766E, 0xFF4FD1C0)
    val onBrand = c(0xFFFFFFFF, 0xFF04201D)
    val brandSoft = c(0xFFDAF1EC, 0xFF1B3F3B)
    val brandInk = c(0xFF0B5F58, 0xFF8FE6D9)
    val warn = c(0xFFB45309, 0xFFF5B25B)
    val warnSoft = c(0xFFFEF3C7, 0xFF3B2D15)
    val warnInk = c(0xFF8A4B00, 0xFFFFD28A)
    val danger = c(0xFFBA1A1A, 0xFFFF9A92)
    val dangerSoft = c(0xFFFFDAD6, 0xFF4A2321)
    val blue = c(0xFF2F5FC4, 0xFF86AAF5)
    val blueSoft = c(0xFFDBEAFE, 0xFF1F3252)
    val blueInk = c(0xFF1E4FA8, 0xFF9DBFFA)

    // Mid-tone ARGB for bitmaps
    const val BM_BRAND = 0xFF14A394.toInt()
    const val BM_BRAND_SOFT = 0x3314A394
    const val BM_BLUE = 0xFF4C7BE0.toInt()
    const val BM_AMBER = 0xFFE08A12.toInt()
    const val BM_SLATE = 0xFF7C8B99.toInt()
    const val BM_TRACK = 0x33808F99
    const val BM_GRID = 0x33808F99
}

internal fun fixed(color: Color) = FixedColor(color)

/** Widget "size class": the Responsive size Glance picked for this instance. */
internal enum class Fit { COMPACT, WIDE, TALL, LARGE, NARROW_TALL }

internal val widgetSizes: Set<DpSize> = setOf(
    DpSize(100.dp, 100.dp), DpSize(100.dp, 220.dp), DpSize(220.dp, 100.dp), DpSize(220.dp, 200.dp), DpSize(220.dp, 300.dp),
)

@Composable
internal fun currentFit(): Fit {
    val size = LocalSize.current
    return when {
        size.width < 200.dp && size.height >= 200.dp -> Fit.NARROW_TALL
        size.width < 200.dp -> Fit.COMPACT
        size.height >= 290.dp -> Fit.LARGE
        size.height >= 190.dp -> Fit.TALL
        else -> Fit.WIDE
    }
}

internal object WidgetLinks {
    fun open(context: Context, target: String): Action = actionStartActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("kipu://widget/$target"))
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
}

// ---- formatting ------------------------------------------------------------------------------------------------

internal fun Money.text(hide: Boolean, decimals: Boolean = false): String {
    if (hide) return "${currency.symbol()} ••••"
    val units = formatMinorUnits(kotlin.math.abs(minorUnits))
    val body = if (decimals) units else units.substringBefore('.')
    return (if (minorUnits < 0) "−" else "") + currency.symbol() + " " + body
}

internal fun Money.signed(hide: Boolean): String =
    if (hide) "${currency.symbol()} ••••" else (if (minorUnits < 0) "−" else "+") + text(false)

private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "PE"))
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale("es", "PE"))

internal fun LocalDate.short(): String = shortDate.format(this).replace(".", "")
internal fun LocalDate.dayMonthText(): String = dayMonth.format(this).replace(".", "")

internal fun daysUntil(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return when {
        days < 0 -> "venció hace ${-days} d"
        days == 0 -> "hoy"
        days == 1 -> "mañana"
        else -> "en $days días"
    }
}

// ---- atoms -----------------------------------------------------------------------------------------------------

@Composable
internal fun WidgetFrame(state: WidgetState, target: String, content: @Composable (WidgetData) -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(WColors.surface).cornerRadius(24.dp)
            .clickable(WidgetLinks.open(context, target)),
    ) {
        when (state) {
            is WidgetState.Ready -> content(state.data)
            WidgetState.SignedOut -> Notice("Abre Kipu", "Inicia sesión para ver tu dinero aquí.")
            WidgetState.Unavailable -> Notice("No pudimos leer tus datos", "Toca para abrir Kipu.")
        }
    }
}

@Composable
private fun Notice(title: String, body: String) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TextStyle(color = WColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
        Spacer(GlanceModifier.height(4.dp))
        Text(body, style = TextStyle(color = WColors.muted, fontSize = 12.sp), maxLines = 3)
    }
}

@Composable
internal fun Label(text: String, color: androidx.glance.unit.ColorProvider = WColors.brandInk, modifier: GlanceModifier = GlanceModifier) {
    Text(text.uppercase(Locale("es", "PE")), modifier = modifier, style = TextStyle(color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

@Composable
internal fun Muted(text: String, size: Float = 12f, modifier: GlanceModifier = GlanceModifier, maxLines: Int = 1) {
    Text(text, modifier = modifier, style = TextStyle(color = WColors.muted, fontSize = size.sp), maxLines = maxLines)
}

@Composable
internal fun Strong(text: String, size: Float = 14f, color: androidx.glance.unit.ColorProvider = WColors.ink, modifier: GlanceModifier = GlanceModifier, maxLines: Int = 1, align: TextAlign? = null) {
    Text(text, modifier = modifier, style = TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Bold, textAlign = align), maxLines = maxLines)
}

@Composable
internal fun Pill(text: String, background: androidx.glance.unit.ColorProvider, foreground: androidx.glance.unit.ColorProvider, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier = modifier.background(background).cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = TextStyle(color = foreground, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
internal fun Dot(color: androidx.glance.unit.ColorProvider, size: Dp = 8.dp) {
    Box(modifier = GlanceModifier.size(size).background(color).cornerRadius(size / 2)) {}
}

@Composable
internal fun Pic(bitmap: Bitmap, size: Dp, modifier: GlanceModifier = GlanceModifier, description: String? = null, tint: androidx.glance.unit.ColorProvider? = null) {
    Image(
        provider = ImageProvider(bitmap), contentDescription = description, modifier = modifier.size(size), contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

@Composable
internal fun BitmapFill(bitmap: Bitmap, modifier: GlanceModifier, description: String? = null) {
    Image(provider = ImageProvider(bitmap), contentDescription = description, modifier = modifier, contentScale = ContentScale.Fit)
}

private const val SEGMENTS = 20

/** Largest-remainder split of [SEGMENTS] segments so the bar always has exactly the same number of pieces. */
private fun allocate(weights: List<Float>): List<Int> {
    val total = weights.sum()
    if (total <= 0f) return weights.map { 0 }
    val raw = weights.map { it / total * SEGMENTS }
    val counts = raw.map { it.toInt() }.toMutableList()
    var rest = SEGMENTS - counts.sum()
    raw.withIndex().sortedByDescending { it.value - it.value.toInt() }.forEach { (i, _) ->
        if (rest > 0 && weights[i] > 0f) { counts[i]++; rest-- }
    }
    return counts
}

/** Segmented bar: widgets cannot weight children unevenly, so proportions become 20 equal rounded segments. */
@Composable
internal fun StackedBar(parts: List<Pair<Float, androidx.glance.unit.ColorProvider>>, height: Dp = 9.dp) {
    val counts = allocate(parts.map { it.first })
    val cells = buildList { parts.forEachIndexed { i, (_, color) -> repeat(counts[i]) { add(color) } } }
    Row(GlanceModifier.fillMaxWidth().height(height)) {
        if (cells.isEmpty()) {
            Box(GlanceModifier.defaultWeight().fillMaxHeight().background(WColors.track).cornerRadius(height / 2)) {}
        } else {
            cells.forEachIndexed { i, color ->
                if (i > 0) Spacer(GlanceModifier.width(2.dp))
                Box(GlanceModifier.defaultWeight().fillMaxHeight().background(color).cornerRadius(height / 3)) {}
            }
        }
    }
}

@Composable
internal fun ProgressBar(percent: Int, fill: androidx.glance.unit.ColorProvider, height: Dp = 8.dp) {
    val filled = Math.round(percent.coerceIn(0, 100) * SEGMENTS / 100f).coerceIn(if (percent > 0) 1 else 0, SEGMENTS)
    Row(GlanceModifier.fillMaxWidth().height(height)) {
        repeat(SEGMENTS) { i ->
            if (i > 0) Spacer(GlanceModifier.width(2.dp))
            Box(GlanceModifier.defaultWeight().fillMaxHeight().background(if (i < filled) fill else WColors.track).cornerRadius(height / 3)) {}
        }
    }
}

@Composable
internal fun ActionChip(text: String, action: Action, filled: Boolean = true, icon: Bitmap? = null) {
    val bg = if (filled) WColors.brand else WColors.tile
    val fg = if (filled) WColors.onBrand else WColors.ink
    Box(
        modifier = GlanceModifier.background(bg).cornerRadius(20.dp).clickable(action)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Pic(icon, 14.dp); Spacer(GlanceModifier.width(5.dp)) }
            Text(text, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

/** Round brand button; [glyph] is a plain mask tinted to read on the brand color in either theme. */
@Composable
internal fun RoundAction(glyph: Bitmap, action: Action, description: String, size: Dp = 40.dp) {
    Box(
        modifier = GlanceModifier.size(size).background(WColors.brand).cornerRadius(size / 2).clickable(action),
        contentAlignment = Alignment.Center,
    ) { Pic(glyph, size * 0.55f, description = description, tint = WColors.onBrand) }
}

@Composable
internal fun px(dp: Int): Int = (dp * LocalContext.current.resources.displayMetrics.density).toInt()

internal fun glyph(vector: ImageVector, sizePx: Int): Bitmap = WidgetBitmaps.glyph(vector, sizePx)

internal fun vectorBitmap(vector: ImageVector, sizePx: Int, tint: Int, bg: Int = 0, circle: Boolean = false): Bitmap =
    WidgetBitmaps.iconBadge(vector, sizePx, tint, bg, circle)
