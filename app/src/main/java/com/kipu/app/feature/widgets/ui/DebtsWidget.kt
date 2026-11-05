package com.kipu.app.feature.widgets.ui

import android.content.Context
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider

/** Deudas: what you owe and what others owe you, side by side. */
class DebtsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "debts") { DebtsContent(it) } }
    }
}

class DebtsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DebtsWidget()
}

@Composable
private fun DebtsContent(d: WidgetData) {
    val hide = d.hideAmounts
    val next = d.dues.firstOrNull { !it.isCard }
    if (d.owed.minorUnits == 0L && d.receivable.minorUnits == 0L) {
        EmptyCard("Sin deudas activas", "Anota lo que debes o te deben y no lo pierdas de vista.", "debts", "Agregar")
        return
    }
    when (currentFit()) {
        Fit.COMPACT, Fit.NARROW_TALL -> Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            Label("Deudas")
            Spacer(GlanceModifier.defaultWeight())
            Muted("Debo", 12f)
            Strong(d.owed.text(hide), 21f)
            if (next != null) Muted("${next.title.take(16)} · ${next.date.dayMonthText()}", 11f)
            Spacer(GlanceModifier.defaultWeight())
            Muted("Me deben", 12f)
            Strong(d.receivable.text(hide), 19f, WColors.brandInk)
        }
        else -> Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
            Label("Deudas")
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                Block("Debo", d.owed.text(hide), next?.let { "${it.title.take(20)} · ${daysUntil(it.date, d.today)}" } ?: "Sin cuotas próximas", WColors.ink, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(10.dp))
                Block("Me deben", d.receivable.text(hide), "por cobrar", WColors.brandInk, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun Block(label: String, value: String, note: String, color: androidx.glance.unit.ColorProvider, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(modifier.background(WColors.tile).cornerRadius(18.dp).padding(12.dp).clickable(WidgetLinks.open(context, "debts"))) {
        Muted(label, 12f)
        Spacer(GlanceModifier.height(2.dp))
        Strong(value, 22f, color)
        Spacer(GlanceModifier.height(2.dp))
        Muted(note, 11f, maxLines = 2)
    }
}
