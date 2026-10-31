package com.kipu.app.feature.notifications.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.component.KipuBottomSheet
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlinx.coroutines.delay

private data class NoticeKind(
    val icon: ImageVector,
    val title: String,
    val example: String,
    val tint: (CalmEmeraldColors) -> Color,
    val container: (CalmEmeraldColors) -> Color,
)

private val NOTICE_KINDS = listOf(
    NoticeKind(Icons.Outlined.PieChart, "Presupuestos", "Llegaste al 80% de Comida", { it.expenseCoral }, { it.expenseBg }),
    NoticeKind(Icons.Outlined.CalendarMonth, "Vencimientos", "Tu cuota vence el viernes", { it.transferBlue }, { it.transferBg }),
    NoticeKind(Icons.Outlined.Flag, "Metas", "Vas 60% en tu viaje", { it.incomeEmerald }, { it.incomeBg }),
    NoticeKind(Icons.Outlined.WarningAmber, "Alertas", "Saldo bajo en tu cuenta", { it.warningAmber }, { it.warningBg }),
)

private data class HowStep(val icon: ImageVector, val title: String, val detail: String)

private val HOW_STEPS = listOf(
    HowStep(Icons.Outlined.Sensors, "Kipu vigila", "Tus presupuestos, metas y fechas"),
    HowStep(Icons.Outlined.NotificationsActive, "Te avisa", "Antes de que sea tarde"),
    HowStep(Icons.Outlined.TouchApp, "Tú decides", "Abres, actúas o lo descartas"),
)

/**
 * KIP-18: visual explanation of how Kipu notifications work. Replaces the plain-text rationale
 * dialog. Short scannable blocks, icon-led, with a staggered entrance and a gentle bell swing that
 * are both disabled under reduced motion.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationsExplainerSheet(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean? = null,
) {
    val palette = rememberCalmEmeraldColors()
    val disableMotion = reducedMotion ?: rememberReducedMotionEnabled()
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp < 360 || configuration.screenHeightDp < 640 ||
        LocalDensity.current.fontScale >= 1.5f

    KipuBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.background,
        modifier = modifier.testTag("notifications_explainer_sheet"),
        footer = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = onEnable,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("notifications_explainer_enable"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryDeep,
                        contentColor = palette.onPrimaryDeep,
                    ),
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Activar avisos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("notifications_explainer_dismiss"),
                ) {
                    Text("Ahora no", fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
                }
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SwingingBell(palette, size = if (compact) 56 else 72, animate = !disableMotion)
            Text(
                "Kipu te avisa a tiempo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = palette.primaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Solo lo que importa para tu dinero.",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.secondaryMuted,
                textAlign = TextAlign.Center,
            )

            Staggered(index = 0, enabled = !disableMotion) {
                ExplainerCard("CÓMO FUNCIONA", palette) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HOW_STEPS.forEachIndexed { i, step ->
                            StepCell(step, number = i + 1, palette = palette, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Staggered(index = 1, enabled = !disableMotion) {
                ExplainerCard("QUÉ TE AVISAMOS", palette) {
                    FlowRow(
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        NOTICE_KINDS.forEach { kind ->
                            NoticeKindTile(kind, palette, Modifier.weight(1f))
                        }
                    }
                }
            }

            Staggered(index = 2, enabled = !disableMotion) {
                ExplainerCard("TÚ TIENES EL CONTROL", palette) {
                    Benefit(Icons.Outlined.Schedule, "Eliges con cuántos días de anticipación.", palette)
                    Benefit(Icons.Outlined.Inbox, "Todo queda en tu centro de avisos, incluso sin permiso.", palette)
                    Benefit(Icons.Outlined.ToggleOn, "Actívalos o desactívalos cuando quieras.", palette)
                }
            }
        }
    }
}

@Composable
private fun SwingingBell(palette: CalmEmeraldColors, size: Int, animate: Boolean) {
    val angle = if (animate) {
        val transition = rememberInfiniteTransition(label = "bell")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2600
                    0f at 0
                    14f at 120
                    -12f at 260
                    8f at 400
                    -5f at 520
                    0f at 640
                    0f at 2600
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "bell-angle",
        ).value
    } else {
        0f
    }
    Box(
        Modifier.size(size.dp).background(palette.incomeBg, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.NotificationsActive,
            contentDescription = null,
            tint = palette.primaryDeep,
            modifier = Modifier.size((size * 0.46f).dp).rotate(angle),
        )
    }
}

/** Fades and lifts children in, one block after another. */
@Composable
private fun Staggered(index: Int, enabled: Boolean, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(!enabled) }
    LaunchedEffect(enabled) {
        if (enabled) {
            delay(90L * index + 60L)
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = if (enabled) {
            fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 }
        } else {
            EnterTransition.None
        },
    ) { content() }
}

@Composable
private fun ExplainerCard(title: String, palette: CalmEmeraldColors, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.surfaceCard,
        border = BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = palette.primaryDeep,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

@Composable
private fun StepCell(step: HowStep, number: Int, palette: CalmEmeraldColors, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier.padding(top = 4.dp, end = 4.dp).size(44.dp).background(palette.pillTrack, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(step.icon, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(22.dp))
            }
            Box(
                Modifier.size(18.dp).background(palette.primaryDeep, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onPrimaryDeep,
                )
            }
        }
        Text(
            step.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.primaryText,
            textAlign = TextAlign.Center,
        )
        Text(
            step.detail,
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoticeKindTile(kind: NoticeKind, palette: CalmEmeraldColors, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = kind.container(palette), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(kind.icon, contentDescription = null, tint = kind.tint(palette), modifier = Modifier.size(20.dp))
                Text(
                    kind.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryText,
                )
            }
            Text(
                "“${kind.example}”",
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryMuted,
            )
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String, palette: CalmEmeraldColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(palette.incomeBg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(18.dp))
        }
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = palette.primaryText)
    }
    Spacer(Modifier.height(0.dp))
}
