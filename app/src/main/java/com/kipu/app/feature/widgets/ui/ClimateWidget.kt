package com.kipu.app.feature.widgets.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider
import java.time.format.TextStyle
import java.util.Locale

/**
 * Clima financiero: a five-day outlook of what stays available after the payments due each day. It only subtracts
 * payments that are already scheduled, so it forecasts nothing that Kipu does not know.
 */
class ClimateWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "calendar") { ClimateContent(it) } }
    }
}

class ClimateReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClimateWidget()
}

private enum class Sky { SUN, CLOUD, RAIN, STORM }

private data class Day(val label: String, val available: Money, val sky: Sky)

private fun forecast(d: WidgetData): List<Day> {
    val base = d.total.minorUnits - d.inGoals.minorUnits
    val today = d.today
    return (0..4).map { offset ->
        val date = today.plusDays(offset.toLong())
        val due = d.dues.filter { !it.date.isAfter(date) }.sumOf { it.amount.minorUnits }
        val onDay = d.dues.any { it.date == date }
        val available = base - due
        Day(
            label = if (offset == 0) "Hoy" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es", "PE")).replace(".", "").replaceFirstChar { it.uppercase() },
            available = Money(available, d.currency),
            sky = when {
                available < 0 -> Sky.STORM
                onDay -> Sky.RAIN
                offset > 0 && d.dues.any { !it.date.isAfter(date) } -> Sky.CLOUD
                else -> Sky.SUN
            },
        )
    }
}

private fun Sky.icon(): ImageVector = when (this) {
    Sky.SUN -> Icons.Rounded.WbSunny
    Sky.CLOUD -> Icons.Rounded.Cloud
    Sky.RAIN -> Icons.Rounded.Umbrella
    Sky.STORM -> Icons.Rounded.Thunderstorm
}

private fun Sky.color() = when (this) { Sky.SUN -> WColors.warn; Sky.CLOUD -> WColors.muted; Sky.RAIN -> WColors.blue; Sky.STORM -> WColors.danger }

private fun compact(money: Money, hide: Boolean): String {
    if (hide) return "••••"
    val value = money.minorUnits / 100.0
    val abs = kotlin.math.abs(value)
    val body = if (abs >= 1000) String.format(Locale.US, "%.1fk", abs / 1000.0).replace(".0k", "k") else abs.toLong().toString()
    return (if (value < 0) "−" else "") + body
}

@Composable
private fun ClimateContent(d: WidgetData) {
    val hide = d.hideAmounts
    val days = forecast(d)
    val worst = days.maxOf { it.sky.ordinal }
    val sky = Sky.entries[worst]
    val title = when (sky) { Sky.SUN -> "Despejado"; Sky.CLOUD -> "Nublado"; Sky.RAIN -> "Lluvia de pagos"; Sky.STORM -> "Tormenta" }
    val subtitle = when (sky) {
        Sky.SUN -> "Sin pagos que te muevan el saldo en 5 días"
        Sky.CLOUD, Sky.RAIN -> d.dues.firstOrNull()?.let { "${it.title.take(18)} vence ${daysUntil(it.date, d.today)}" } ?: "Hay pagos cerca"
        Sky.STORM -> "Un pago no queda cubierto con tu saldo"
    }
    val compactMode = currentFit() == Fit.COMPACT || currentFit() == Fit.NARROW_TALL
    Column(GlanceModifier.fillMaxSize().padding(if (compactMode) 14.dp else 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pic(glyph(sky.icon(), px(112)), if (compactMode) 34.dp else 44.dp, tint = sky.color())
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Label("Tu clima financiero", sky.color())
                Strong(title, if (compactMode) 17f else 22f, maxLines = 1)
                if (!compactMode) Muted(subtitle, 12f, maxLines = 1)
            }
        }
        if (compactMode) {
            Spacer(GlanceModifier.height(6.dp))
            Muted(subtitle, 11.5f, maxLines = 3)
        } else {
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth().background(WColors.tile).cornerRadius(18.dp).padding(vertical = 7.dp, horizontal = 4.dp)) {
                days.forEach { day ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Muted(day.label, 11f)
                        Spacer(GlanceModifier.height(2.dp))
                        Pic(glyph(day.sky.icon(), px(56)), 20.dp, tint = day.sky.color())
                        Spacer(GlanceModifier.height(2.dp))
                        Strong(compact(day.available, hide), 12f, if (day.available.minorUnits < 0) WColors.danger else WColors.ink)
                    }
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Muted("Disponible al cierre de cada día, tras los pagos programados.", 10.5f)
        }
    }
}
