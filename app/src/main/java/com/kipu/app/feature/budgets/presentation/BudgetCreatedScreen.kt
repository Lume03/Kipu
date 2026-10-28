package com.kipu.app.feature.budgets.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.rememberPlanningColors

@Composable
internal fun BudgetCreatedScreen(content: BudgetDetailContent, onView: () -> Unit, onReturn: () -> Unit) {
    val colors = rememberPlanningColors()
    val pulse = if (!rememberReducedMotionEnabled()) {
        rememberInfiniteTransition(label = "budgetCreatedPulse").animateFloat(1f, 1.06f,
            infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "budgetCreatedScale")
    } else null
    val budget = content.budget
    Scaffold(modifier = Modifier.testTag("budget_created_screen"), containerColor = colors.background, bottomBar = {
        Surface(color = colors.surfaceCard) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onView, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text("Ver presupuesto", fontWeight = FontWeight.Bold) }
                OutlinedButton(onReturn, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Volver a Planificación", fontWeight = FontWeight.SemiBold) }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = colors.primaryDeep.copy(alpha = 0.12f),
                modifier = Modifier.size(104.dp).graphicsLayer { scaleX = pulse?.value ?: 1f; scaleY = pulse?.value ?: 1f }) {
                Surface(Modifier.padding(16.dp), shape = CircleShape, color = colors.primaryDeep) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, "Presupuesto creado correctamente", Modifier.size(38.dp), tint = colors.onPrimaryDeep) }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Presupuesto creado", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Tu presupuesto está listo.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, textAlign = TextAlign.Center)
            }
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceCard, border = BorderStroke(1.dp, colors.borderSubtle), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BudgetIconBadge(content.iconId, colors.primaryDeep, colors.incomeBg)
                        Column(Modifier.weight(1f)) {
                            Text(budget.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(content.scopeLabel, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                        }
                    }
                    content.current?.let {
                        Text("${BudgetTexts.periodTypeShort(budget.schedule.type)} · ${BudgetTexts.compactInterval(it.interval)}",
                            style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted)
                        BudgetAmountsRow(it.consumption, budget.currency, thresholdsBps = budget.thresholdsBps)
                        BudgetProgressBar(it.consumption, budget.currency, thresholdsBps = budget.thresholdsBps)
                    }
                    HorizontalDivider(color = colors.borderSubtle)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.NotificationsNone, null, tint = colors.warningText, modifier = Modifier.size(20.dp))
                        Text("Avisa al ${BudgetTexts.thresholds(budget.thresholdsBps)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Autorenew, null, tint = colors.primaryDeep, modifier = Modifier.size(20.dp))
                        Text(if (budget.schedule is BudgetSchedule.CustomRange) "Termina al cerrar el rango elegido" else "Se renueva al terminar el periodo",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = colors.incomeBg, modifier = Modifier.fillMaxWidth()) {
                Text("Solo planificación · no movió dinero.", Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = colors.incomeText, textAlign = TextAlign.Center)
            }
        }
    }
}
