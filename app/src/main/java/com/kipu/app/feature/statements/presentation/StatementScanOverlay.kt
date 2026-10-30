package com.kipu.app.feature.statements.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Finite timeline (seconds) of the "reading your statement" moment; the scan itself lasts about three seconds. */
object StatementScanTimeline {
    const val SCAN_START = .7f
    const val SCAN_END = 3.1f
    const val RING = 3.45f
    const val CHECK = 3.95f
    const val TOTAL = 4.9f
    const val HANDOFF_MILLIS = 1_100L

    /** Counter shown while scanning: eases out to the real number of movements. */
    fun count(seconds: Float, total: Int): Int {
        val p = ((seconds - SCAN_START) / (SCAN_END - SCAN_START)).coerceIn(0f, 1f)
        return (total * (1f - (1f - p) * (1f - p))).roundToInt()
    }

    /** The scan never ends before the movements are known, and never cuts the check short. */
    fun canFinish(seconds: Float, known: Boolean) = known && seconds >= TOTAL
}

private val EaseOut = CubicBezierEasing(.22f, .8f, .3f, 1f)
private fun ramp(t: Float, start: Float, duration: Float) = ((t - start) / duration).coerceIn(0f, 1f)

/**
 * Full-screen scan: a document rises, a light beam reads it row by row while a counter climbs, then it folds into a
 * drawn check and "¡Listo!". [onFinished] runs once the check has had its moment; reading itself keeps going underneath.
 */
@Composable
fun StatementScanOverlay(summary: StatementScanSummary?, onFinished: () -> Unit) {
    val reduced = rememberReducedMotionEnabled()
    val clock = remember { Animatable(0f) }
    val known = summary != null
    val finishState = rememberUpdatedState(onFinished)
    LaunchedEffect(reduced) {
        if (reduced) clock.snapTo(StatementScanTimeline.SCAN_END)
        else clock.animateTo(StatementScanTimeline.SCAN_END, tween((StatementScanTimeline.SCAN_END * 1000).toInt(), easing = LinearEasing))
    }
    LaunchedEffect(known, reduced) {
        if (!known) return@LaunchedEffect
        if (reduced) { clock.snapTo(StatementScanTimeline.TOTAL); delay(700); finishState.value(); return@LaunchedEffect }
        // Let the scan reach its end before the check starts, even if the movements were known earlier.
        while (clock.value < StatementScanTimeline.SCAN_END - .02f) delay(30)
        clock.animateTo(StatementScanTimeline.TOTAL, tween(((StatementScanTimeline.TOTAL - clock.value) * 1000).toInt().coerceAtLeast(1), easing = LinearEasing))
        delay(StatementScanTimeline.HANDOFF_MILLIS)
        finishState.value()
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ScanScene({ clock.value }, summary, waitingForResult = !known && clock.value >= StatementScanTimeline.SCAN_END - .02f, onSkip = { if (known) finishState.value() })
    }
}

