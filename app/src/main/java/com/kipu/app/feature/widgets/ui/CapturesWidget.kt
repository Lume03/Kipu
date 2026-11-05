package com.kipu.app.feature.widgets.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.layout.width
import com.kipu.app.feature.widgets.data.CaptureRow
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider
import java.time.ZoneId

/** Capturas: payments Kipu read from notifications that wait for a quick review. The balance only moves on confirm. */
class CapturesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "capture") { CapturesContent(it) } }
    }
}

class CapturesReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CapturesWidget()
}

@Composable
private fun CapturesContent(d: WidgetData) {
    val context = LocalContext.current
    val info = d.captures
    val fit = currentFit()
    if (!info.premium) {
        Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Capturas", modifier = GlanceModifier.defaultWeight())
                Pill("PREMIUM", WColors.warnSoft, WColors.warnInk)
            }
            Spacer(GlanceModifier.defaultWeight())
            Strong("Que tus pagos de Yape y Plin se registren solos", if (fit == Fit.COMPACT) 14f else 17f, maxLines = 3)
            if (fit != Fit.COMPACT) {
                Spacer(GlanceModifier.height(4.dp))
                Muted("Kipu lee la notificación en tu teléfono. Tú solo confirmas.", 12f, maxLines = 2)
            }
            Spacer(GlanceModifier.defaultWeight())
            ActionChip("Conocer Premium", WidgetLinks.open(context, "capture"), icon = glyph(Icons.Rounded.Lock, px(32)))
        }
        return
    }
    if (info.pendingCount == 0) {
        Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("Capturas")
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.background(WColors.brandSoft).cornerRadius(24.dp).padding(10.dp)) {
                    Pic(glyph(Icons.Rounded.CheckCircle, px(64)), 28.dp, tint = WColors.brandInk)
                }
                Spacer(GlanceModifier.width(12.dp))
                Column {
                    Strong("Todo al día", 19f)
                    Muted(if (info.autoToday > 0) "Kipu registró solo ${info.autoToday} ${if (info.autoToday == 1) "pago" else "pagos"} hoy." else "No tienes pagos por revisar.", 12f, maxLines = 2)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
        }
        return
    }
    when (fit) {
        Fit.COMPACT, Fit.NARROW_TALL -> Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            Label("Por revisar")
            Spacer(GlanceModifier.defaultWeight())
            Strong("${info.pendingCount}", 40f)
            Muted(if (info.pendingCount == 1) "captura" else "capturas", 12f)
            Spacer(GlanceModifier.defaultWeight())
            Muted("Tu saldo no cambia hasta confirmar", 10.5f, maxLines = 2)
        }
        else -> {
            val rows = info.pending.take(if (fit == Fit.LARGE || fit == Fit.TALL) 3 else 2)
            Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label("Por revisar", modifier = GlanceModifier.defaultWeight())
                    Box(GlanceModifier.background(WColors.brand).cornerRadius(11.dp).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Strong("${info.pendingCount}", 12f, WColors.onBrand)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                rows.forEachIndexed { index, row ->
                    if (index > 0) Spacer(GlanceModifier.height(8.dp))
                    CaptureLine(row, d)
                }
                Spacer(GlanceModifier.defaultWeight())
                ActionChip(if (info.pendingCount == 1) "Revisar" else "Revisar las ${info.pendingCount}", WidgetLinks.open(context, "capture"))
            }
        }
    }
}

@Composable
private fun CaptureLine(row: CaptureRow, d: WidgetData) {
    val context = LocalContext.current
    val title = row.party?.takeIf { it.isNotBlank() } ?: row.provider.lowercase().replaceFirstChar { it.uppercase() }
    val initials = title.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "•" }
    Row(GlanceModifier.fillMaxWidth().clickable(WidgetLinks.open(context, "capture")), verticalAlignment = Alignment.CenterVertically) {
        Pic(WidgetBitmaps.monogram(initials, px(34), softOf(WColors.BM_BLUE), WColors.BM_BLUE), 34.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Strong(title, 13f, maxLines = 1)
            Muted("${row.provider.lowercase().replaceFirstChar { it.uppercase() }} · ${whenText(row.whenMillis, ZoneId.systemDefault(), d.today)}", 11.5f)
        }
        Spacer(GlanceModifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Strong(row.amount?.text(d.hideAmounts, decimals = true) ?: "—", 13f)
            if (row.needsCategory) Strong("Falta categoría", 10.5f, WColors.warn) else Strong("Lista", 10.5f, WColors.brandInk)
        }
    }
}
