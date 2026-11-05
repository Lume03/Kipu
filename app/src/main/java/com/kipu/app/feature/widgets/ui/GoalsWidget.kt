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
import com.kipu.app.feature.goals.presentation.goalIcon
import com.kipu.app.feature.widgets.data.GoalRow
import com.kipu.app.feature.widgets.data.WidgetData
import com.kipu.app.feature.widgets.di.widgetDataProvider
import java.time.temporal.ChronoUnit

/** Meta: progress toward the goal that is closest to done, and the weeks that kept it moving. */
class GoalsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(widgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "goals") { GoalContent(it) } }
    }
}

class GoalsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalsWidget()
}

@Composable
private fun GoalContent(d: WidgetData) {
    val goal = d.goal
    if (goal == null) {
        EmptyCard("Aún no tienes metas", "Aparta dinero para lo que quieres lograr y mira cómo avanzas.", "goals", "Crear una meta")
        return
    }
    val hide = d.hideAmounts
    val context = LocalContext.current
    val open = WidgetLinks.open(context, "goal/${goal.id}")
    val icon = tile(goalIcon(goal.iconKey).image, px(40), WColors.BM_BLUE)
    when (currentFit()) {
        Fit.COMPACT, Fit.NARROW_TALL -> {
            val ring = WidgetBitmaps.ring(px(76), px(8).toFloat(), goal.percent / 100f, WColors.BM_BLUE, WColors.BM_TRACK)
            Column(GlanceModifier.fillMaxSize().padding(14.dp).clickable(open), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pic(tile(goalIcon(goal.iconKey).image, px(28), WColors.BM_BLUE), 28.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    Strong(goal.name, 13f, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.defaultWeight())
                CenteredNumber(ring, 74.dp, "${goal.percent}%", null, 17f)
                Spacer(GlanceModifier.defaultWeight())
                Muted("${goal.reserved.text(hide)} de ${goal.target.text(hide)}", 11f)
            }
        }
        else -> Column(GlanceModifier.fillMaxSize().padding(16.dp).clickable(open)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pic(icon, 38.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Strong(goal.name, 15f, maxLines = 1)
                    Muted(deadline(goal, d), 11.5f)
                }
                if (goal.streak >= 2) Pill("${goal.streak} sem.", WColors.warnSoft, WColors.warnInk)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Strong(goal.reserved.text(hide), 26f)
                Spacer(GlanceModifier.width(6.dp))
                Muted("de ${goal.target.text(hide)}", 12.5f, GlanceModifier.defaultWeight())
                Strong("${goal.percent}%", 14f, WColors.blue)
            }
            Spacer(GlanceModifier.height(8.dp))
            ProgressBar(goal.percent, WColors.blue, 8.dp)
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val remaining = Money((goal.target.minorUnits - goal.reserved.minorUnits).coerceAtLeast(0L), goal.target.currency)
                Muted(if (remaining.minorUnits == 0L) "¡Meta lograda!" else "Faltan ${remaining.text(hide)}", 11.5f, GlanceModifier.defaultWeight())
                Muted("12 últimas semanas", 11f)
            }
            Spacer(GlanceModifier.height(5.dp))
            BitmapFill(
                WidgetBitmaps.cord(px(320), px(18), goal.weeks, WColors.BM_BLUE, WColors.BM_GRID, WColors.BM_SLATE),
                GlanceModifier.fillMaxWidth().height(18.dp), "Semanas con aporte",
            )
        }
    }
}

private fun deadline(goal: GoalRow, d: WidgetData): String {
    val date = goal.targetDate ?: return "Meta de ahorro"
    val weeks = ChronoUnit.WEEKS.between(d.today, date).toInt()
    return when {
        weeks < 0 -> "Venció el ${date.dayMonthText()}"
        weeks == 0 -> "Para el ${date.dayMonthText()} · esta semana"
        else -> "Para el ${date.dayMonthText()} · $weeks ${if (weeks == 1) "semana" else "semanas"}"
    }
}
