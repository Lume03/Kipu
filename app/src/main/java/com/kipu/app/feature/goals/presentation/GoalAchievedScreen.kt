package com.kipu.app.feature.goals.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.*
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.rememberPlanningColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
internal fun GoalAchievedScreen(goal: Goal, events: List<GoalEvent>, onRelease: () -> Unit, onRetain: () -> Unit, onCreate: (() -> Unit)? = null,
    reducedMotion: Boolean = rememberReducedMotionEnabled()) {
    val colors = rememberPlanningColors()
    val reserved = remember(goal, events) { GoalProgressCalculator.calculate(goal, events).reserved }
    val contributions = remember(events) { events.filter { !it.transactionVoided && it.type in setOf(GoalEventType.ALLOCATION, GoalEventType.DIRECT_DEPOSIT) } }
    val zone = ZoneId.systemDefault()
    val completedOn = goal.updatedAt.atZone(zone).toLocalDate()
    val startedOn = goal.createdAt.atZone(zone).toLocalDate()
    val months = ChronoUnit.MONTHS.between(startedOn.withDayOfMonth(1), completedOn.withDayOfMonth(1)).plus(1).coerceAtLeast(1)
    val average = contributions.fold(0L) { sum, event -> Math.addExact(sum, event.delta.minorUnits) } / months
    Scaffold(containerColor = colors.background, modifier = Modifier.testTag("goal_celebration"), bottomBar = {
        Surface(color = colors.background) {
            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRelease, enabled = reserved.minorUnits > 0L,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goal_celebration_release"), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep)) { Text("Liberar dinero a mis cuentas", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onRetain, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("Dejarlo reservado por ahora", color = colors.primaryText, textAlign = TextAlign.Center)
                }
                onCreate?.let { create -> TextButton(onClick = create, modifier = Modifier.fillMaxWidth()) { Text("Crear una nueva meta", color = colors.primaryDeep) } }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                CelebrationConfetti(reducedMotion, Modifier.matchParentSize())
                PulsingGoalCheck(reducedMotion)
            }
            Text("¡Meta lograda!", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("Llegaste al objetivo de ${goal.name}.", style = MaterialTheme.typography.bodyLarge, color = colors.secondaryMuted, textAlign = TextAlign.Center)
            Surface(color = colors.surfaceCard, shape = RoundedCornerShape(24.dp), shadowElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Reservado", color = colors.secondaryMuted, modifier = Modifier.padding(top = 8.dp))
                        MoneyText(reserved, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    LinearProgressIndicator(progress = { 1f }, color = colors.incomeEmerald, trackColor = colors.incomeBg,
                        modifier = Modifier.fillMaxWidth().height(10.dp), drawStopIndicator = {})
                    HorizontalDivider(color = colors.borderSubtle)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column { Text("Aportes", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted); Text(contributions.size.toString(), fontWeight = FontWeight.Bold) }
                        Column { Text("Promedio al mes", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted); MoneyText(Money(average, goal.currency), fontWeight = FontWeight.Bold) }
                        Column { Text(if (goal.targetDate != null) "Fecha meta" else "Fecha lograda", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                            Text((goal.targetDate ?: completedOn).format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE"))), fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Surface(color = colors.warningBg, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = colors.warningText, modifier = Modifier.size(22.dp))
                    Text("Tu dinero sigue apartado. Libéralo cuando vayas a usarlo.", color = colors.warningText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PulsingGoalCheck(reducedMotion: Boolean) {
    val colors = rememberPlanningColors()
    val pulse = if (reducedMotion) null else rememberInfiniteTransition(label = "goalCheckPulse").animateFloat(
        initialValue = 0.96f, targetValue = 1.04f, animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "goalCheckScale")
    Surface(shape = CircleShape, color = colors.incomeBg, modifier = Modifier.size(128.dp).graphicsLayer { scaleX = pulse?.value ?: 1f; scaleY = scaleX }) {
        Box(contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, color = colors.incomeEmerald, modifier = Modifier.size(88.dp)) {
                Icon(Icons.Default.Check, null, tint = if (colors.isDark) colors.background else Color.White, modifier = Modifier.padding(22.dp))
            }
        }
    }
}

@Composable
private fun CelebrationConfetti(reducedMotion: Boolean, modifier: Modifier) {
    val colors = rememberPlanningColors()
    val fall = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) { if (reducedMotion) fall.snapTo(0.35f) else fall.animateTo(1f, tween(4200, easing = LinearEasing)) }
    val palette = listOf(colors.incomeEmerald, colors.primaryDeep, colors.warningAmber, colors.expenseCoral)
    Canvas(modifier) {
        val progress = fall.value
        repeat(24) { index ->
            val x = size.width * (((index * 37 % 101) / 100f) + 0.025f * kotlin.math.sin(progress * 8f + index))
            val y = size.height * (((index * 23 % 97) / 97f + progress * 1.7f) % 1f)
            val center = Offset(x, y)
            rotate(index * 29f + progress * 240f, center) {
                drawRect(palette[index % palette.size].copy(alpha = if (reducedMotion) 0.7f else (1.3f - progress).coerceIn(0.3f, 1f)),
                    topLeft = center, size = Size(7.dp.toPx(), 4.dp.toPx()))
            }
        }
    }
}
