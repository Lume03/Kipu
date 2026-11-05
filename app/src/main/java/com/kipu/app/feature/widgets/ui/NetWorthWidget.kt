package com.kipu.app.feature.widgets.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
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
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider

/** Patrimonio: net worth with how the money in your accounts moved over the last 30 days. */
class NetWorthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 50.dp), DpSize(220.dp, 50.dp), DpSize(220.dp, 100.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "money") { NetWorthContent(it) } }
    }
}

class NetWorthReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetWorthWidget()
}

@Composable
private fun NetWorthContent(d: WidgetData) {
    val hide = d.hideAmounts
    val worth = d.netWorth ?: d.total
    val series = d.balanceSeries
    val delta = if (series.size >= 2) Money(series.last() - series.first(), d.currency) else null
    val up = (delta?.minorUnits ?: 0L) >= 0L
    val compact = currentFit() == Fit.COMPACT
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Label("Patrimonio")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Strong(worth.text(hide), if (compact) 20f else 24f, maxLines = 1)
                if (!compact && delta != null && delta.minorUnits != 0L) {
                    Spacer(GlanceModifier.width(8.dp))
                    Box(GlanceModifier.background(if (up) WColors.brandSoft else WColors.warnSoft).cornerRadius(10.dp).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pic(glyph(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, px(32)), 11.dp, tint = if (up) WColors.brandInk else WColors.warnInk)
                            Spacer(GlanceModifier.width(2.dp))
                            Strong(Money(kotlin.math.abs(delta.minorUnits), d.currency).text(hide), 11f, if (up) WColors.brandInk else WColors.warnInk)
                        }
                    }
                }
            }
            if (!compact && currentFit() != Fit.WIDE) Muted("Tu dinero en 30 días", 11f)
        }
        if (!compact && series.size >= 2) {
            Pic(WidgetBitmaps.sparkline(px(220), px(80), series, if (up) WColors.BM_BRAND else WColors.BM_AMBER, softOf(if (up) WColors.BM_BRAND else WColors.BM_AMBER, 0x55)), 96.dp, description = "Tu dinero en los últimos 30 días")
        }
    }
}
