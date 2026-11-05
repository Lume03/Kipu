package com.kipu.app.feature.widgets.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.widgets.data.PaceData
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider

/** Ritmo del mes: what has been spent against an even pace, and where the month ends if nothing changes. */
class PaceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "analysis") { PaceContent(it) } }
    }
}

class PaceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaceWidget()
}

@Composable
private fun PaceContent(d: WidgetData) {
    val pace = d.pace
    if (pace == null || (pace.spent.minorUnits == 0L && pace.reference == null)) {
        EmptyCard("Aún no hay gastos este mes", "Registra un gasto y aquí verás tu ritmo.", "register", "Registrar gasto")
        return
    }
    val hide = d.hideAmounts
    val fit = currentFit()
    val ideal = pace.idealToday
    val diff = ideal?.let { it.minorUnits - pace.spent.minorUnits }
    val under = diff == null || diff >= 0
    val headline = when {
        diff == null -> "${pace.spent.text(hide)} gastados"
        under -> "${Money(diff, d.currency).text(hide)} por debajo de tu ritmo"
        else -> "${Money(-diff, d.currency).text(hide)} por encima de tu ritmo"
    }
    val projected = pace.spent.minorUnits * pace.daysInMonth / pace.dayOfMonth
    val referenceLabel = if (pace.referenceIsBudget) "Tope del mes" else "Mes pasado"

    when (fit) {
        Fit.COMPACT, Fit.NARROW_TALL -> Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            Label("Ritmo del mes")
            Spacer(GlanceModifier.height(4.dp))
            Strong(pace.spent.text(hide), 22f)
            Muted("gastados · día ${pace.dayOfMonth} de ${pace.daysInMonth}", 11f, maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            if (pace.reference != null) {
                val percent = (pace.spent.minorUnits * 100 / pace.reference.minorUnits.coerceAtLeast(1)).toInt()
                ProgressBar(percent, if (under) WColors.brand else WColors.warn, 7.dp)
                Spacer(GlanceModifier.height(6.dp))
            }
            if (under) Strong(if (diff == null) "Sin referencia" else "Vas bajo tu ritmo", 11.5f, WColors.brandInk, maxLines = 1)
            else Strong("Vas sobre tu ritmo", 11.5f, WColors.warn, maxLines = 1)
        }

        else -> Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Ritmo del mes", modifier = GlanceModifier.defaultWeight())
                Muted("Día ${pace.dayOfMonth} de ${pace.daysInMonth}", 11.5f)
            }
            Spacer(GlanceModifier.height(3.dp))
            Strong(headline, if (fit == Fit.WIDE) 16f else 19f, if (under) WColors.ink else WColors.warn, maxLines = 2)
            Spacer(GlanceModifier.defaultWeight())
            BitmapFill(
                WidgetBitmaps.pace(px(640), px(if (fit == Fit.WIDE) 150 else 220), pace, WColors.BM_BRAND, WColors.BM_SLATE, WColors.BM_GRID, WColors.BM_AMBER),
                GlanceModifier.fillMaxWidth().height(if (fit == Fit.WIDE) 60.dp else 96.dp),
                "Gasto acumulado del mes frente a un ritmo parejo",
            )
            Spacer(GlanceModifier.defaultWeight())
            if (fit != Fit.WIDE) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat("Gastado", pace.spent.text(hide), GlanceModifier.defaultWeight())
                    Stat(referenceLabel, pace.reference?.text(hide) ?: "—", GlanceModifier.defaultWeight())
                    Stat("Cierre estimado", Money(projected, d.currency).text(hide), GlanceModifier.defaultWeight())
                }
            } else {
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat("Gastado", pace.spent.text(hide), GlanceModifier.defaultWeight())
                    Stat("Cierre estimado", Money(projected, d.currency).text(hide), GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: GlanceModifier) {
    Column(modifier) {
        Muted(label, 11f)
        Strong(value, 13.5f)
    }
}
