package com.kipu.app.feature.widgets.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.widgets.data.DueRow
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider
import java.time.temporal.ChronoUnit

/** Pagos: the next payment at a glance, or the week's payments with how much is already set aside. */
class PaymentsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "dues") { PaymentsContent(it) } }
    }
}

class PaymentsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaymentsWidget()
}

@Composable
private fun PaymentsContent(d: WidgetData) {
    val context = LocalContext.current
    val hide = d.hideAmounts
    if (d.dues.isEmpty()) {
        Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic(glyph(Icons.Rounded.CheckCircle, px(64)), 32.dp, tint = WColors.brand)
            Spacer(GlanceModifier.height(8.dp))
            Strong("Sin pagos por vencer", 16f, maxLines = 2)
            Spacer(GlanceModifier.height(2.dp))
            Muted("Tus deudas y tarjetas están al día.", 12f, maxLines = 2)
        }
        return
    }
    when (currentFit()) {
        Fit.COMPACT -> NextPaymentCompact(d, d.dues.first(), hide)
        Fit.WIDE -> Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Header(d, hide)
            Spacer(GlanceModifier.defaultWeight())
            d.dues.take(2).forEachIndexed { index, due ->
                if (index > 0) Spacer(GlanceModifier.height(10.dp))
                DueLine(due, d.today, hide)
            }
            Spacer(GlanceModifier.defaultWeight())
        }
        Fit.TALL, Fit.LARGE, Fit.NARROW_TALL -> {
            val max = if (currentFit() == Fit.LARGE) 5 else 3
            Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Header(d, hide)
                Spacer(GlanceModifier.defaultWeight())
                d.dues.take(max).forEachIndexed { index, due ->
                    if (index > 0) Spacer(GlanceModifier.height(9.dp))
                    DueLine(due, d.today, hide)
                }
                Spacer(GlanceModifier.defaultWeight())
                Coverage(d, hide)
            }
        }
    }
}

@Composable
private fun Header(d: WidgetData, hide: Boolean) {
    val week = d.dues.filter { !it.date.isAfter(d.today.plusDays(7)) }
    val total = Money(week.sumOf { it.amount.minorUnits }, d.currency)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label("Esta semana", modifier = GlanceModifier.defaultWeight())
        if (week.isNotEmpty()) Strong("${week.size} ${if (week.size == 1) "pago" else "pagos"} · ${total.text(hide)}", 12f)
        else Muted("Sin pagos en 7 días", 12f)
    }
}

/** How much of what is due this month is already set aside, so nothing surprises the balance. */
@Composable
private fun Coverage(d: WidgetData, hide: Boolean) {
    val due = Money(d.dues.sumOf { it.amount.minorUnits }, d.currency)
    val covered = if (due.minorUnits == 0L) 100 else (d.upcoming.minorUnits * 100 / due.minorUnits).toInt().coerceIn(0, 100)
    Column(GlanceModifier.fillMaxWidth().background(WColors.tile).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Muted("Apartado", 11.5f, GlanceModifier.defaultWeight())
            Strong(d.upcoming.text(hide), 12.5f)
        }
        Spacer(GlanceModifier.height(5.dp))
        ProgressBar(covered, if (covered >= 100) WColors.brand else WColors.warn, 6.dp)
        Spacer(GlanceModifier.height(4.dp))
        if (covered >= 100) Strong("Todo cubierto", 11.5f, WColors.brandInk) else Strong("Cubre el $covered%", 11.5f, WColors.warn)
    }
}

@Composable
private fun NextPaymentCompact(d: WidgetData, due: DueRow, hide: Boolean) {
    val context = LocalContext.current
    val days = ChronoUnit.DAYS.between(d.today, due.date).toInt()
    val fraction = if (due.overdue) 1f else 1f - (days.coerceIn(0, 30) / 30f)
    val ring = WidgetBitmaps.ring(px(72), px(7).toFloat(), fraction, if (due.overdue) 0xFFE5484D.toInt() else WColors.BM_AMBER, WColors.BM_TRACK)
    val covered = d.upcoming.minorUnits >= due.amount.minorUnits
    Column(
        GlanceModifier.fillMaxSize().padding(14.dp).clickable(WidgetLinks.open(context, if (due.isCard) "card/${due.destinationId}" else "debt/${due.destinationId}")),
    ) {
        Label("Próximo pago", if (due.overdue) WColors.danger else WColors.warn)
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically) {
            CenteredNumber(ring, 60.dp, if (due.overdue) "!" else "$days", if (due.overdue) "vencido" else if (days == 1) "día" else "días", 21f)
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Strong(due.amount.text(hide), 17f, maxLines = 1)
                Muted(due.title, 11.5f)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Box(GlanceModifier.fillMaxWidth().background(if (covered) WColors.brandSoft else WColors.warnSoft).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 7.dp)) {
            if (covered) Strong("Ya lo tienes apartado", 11.5f, WColors.brandInk, maxLines = 1)
            else Strong("Vence ${due.date.dayMonthText()}", 11.5f, WColors.warnInk, maxLines = 1)
        }
    }
}
