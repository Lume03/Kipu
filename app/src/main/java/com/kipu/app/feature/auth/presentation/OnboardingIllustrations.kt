package com.kipu.app.feature.auth.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.component.KipuCard
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TOTAL_CENTS = 842_050L

/**
 * Illustrative sample data is restricted to onboarding and never enters the ledger.
 * Scene 1: the total counts up, the account and card arrive from opposite sides, the latest movement rises.
 */
@Composable
internal fun OnboardingMoneyIllustration(clock: SceneClock) {
    val colors = rememberCalmEmeraldColors()
    val t = OnboardingTimeline.Money
    val cents by remember(clock) {
        derivedStateOf { (TOTAL_CENTS * easeScene(clock.at(t.COUNT_START, t.COUNT_DURATION))).toLong() }
    }
    CappedFontScale {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.radialGradient(listOf(colors.incomeBg, colors.background)))
                .padding(14.dp)
                .clearAndSetSemantics { contentDescription = "Ilustración: cuentas, tarjeta y un ingreso en un solo lugar. Datos de ejemplo." },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IllustrationCard(Modifier.sceneReveal(clock, t.TOTAL, 0.55f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total disponible", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Row(Modifier.sceneReveal(clock, t.LIVE, 0.4f, dy = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(8.dp)
                                .graphicsLayer { alpha = 0.7f + 0.3f * cos(2f * PI.toFloat() * (clock() - 1f) / 1.8f) }
                                .background(colors.incomeEmerald, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("En tiempo real", color = colors.primaryDeep, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(formatSoles(cents), style = MaterialTheme.typography.headlineMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IllustrationCard(Modifier.weight(1f).sceneReveal(clock, t.SAVINGS, 0.6f, dy = 0.dp, dx = (-44).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IllustrationIcon(Icons.Outlined.AccountBalance)
                        Spacer(Modifier.width(8.dp))
                        Text("Ahorros", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("Sueldo BCP", style = MaterialTheme.typography.bodyMedium)
                    Text("S/ 3,850.00", style = MaterialTheme.typography.titleSmall)
                }
                Surface(
                    Modifier.weight(1f).sceneReveal(clock, t.CARD, 0.6f, dy = 0.dp, dx = 44.dp),
                    shape = RoundedCornerShape(18.dp), color = colors.primaryDeep, contentColor = colors.onPrimaryDeep,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("VISA", style = MaterialTheme.typography.titleSmall)
                            Icon(Icons.Outlined.Contactless, null, Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(38.dp))
                        Text("Oro BCP", style = MaterialTheme.typography.bodyMedium)
                        Text("•••• 4821", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Surface(
                Modifier.sceneReveal(clock, t.TRANSACTION, 0.6f),
                shape = RoundedCornerShape(16.dp), color = colors.pillTrack,
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IllustrationIcon(Icons.Outlined.Payments)
                    Spacer(Modifier.width(10.dp))
                    Text("Sueldo de Quincena", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("+S/ 1,925.00", style = MaterialTheme.typography.labelLarge, color = colors.incomeText)
                }
            }
        }
    }
}

/** Scene 2: sources → detected movement → Kipu, which goes from "Organizando" to "Organizado". */
@Composable
internal fun OnboardingLedgerIllustration(clock: SceneClock) {
    val colors = rememberCalmEmeraldColors()
    val t = OnboardingTimeline.Ledger
    CappedFontScale {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp).clearAndSetSemantics {
                contentDescription = "Ilustración: cuentas, billetera y efectivo; movimiento sugerido de supermercado; Kipu lo organiza. Datos de ejemplo."
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourcePill("BCP / BBVA", "Cuentas", Icons.Outlined.AccountBalance, Modifier.weight(1f).sceneReveal(clock, t.SOURCES[0], 0.5f))
                SourcePill("Billetera", "Digital", Icons.Outlined.PhoneAndroid, Modifier.weight(1f).sceneReveal(clock, t.SOURCES[1], 0.5f))
                Surface(Modifier.sceneReveal(clock, t.SOURCES[2], 0.5f), shape = RoundedCornerShape(20.dp), color = colors.surfaceCard) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Payments, null, Modifier.size(20.dp))
                        Text("Sol", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            DashedLink(clock, t.LINK_ONE, t.DOT_ONE)
            IllustrationCard(Modifier.sceneReveal(clock, t.MOVEMENT, 0.6f, dy = (-22).dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IllustrationIcon(Icons.Outlined.NotificationsActive)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Supermercado", style = MaterialTheme.typography.bodyMedium)
                        Text("● Detección sugerida", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("- S/ 142.50", style = MaterialTheme.typography.labelLarge, color = colors.expenseText)
                        Text("Hoy, 10:45 am", style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                    }
                }
            }
            DashedLink(clock, t.LINK_TWO, t.DOT_TWO)
            KipuBlock(clock)
        }
    }
}

@Composable
private fun KipuBlock(clock: SceneClock) {
    val colors = rememberCalmEmeraldColors()
    val t = OnboardingTimeline.Ledger
    Surface(
        Modifier.fillMaxWidth()
            .sceneReveal(clock, t.KIPU, 0.6f)
            .graphicsLayer {
                val s = 1f + 0.03f * sin(PI.toFloat() * clock.at(t.DONE, 0.5f))
                scaleX = s
                scaleY = s
            },
        shape = RoundedCornerShape(20.dp), color = colors.primaryDeep, contentColor = colors.onPrimaryDeep,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(colors.onPrimaryDeep.copy(alpha = 0.16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Kipu", style = MaterialTheme.typography.titleSmall)
                Text("Tus movimientos", style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.height(32.dp).widthIn(min = 112.dp), contentAlignment = Alignment.CenterEnd) {
                Row(
                    Modifier
                        .graphicsLayer {
                            alpha = clock.at(t.WORKING, 0.3f) * (1f - clock.at(t.WORKING_END, 0.3f))
                        }
                        .background(colors.onPrimaryDeep.copy(alpha = 0.16f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner(clock, colors.onPrimaryDeep)
                    Spacer(Modifier.width(6.dp))
                    Text("Organizando", style = MaterialTheme.typography.labelSmall)
                }
                Surface(
                    Modifier.sceneReveal(clock, t.DONE, 0.45f, dy = 0.dp, from = 0.7f),
                    shape = CircleShape, color = colors.surfaceCard, contentColor = colors.primaryDeep,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Organizado", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Spinner(clock: SceneClock, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(14.dp)) {
        val w = 2.dp.toPx()
        val inset = Offset(w / 2f, w / 2f)
        val box = Size(size.width - w, size.height - w)
        drawArc(color.copy(alpha = 0.4f), 0f, 360f, false, inset, box, style = Stroke(w))
        drawArc(color, clock() * 400f, 90f, false, inset, box, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Dashed line that grows downward with a dot riding along it and an arrow head once it lands. */
@Composable
private fun DashedLink(clock: SceneClock, start: Float, dotStart: Float) {
    val teal = rememberCalmEmeraldColors().primaryDeep
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val cx = size.width / 2f
        val grow = easeScene(clock.at(start, 0.5f))
        if (grow > 0f) {
            val bottom = size.height - 2.dp.toPx()
            drawLine(
                teal, Offset(cx, 0f), Offset(cx, bottom * grow), 2.dp.toPx(), StrokeCap.Round,
                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), -clock() * 14.dp.toPx()),
            )
            val arrow = clock.at(start + 0.4f, 0.2f)
            if (arrow > 0f) {
                val c = teal.copy(alpha = arrow)
                val tip = Offset(cx, size.height)
                drawLine(c, Offset(cx - 6.dp.toPx(), size.height - 6.dp.toPx()), tip, 2.dp.toPx(), StrokeCap.Round)
                drawLine(c, Offset(cx + 6.dp.toPx(), size.height - 6.dp.toPx()), tip, 2.dp.toPx(), StrokeCap.Round)
            }
        }
        val dot = clock.at(dotStart, 0.9f)
        if (dot > 0f && dot < 1f) {
            val visible = minOf(minOf(dot / 0.15f, 1f), minOf((1f - dot) / 0.15f, 1f))
            val y = dot * (size.height - 6.dp.toPx())
            drawCircle(teal.copy(alpha = 0.2f * visible), 8.dp.toPx(), Offset(cx, y))
            drawCircle(teal.copy(alpha = visible), 4.dp.toPx(), Offset(cx, y))
        }
    }
}

/**
 * Scene 3: a secure core with the lock pulsing, and three modules that join it through lines drawn one by one.
 * All elements are Compose; no bank connection is implied.
 */
@Composable
internal fun OnboardingPrivacyIllustration(clock: SceneClock) {
    val colors = rememberCalmEmeraldColors()
    val t = OnboardingTimeline.Privacy
    CappedFontScale {
        Box(
            Modifier.widthIn(max = 358.dp).fillMaxWidth().height(346.dp).clearAndSetSemantics {
                contentDescription = "Ilustración de privacidad: saldo oculto, local y seguro, sin acceso a credenciales ni APIs bancarias."
            },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val ring = clock.at(0.1f, 0.8f)
                if (ring > 0f) {
                    drawCircle(
                        colors.borderSubtle.copy(alpha = ring), 130.dp.toPx(), Offset(w / 2f, 130.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 7.dp.toPx()), clock() * 5.dp.toPx())),
                    )
                }
                val coreLeft = (w - 150.dp.toPx()) / 2f
                val modB = w - 168.dp.toPx()
                val measure = PathMeasure()
                val segment = Path()
                fun link(index: Int, build: Path.() -> Unit) {
                    val progress = easeScene(clock.at(t.LINES[index], 0.6f))
                    if (progress <= 0f) return
                    measure.setPath(Path().apply(build), false)
                    segment.rewind()
                    measure.getSegment(0f, measure.length * progress, segment, true)
                    drawPath(segment, colors.primaryDeep.copy(alpha = 0.5f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
                }
                fun Float.d() = this.dp.toPx()
                link(0) {
                    moveTo(70f.d(), 54f.d())
                    cubicTo(70f.d(), 92f.d(), coreLeft - 24f.d(), 112f.d(), coreLeft, 114f.d())
                }
                link(1) {
                    moveTo(coreLeft + 92f.d(), 232f.d())
                    cubicTo(coreLeft + 92f.d(), 250f.d(), modB + 42f.d(), 238f.d(), modB + 48f.d(), 246f.d())
                }
                link(2) {
                    moveTo(coreLeft + 34f.d(), 232f.d())
                    cubicTo(coreLeft + 34f.d(), 262f.d(), 86f.d(), 270f.d(), 86f.d(), 298f.d())
                }
            }
            Surface(
                Modifier.align(Alignment.TopCenter).offset(y = 56.dp).size(150.dp, 176.dp)
                    .sceneReveal(clock, t.CORE, 0.6f, dy = 0.dp, from = 0.88f),
                shape = RoundedCornerShape(34.dp), color = colors.surfaceCard,
                border = BorderStroke(2.dp, colors.primaryDeep), shadowElevation = 4.dp,
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    LockPulse(clock)
                    Spacer(Modifier.height(16.dp))
                    Surface(Modifier.sceneReveal(clock, t.CHIP, 0.5f), shape = CircleShape, color = colors.pillTrack, border = BorderStroke(1.dp, colors.borderSubtle)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(colors.primaryText, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("Local y seguro", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            PrivacyModule(
                Modifier.align(Alignment.TopStart)
                    .sceneReveal(clock, t.MODULES[0], 0.55f).sceneFloat(clock, t.FLOAT, 4.2f),
                Icons.Outlined.AccountBalanceWallet, "Saldo Kipu", "••••••", clock = null,
            )
            PrivacyModule(
                Modifier.align(Alignment.TopEnd).offset(y = 246.dp)
                    .sceneReveal(clock, t.MODULES[1], 0.55f).sceneFloat(clock, t.FLOAT + 0.4f, 4.8f),
                Icons.Outlined.VisibilityOff, "Credenciales", "0% acceso", clock = clock, emphasized = true,
            )
            Surface(
                Modifier.align(Alignment.TopStart).offset(y = 298.dp)
                    .sceneReveal(clock, t.MODULES[2], 0.55f).sceneFloat(clock, t.FLOAT + 0.2f, 5.2f),
                shape = CircleShape, color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.borderSubtle), shadowElevation = 2.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = colors.primaryDeep)
                    Spacer(Modifier.width(8.dp))
                    Text("Sin APIs bancarias", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LockPulse(clock: SceneClock) {
    val colors = rememberCalmEmeraldColors()
    Box(
        Modifier.size(76.dp)
            .graphicsLayer {
                val time = clock() - 1f
                if (time > 0f) {
                    val s = 1f + 0.022f * (1f - cos(2f * PI.toFloat() * time / 3.6f))
                    scaleX = s
                    scaleY = s
                }
            }
            .drawBehind {
                val time = clock() - 1f
                if (time > 0f) {
                    val phase = (time % 2.4f) / 2.4f
                    drawCircle(colors.primaryDeep.copy(alpha = 0.32f * (1f - phase)), size.minDimension / 2f + 20.dp.toPx() * phase)
                }
            }
            .background(colors.pillTrack, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(34.dp), tint = colors.primaryDeep)
    }
}

@Composable
private fun PrivacyModule(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    clock: SceneClock?,
    emphasized: Boolean = false,
) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier, shape = RoundedCornerShape(if (emphasized) 18.dp else 16.dp), color = colors.surfaceCard,
        border = BorderStroke(if (emphasized) 1.5.dp else 1.dp, if (emphasized) colors.primaryDeep else colors.borderSubtle),
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(colors.pillTrack, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = colors.primaryDeep)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                val highlight = colors.warningAmber.copy(alpha = 0.35f)
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (emphasized) colors.primaryDeep else colors.primaryText,
                    modifier = if (clock == null) Modifier else Modifier.drawBehind {
                        val p = clock.at(OnboardingTimeline.Privacy.HIGHLIGHT, 0.7f)
                        drawRect(highlight, Offset(0f, size.height * 0.58f), Size(size.width * p, size.height * 0.42f))
                    },
                )
            }
        }
    }
}

@Composable
private fun SourcePill(title: String, subtitle: String, icon: ImageVector, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = rememberCalmEmeraldColors().surfaceCard) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = rememberCalmEmeraldColors().secondaryMuted)
            }
        }
    }
}

@Composable
private fun IllustrationIcon(icon: ImageVector) {
    val colors = rememberCalmEmeraldColors()
    Box(Modifier.size(30.dp).background(colors.borderSubtle, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = colors.primaryDeep)
    }
}

@Composable
private fun IllustrationCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = rememberCalmEmeraldColors()
    KipuCard(modifier, shape = RoundedCornerShape(18.dp), containerColor = colors.surfaceCard,
        contentColor = colors.primaryText, borderColor = colors.borderSubtle) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}
