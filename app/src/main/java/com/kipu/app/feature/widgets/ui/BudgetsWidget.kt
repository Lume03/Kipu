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
import androidx.glance.appwidget.provideContent
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
import com.kipu.app.feature.widgets.data.BudgetRow
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider

/** Presupuestos: how much of each limit is used, with the icon the person chose for it. */
class BudgetsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "budgets") { BudgetsContent(it) } }
    }
}

class BudgetsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetsWidget()
}

@Composable
private fun BudgetsContent(d: WidgetData) {
    if (d.budgets.isEmpty()) {
        EmptyCard("Aún no tienes presupuestos", "Ponle un límite a lo que más gastas y míralo aquí.", "budgets", "Crear uno")
        return
    }
    val hide = d.hideAmounts
    when (currentFit()) {
        Fit.COMPACT, Fit.NARROW_TALL -> BudgetRing(d.budgets.first(), hide)
        Fit.WIDE -> BudgetList(d, d.budgets.take(2), "Presupuestos", hide, showTotal = false)
        Fit.TALL -> BudgetList(d, d.budgets.take(3), "Presupuestos", hide, showTotal = true)
        Fit.LARGE -> BudgetList(d, d.budgets.take(5), "Presupuestos", hide, showTotal = true)
    }
}

@Composable
private fun BudgetRing(budget: BudgetRow, hide: Boolean) {
    val context = LocalContext.current
    val level = levelOf(budget.percent)
    val ring = WidgetBitmaps.ring(px(88), px(8).toFloat(), budget.percent / 100f, level.bitmapColor(), WColors.BM_TRACK)
    Column(
        GlanceModifier.fillMaxSize().padding(14.dp).clickable(WidgetLinks.open(context, "budget/${budget.id}")),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pic(tile(budgetIcon(budget.iconId), px(28), level.bitmapColor()), 28.dp)
            Spacer(GlanceModifier.width(8.dp))
            Strong(budget.name, 13f, maxLines = 1, modifier = GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.defaultWeight())
        CenteredNumber(ring, 76.dp, "${budget.percent}%", null, 17f)
        Spacer(GlanceModifier.defaultWeight())
        if (budget.remaining.minorUnits >= 0) Muted("Restan ${budget.remaining.text(hide)}", 11.5f)
        else Strong("Excedido ${Money(-budget.remaining.minorUnits, budget.remaining.currency).text(hide)}", 11.5f, WColors.danger)
    }
}

@Composable
private fun BudgetList(d: WidgetData, rows: List<BudgetRow>, title: String, hide: Boolean, showTotal: Boolean) {
    val context = LocalContext.current
    val spent = Money(d.budgets.sumOf { it.consumed.minorUnits }, d.currency)
    val limit = Money(d.budgets.sumOf { it.limit.minorUnits }, d.currency)
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(title, modifier = GlanceModifier.defaultWeight())
            if (showTotal) Muted("${spent.text(hide)} de ${limit.text(hide)}", 11.5f)
        }
        Spacer(GlanceModifier.defaultWeight())
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(GlanceModifier.height(9.dp))
            BudgetLine(row, hide)
        }
        Spacer(GlanceModifier.defaultWeight())
        if (d.budgets.size > rows.size) Muted("+${d.budgets.size - rows.size} más", 11f)
    }
}

@Composable
private fun BudgetLine(row: BudgetRow, hide: Boolean) {
    val context = LocalContext.current
    val level = levelOf(row.percent)
    Column(GlanceModifier.fillMaxWidth().clickable(WidgetLinks.open(context, "budget/${row.id}"))) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pic(tile(budgetIcon(row.iconId), px(30), level.bitmapColor()), 30.dp)
            Spacer(GlanceModifier.width(9.dp))
            Column(GlanceModifier.defaultWeight()) {
                Strong(row.name, 13f, maxLines = 1)
                Muted("${row.consumed.text(hide)} de ${row.limit.text(hide)}", 11.5f)
            }
            Spacer(GlanceModifier.width(6.dp))
            when (level) {
                Level.OVER -> Pill("Excedido", WColors.dangerSoft, WColors.danger)
                Level.WARN -> Pill("${row.percent}%", WColors.warnSoft, WColors.warnInk)
                Level.OK -> Strong("${row.percent}%", 13f, WColors.brandInk)
            }
        }
        Spacer(GlanceModifier.height(5.dp))
        ProgressBar(row.percent, level.fill(), 6.dp)
    }
}
