package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.ui.component.*
import com.kipu.app.ui.theme.rememberPlanningColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun GoalMonthlyContributions(events: List<GoalEvent>, goal: Goal, reservedMinor: Long) {
    val colors = rememberPlanningColors()
    val masked = LocalBalanceMasked.current
    var scale by rememberSaveable(goal.id) { mutableStateOf(GoalChartScale.MONTH) }
    var anchorText by rememberSaveable(goal.id) { mutableStateOf(LocalDate.now().toString()) }
    var selected by rememberSaveable(goal.id, scale, anchorText) { mutableStateOf<Int?>(null) }
    val anchor = LocalDate.parse(anchorText)
    val buckets = remember(events, scale, anchor) { goalChartBuckets(events, scale, anchor) }
    val maximum = buckets.maxOf { it.minor }.coerceAtLeast(1L)
    val locale = Locale.forLanguageTag("es-PE")
    val dateFormat = DateTimeFormatter.ofPattern("d MMM", locale)
    val rangeLabel = when (scale) {
        GoalChartScale.MONTH -> anchor.year.toString()
        GoalChartScale.WEEK -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        GoalChartScale.DAY -> "${buckets.first().start.format(dateFormat)} – ${buckets.last().start.format(dateFormat)} · ${anchor.year}"
    }
    fun move(step: Long) { anchorText = when (scale) {
        GoalChartScale.MONTH -> anchor.plusYears(step)
        GoalChartScale.WEEK -> YearMonth.from(anchor).plusMonths(step).atDay(1)
        GoalChartScale.DAY -> anchor.plusWeeks(step)
    }.toString() }
    KipuCard(containerColor = colors.surfaceCard, borderColor = null, shape = RoundedCornerShape(24.dp), elevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp).testTag("goal_contribution_chart"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tus aportes", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GoalChartScale.entries.forEach { option -> KipuFilterChip(selected = scale == option, onClick = { scale = option },
                    label = { BasicText(option.label, modifier = Modifier.fillMaxWidth(), maxLines = 1, softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                        color = { if (scale == option) colors.onPrimaryDeep else colors.secondaryMuted },
                        style = MaterialTheme.typography.labelLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)) },
                    modifier = Modifier.weight(1f).testTag("goal_chart_${option.name}")) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { move(-1) }, modifier = Modifier.testTag("goal_chart_previous")) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Periodo anterior") }
                Text(rangeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { move(1) }, modifier = Modifier.testTag("goal_chart_next")) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Periodo siguiente") }
            }
            val amount = selected?.let { buckets.getOrNull(it)?.minor } ?: buckets.fold(0L) { sum, bucket -> Math.addExact(sum, bucket.minor) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (selected == null) "Aportado en este periodo" else "Aporte seleccionado", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                MoneyText(amount, currency = goal.currency, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().height(156.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                buckets.forEachIndexed { index, bucket ->
                    val label = when (scale) {
                        GoalChartScale.MONTH -> bucket.start.format(DateTimeFormatter.ofPattern("MMM", locale)).removeSuffix(".")
                        GoalChartScale.WEEK -> "${bucket.start.dayOfMonth}–${bucket.endExclusive.minusDays(1).dayOfMonth}"
                        GoalChartScale.DAY -> bucket.start.format(DateTimeFormatter.ofPattern("EEE", locale)).removeSuffix(".")
                    }
                    val spoken = "${bucket.start.format(dateFormat)} – ${bucket.endExclusive.minusDays(1).format(dateFormat)}"
                    Column(Modifier.weight(1f).fillMaxHeight().clickable { selected = if (selected == index) null else index }
                        .testTag("goal_chart_bucket_$index").clearAndSetSemantics {
                            contentDescription = if (masked) "$spoken: importe oculto" else "$spoken: ${goal.currency.symbol()} ${formatMinorUnits(bucket.minor)}"
                            onClick { selected = if (selected == index) null else index; true }
                        }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.widthIn(max = 26.dp).fillMaxWidth().height((112f * bucket.minor.toFloat() / maximum).coerceAtLeast(2f).dp)
                            .background(if (bucket.minor == 0L) colors.borderSubtle else colors.incomeEmerald, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            fontSize = if (scale == GoalChartScale.MONTH) 9.sp else MaterialTheme.typography.labelSmall.fontSize,
                            color = colors.secondaryMuted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (buckets.all { it.minor == 0L }) Text("Sin aportes en este periodo.", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            Text("Solo aportes confirmados. Los retiros y reasignaciones no se suman aquí.", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            (if (scale == GoalChartScale.MONTH) goalMonthlySuggestion(goal, reservedMinor, LocalDate.now()) else null)?.let { suggestion ->
                Surface(color = colors.incomeBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("goal_monthly_suggestion")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Para llegar a tu fecha", fontWeight = FontWeight.Bold, color = colors.incomeText)
                        MoneyText(suggestion, currency = goal.currency, fontWeight = FontWeight.Bold)
                        Text("al mes sugerido, hasta ${goal.targetDate?.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))}", style = MaterialTheme.typography.bodyMedium)
                        Text("Lo que falta dividido entre los meses restantes, incluido este mes. Es una guía, no un cargo automático.", style = MaterialTheme.typography.bodySmall, color = colors.incomeText)
                    }
                }
            }
        }
    }
}