@Composable
private fun ScanScene(clock: () -> Float, summary: StatementScanSummary?, waitingForResult: Boolean, onSkip: () -> Unit) {
    val rows = summary?.sample.orEmpty()
    Box(Modifier.fillMaxSize().testTag("statement_scan")
        .background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF0B5F58), Color(0xFF073F3A)), Offset(0f, 0f), Offset(300f, 2200f)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Row(Modifier.graphicsLayer { alpha = easeIn(ramp(clock(), .15f, .5f)) }.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .15f))
                .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatementBankTile((summary?.label ?: "BCP").substringBefore(' '), size = 28.dp)
                Text(summary?.label ?: "Estado de cuenta", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                Text(if (waitingForResult) "Casi listo…" else "Leyendo tu estado de cuenta", color = Color.White, fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.graphicsLayer {
                        val t = clock()
                        alpha = easeIn(ramp(t, .3f, .55f)) * (1f - (if (waitingForResult) 0f else ramp(t, 3.05f, .35f)))
                        translationY = (1f - easeOut(ramp(t, .3f, .55f))) * 14.dp.toPx()
                    })
                Text("¡Listo!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = easeIn(ramp(clock(), 4.1f, .5f)); translationY = (1f - easeOut(ramp(clock(), 4.1f, .5f))) * 14.dp.toPx() })
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // The document, folded away after the scan.
                Column(Modifier.width(240.dp).height(334.dp).graphicsLayer {
                    val t = clock()
                    val entry = easeOut(ramp(t, .25f, .7f))
                    val fold = ramp(t, 3.15f, .6f)
                    alpha = entry * (1f - fold)
                    translationY = (1f - entry) * 46.dp.toPx() - fold * 70.dp.toPx()
                    rotationZ = -2f - (1f - entry) * 5f + fold * 2f
                    val s = 1f - fold * .55f
                    scaleX = s; scaleY = s
                }.shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Color(0xFF02201D), spotColor = Color(0xFF02201D)).clip(RoundedCornerShape(20.dp)).background(Color.White)) {
                    Row(Modifier.fillMaxWidth().height(56.dp).background(Color(0xFFF4F7FA)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatementBankTile((summary?.label ?: "BCP").substringBefore(' '), size = 30.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(90.dp, 7.dp).background(Color(0xFFC9D3DD), RoundedCornerShape(4.dp)))
                            Box(Modifier.size(60.dp, 6.dp).background(Color(0xFFDCE3EA), RoundedCornerShape(4.dp)))
                        }
                        Text("PDF", color = Color(0xFF64748B), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(Modifier.fillMaxWidth().height(270.dp)) {
                        Column {
                            repeat(9) { i ->
                                val lineAt = StatementScanTimeline.SCAN_START + (i + .5f) / 9f * (StatementScanTimeline.SCAN_END - StatementScanTimeline.SCAN_START)
                                Row(Modifier.fillMaxWidth().height(30.dp).graphicsLayer {}.background(Color(0xFFE1F4F0).copy(alpha = easeOut(ramp(clock(), lineAt, .35f))))
                                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).graphicsLayer { val p = ramp(clock(), lineAt, .35f); scaleX = p; scaleY = p }.background(Color(0xFF0F766E), CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    val row = rows.getOrNull(i)
                                    if (row != null) {
                                        Text(row.first, Modifier.weight(1f), color = Color(0xFF334155), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(row.second, color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    } else {
                                        Box(Modifier.weight(1f).height(7.dp).background(Color(0xFFE6ECF1), RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.width(30.dp))
                                        Box(Modifier.size(34.dp, 7.dp).background(Color(0xFFD7DFE7), RoundedCornerShape(4.dp)))
                                    }
                                }
                            }
                        }
                        // Beam and its trail.
                        Box(Modifier.fillMaxWidth().height(60.dp).graphicsLayer {
                            val p = ramp(clock(), StatementScanTimeline.SCAN_START, StatementScanTimeline.SCAN_END - StatementScanTimeline.SCAN_START)
                            translationY = p * 270.dp.toPx() - 58.dp.toPx()
                            alpha = if (p <= 0f || p >= 1f) 0f else minOf(1f, p / .06f, (1f - p) / .06f)
                        }.background(Brush.verticalGradient(listOf(Color(0x005EEAD4), Color(0x615EEAD4)))))
                        Box(Modifier.fillMaxWidth().height(3.dp).graphicsLayer {
                            val p = ramp(clock(), StatementScanTimeline.SCAN_START, StatementScanTimeline.SCAN_END - StatementScanTimeline.SCAN_START)
                            translationY = p * 270.dp.toPx()
                            alpha = if (p <= 0f || p >= 1f) 0f else minOf(1f, p / .06f, (1f - p) / .06f)
                        }.shadow(10.dp, RoundedCornerShape(2.dp), ambientColor = Color(0xFF5EEAD4), spotColor = Color(0xFF5EEAD4)).background(Color(0xFF5EEAD4)))
                    }
                }
                CheckRing(clock)
            }
            // Counter and progress during the scan; totals and the way forward after it.
            Box(Modifier.fillMaxWidth().height(174.dp)) {
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    val t = clock(); alpha = easeIn(ramp(t, .6f, .5f)) * (1f - ramp(t, 3.1f, .3f))
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (summary == null) "—" else StatementScanTimeline.count(clock(), summary.count).toString(), color = Color.White, fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold, lineHeight = 60.sp)
                    Text("movimientos encontrados", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.size(240.dp, 5.dp).background(Color.White.copy(alpha = .2f), RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(ramp(clock(), StatementScanTimeline.SCAN_START, StatementScanTimeline.SCAN_END - StatementScanTimeline.SCAN_START)).height(5.dp)
                            .background(Color(0xFF5EEAD4), RoundedCornerShape(3.dp)))
                    }
                }
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = easeIn(ramp(clock(), 4.25f, .5f)); translationY = (1f - easeOut(ramp(clock(), 4.25f, .5f))) * 14.dp.toPx() },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Encontramos ${summary?.count ?: 0} movimientos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        summary?.penTotal?.let { TotalPill("PEN $it") }
                        summary?.usdTotal?.let { TotalPill("USD $it") }
                    }
                    if (summary?.verified == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.VerifiedUser, null, Modifier.size(17.dp), tint = Color(0xFFA7F3D0))
                        Text("Totales verificados con el PDF", color = Color(0xFFA7F3D0), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Surface(onClick = onSkip, shape = RoundedCornerShape(16.dp), color = Color.White, contentColor = Color(0xFF0B5F58),
                modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth().heightIn(min = 56.dp).graphicsLayer { alpha = easeIn(ramp(clock(), 4.8f, .1f)) }.testTag("statement_scan_continue")) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Revisar movimientos", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Filled.ChevronRight, null, Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun TotalPill(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .16f)).padding(horizontal = 14.dp, vertical = 6.dp))
}

/** The folded document turns into a ring that closes and a check that is drawn, with two soft ripples. */
@Composable
private fun CheckRing(clock: () -> Float) {
    Canvas(Modifier.size(150.dp).offset(y = (-6).dp).graphicsLayer {
        val p = easeOut(ramp(clock(), 3.4f, .5f))
        alpha = minOf(1f, p * 2f); val s = .4f + .6f * p; scaleX = s; scaleY = s
    }) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * .37f
        drawCircle(Color.White.copy(alpha = .14f), radius * 1.14f, center)
        listOf(4.0f, 4.18f).forEachIndexed { i, start ->
            val r = ramp(clock(), start, .9f)
            if (r > 0f && r < 1f) drawCircle(Color.White.copy(alpha = (.55f - i * .15f) * (1f - r)), radius * (.7f + 1.6f * r), center, style = Stroke(3.dp.toPx()))
        }
        val ring = ramp(clock(), StatementScanTimeline.RING, .55f)
        drawArc(Color.White, -90f, 360f * easeOut(ring), false, topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        val check = easeOut(ramp(clock(), StatementScanTimeline.CHECK, .4f))
        if (check > 0f) {
            val u = radius / 52f
            val path = Path().apply {
                moveTo(center.x - 22f * u, center.y + 1f * u); lineTo(center.x - 7f * u, center.y + 16f * u); lineTo(center.x + 23f * u, center.y - 16f * u)
            }
            val out = Path()
            PathMeasure().apply { setPath(path, false) }.let { it.getSegment(0f, it.length * check, out, true) }
            drawPath(out, Color.White, style = Stroke(9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun easeOut(p: Float) = EaseOut.transform(p)
private fun easeIn(p: Float) = p
