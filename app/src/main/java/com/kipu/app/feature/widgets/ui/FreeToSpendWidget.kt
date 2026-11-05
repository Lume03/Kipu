package com.kipu.app.feature.widgets.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider

/** «Libre para gastar»: what can be spent today without touching goals or the payments that are coming. */
class FreeToSpendWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "money") { FreeToSpendContent(it) } }
    }
}

class FreeToSpendReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FreeToSpendWidget()
}

@Composable
private fun FreeToSpendContent(d: WidgetData) {
    val context = LocalContext.current
    val fit = currentFit()
    val hide = d.hideAmounts
    val daysLeft = d.today.lengthOfMonth() - d.today.dayOfMonth + 1
    val perDay = Money(d.free.minorUnits / daysLeft, d.free.currency)
    val parts = listOf(
        d.free.minorUnits.toFloat() to WColors.brand,
        d.inGoals.minorUnits.toFloat() to WColors.blue,
        d.upcoming.minorUnits.toFloat() to WColors.warn,
    )
    val add = glyph(Icons.Rounded.Add, px(56))
    val register = WidgetLinks.open(context, "register")

    when (fit) {
        Fit.COMPACT, Fit.NARROW_TALL -> Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Dot(WColors.brand); Spacer(GlanceModifier.width(6.dp)); Label("Libre") }
            Spacer(GlanceModifier.height(4.dp))
            Strong(d.free.text(hide), 25f, maxLines = 1)
            Muted("≈ ${perDay.text(hide)} al día", 12f)
            Spacer(GlanceModifier.defaultWeight())
            StackedBar(parts, 7.dp)
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Muted("Quedan $daysLeft días", 11f, GlanceModifier.defaultWeight())
                RoundAction(add, register, "Registrar un gasto", 40.dp)
            }
        }

        Fit.WIDE -> Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Dot(WColors.brand); Spacer(GlanceModifier.width(6.dp)); Label("Libre para gastar") }
                    Spacer(GlanceModifier.height(2.dp))
                    Strong(d.free.text(hide, decimals = true), 34f)
                    Muted("≈ ${perDay.text(hide)} al día · quedan $daysLeft días del mes", 12f)
                }
                RoundAction(add, register, "Registrar un gasto", 44.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            StackedBar(parts, 10.dp)
            Spacer(GlanceModifier.height(9.dp))
            Legend(d, hide)
        }

        Fit.TALL, Fit.LARGE -> Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Dot(WColors.brand); Spacer(GlanceModifier.width(6.dp)); Label("Libre para gastar") }
                    Spacer(GlanceModifier.height(2.dp))
                    Strong(d.free.text(hide, decimals = true), 38f)
                    Muted("≈ ${perDay.text(hide)} al día · ${d.today.short()}", 12f)
                }
                RoundAction(add, register, "Registrar un gasto", 44.dp)
            }
            Spacer(GlanceModifier.height(12.dp))
            StackedBar(parts, 10.dp)
            Spacer(GlanceModifier.height(9.dp))
            Legend(d, hide)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                SummaryTile(
                    modifier = GlanceModifier.defaultWeight(), target = "analysis", label = "Gastado",
                    value = d.pace?.spent?.text(hide) ?: "—",
                    note = d.pace?.let { pace ->
                        val ideal = pace.idealToday
                        when {
                            ideal == null -> "este mes"
                            pace.spent.minorUnits <= ideal.minorUnits -> "${Money(ideal.minorUnits - pace.spent.minorUnits, ideal.currency).text(hide)} bajo tu ritmo"
                            else -> "${Money(pace.spent.minorUnits - ideal.minorUnits, ideal.currency).text(hide)} sobre tu ritmo"
                        }
                    } ?: "este mes",
                    warn = d.pace?.let { p -> p.idealToday?.let { p.spent.minorUnits > it.minorUnits } } == true,
                )
                Spacer(GlanceModifier.width(10.dp))
                val due = d.dues.firstOrNull()
                SummaryTile(
                    modifier = GlanceModifier.defaultWeight(), target = "dues", label = "Próximo pago",
                    value = due?.amount?.text(hide) ?: "Sin pagos",
                    note = due?.let { "${it.title.take(18)} · ${daysUntil(it.date, d.today)}" } ?: "nada por vencer",
                    warn = due?.overdue == true,
                )
            }
        }
    }
}

@Composable
private fun Legend(d: WidgetData, hide: Boolean) {
    Row(GlanceModifier.fillMaxWidth()) {
        LegendItem(WColors.brand, "Libre", d.free.text(hide), GlanceModifier.defaultWeight())
        LegendItem(WColors.blue, "En metas", d.inGoals.text(hide), GlanceModifier.defaultWeight())
        LegendItem(WColors.warn, "Pagos", d.upcoming.text(hide), GlanceModifier.defaultWeight())
    }
}

@Composable
private fun LegendItem(color: androidx.glance.unit.ColorProvider, label: String, value: String, modifier: GlanceModifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) { Dot(color, 7.dp); Spacer(GlanceModifier.width(5.dp)); Muted(label, 11f) }
        Strong(value, 13f)
    }
}

@Composable
internal fun SummaryTile(modifier: GlanceModifier, target: String, label: String, value: String, note: String, warn: Boolean = false) {
    val context = LocalContext.current
    Column(
        modifier = modifier.background(WColors.tile).cornerRadius(18.dp).padding(12.dp)
            .clickable(WidgetLinks.open(context, target)),
    ) {
        Label(label, WColors.muted)
        Spacer(GlanceModifier.height(3.dp))
        Strong(value, 18f)
        Spacer(GlanceModifier.height(2.dp))
        Text2(note, warn)
    }
}

@Composable
private fun Text2(note: String, warn: Boolean) {
    if (warn) Strong(note, 11.5f, WColors.warn, maxLines = 1) else Strong(note, 11.5f, WColors.brandInk, maxLines = 1)
}
