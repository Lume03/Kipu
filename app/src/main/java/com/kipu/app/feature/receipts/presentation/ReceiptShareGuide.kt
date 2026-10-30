package com.kipu.app.feature.receipts.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.feature.receipts.domain.ReceiptGuideTimeline
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlin.math.max
import kotlin.math.min

private val Violet = Color(0xFF6E2A8C)
private val VioletInk = Color(0xFF2A1235)
private val VioletMuted = Color(0xFF5B4A66)
private val VioletTint = Color(0xFFF0E4F7)
private val GuideTeal = Color(0xFF0F766E)
private val GuideGreen = Color(0xFF15803D)
private val EaseOut = CubicBezierEasing(.22f, .8f, .3f, 1f)
private val EaseInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)

private const val PHONE_W = 196f
private const val PHONE_H = 344f
private const val SCREEN_W = 184f
private const val SCREEN_H = 278f

/**
 * Full-screen, finite and pausable guide: a phone shows the real sequence (Yape → share menu → Kipu).
 * One clock per scene drives every movement; nothing repeats forever so Compose tests stay idle.
 */
@Composable
fun ReceiptShareGuide(onDismiss: () -> Unit, reducedMotion: Boolean = rememberReducedMotionEnabled()) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var autoplay by rememberSaveable { mutableStateOf(true) }
    var playing by rememberSaveable { mutableStateOf(true) }
    var replay by rememberSaveable { mutableIntStateOf(0) }
    val time = remember(step, replay) { Animatable(0f) }
    val duration = ReceiptGuideTimeline.durations[step]
    val palette = rememberCalmEmeraldColors()
    fun go(target: Int) {
        step = target.coerceIn(0, 2)
        autoplay = false
        playing = true
    }
    LaunchedEffect(step, replay, playing, reducedMotion) {
        if (reducedMotion) { time.snapTo(duration); return@LaunchedEffect }
        if (!playing) return@LaunchedEffect
        time.animateTo(duration, tween(((duration - time.value) * 1000).toInt().coerceAtLeast(1), easing = LinearEasing))
        if (autoplay && step < 2) step++ else playing = false
    }
    // Kipu "opens" from its icon; this is the only transition that survives a step change.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(step, reducedMotion) {
        val target = if (step == 2) 1f else 0f
        if (reducedMotion) reveal.snapTo(target) else reveal.animateTo(target, tween(550, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)))
    }
    val footer = ReceiptGuideTimeline.footerVisible(step, time.value, !autoplay, reducedMotion)
    val footerP by animateFloatAsState(if (footer) 1f else 0f, tween(if (reducedMotion) 0 else 550, easing = EaseOut), label = "guideFooter")
    val bonus = remember { Animatable(0f) }
    LaunchedEffect(footer, reducedMotion, playing) {
        if (!footer) bonus.snapTo(0f)
        else if (reducedMotion) bonus.snapTo(1f)
        else if (playing || time.value >= duration) {
            bonus.animateTo(1f, tween(((1f - bonus.value) * 1600).toInt().coerceAtLeast(1), easing = LinearEasing))
        }
    }

    Surface(color = palette.background, modifier = Modifier.fillMaxSize().testTag("receipt_share_guide")) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val stageHeight = (maxHeight * .5f).coerceIn(290.dp, 440.dp)
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = palette.primaryDeep.copy(alpha = .12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.primaryDeep.copy(alpha = .25f))) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Share, null, Modifier.size(13.dp), tint = palette.primaryDeep)
                                Text("Guía rápida", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.primaryDeep)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).offset(x = 10.dp)) {
                            Icon(Icons.Filled.Close, "Cerrar guía", tint = palette.secondaryMuted)
                        }
                    }
                    Text("¿Cómo compartir desde Yape?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                    Text("Tres toques, sin escribir nada.", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted,
                        modifier = Modifier.padding(top = 2.dp))

                    var drag by remember { mutableFloatStateOf(0f) }
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(stageHeight).clip(RoundedCornerShape(24.dp))
                        .background(if (palette.isDark) Brush.linearGradient(listOf(palette.surfaceCard, palette.background))
                        else Brush.linearGradient(listOf(Color(0xFFE3F4F0), Color(0xFFF2F9F8), Color(0xFFEAF0F8))))
                        .pointerInput(step) {
                            detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                                if (drag < -40.dp.toPx()) go(step + 1) else if (drag > 40.dp.toPx()) go(step - 1)
                            }) { change, amount -> change.consume(); drag += amount }
                        }.semantics { contentDescription = "Demostración del paso ${step + 1} de 3. Desliza para cambiar de paso." }) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val fit = min(maxWidth * .74f / PHONE_W.dp, (maxHeight - 18.dp) / (PHONE_H - 52f).dp).coerceIn(.8f, 1.6f)
                            val base = LocalDensity.current
                            // The phone is a drawing: it keeps its proportions whatever the font size is.
                            CompositionLocalProvider(LocalDensity provides Density(base.density, 1f)) {
                                Phone(step, { time.value }, reducedMotion, { reveal.value },
                                    Modifier.align(Alignment.TopCenter).padding(top = 18.dp).requiredSize(PHONE_W.dp, PHONE_H.dp)
                                        .graphicsLayer { scaleX = fit; scaleY = fit; transformOrigin = TransformOrigin(.5f, 0f) })
                            }
                        }
                        IconButton(enabled = !reducedMotion, onClick = {
                            if (time.value >= duration) { step = 0; replay++; autoplay = true; playing = true }
                            else playing = !playing
                        }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = .92f)).testTag("receipt_guide_play_pause")) {
                            Icon(if (playing && !reducedMotion) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (playing && !reducedMotion) "Pausar animación" else "Reproducir animación", tint = Color(0xFF0F172A))
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Compartir", "Elegir Kipu", "Confirmar").forEachIndexed { index, label ->
                            val fraction = when { index < step -> 1f; index > step -> 0f; else -> (time.value / duration).coerceIn(0f, 1f) }
                            val tint = if (index <= step) palette.primaryDeep else palette.secondaryMuted
                            Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable { go(index) }.testTag("receipt_guide_step_${index + 1}")
                                .semantics {
                                    contentDescription = "Paso ${index + 1} de 3: $label"
                                    stateDescription = if (index < step) "Completado" else if (index == step) "Actual" else "Pendiente"
                                }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.borderSubtle)) {
                                    Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(palette.primaryDeep, RoundedCornerShape(2.dp)))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (index < step) Icon(Icons.Filled.Check, null, Modifier.size(13.dp), tint = tint)
                                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
                                }
                            }
                        }
                    }

                    Crossfade(step, animationSpec = tween(if (reducedMotion) 0 else 300), label = "guideText", modifier = Modifier.padding(top = 10.dp)) { s ->
                        Column {
                            Text(listOf("Toca «Compartir» en Yape", "Elige Kipu en la lista", "Revisa y confirma")[s],
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                            Text(listOf(
                                "Al terminar de yapear o recibir un pago, usa el botón de la confirmación.",
                                "En el menú de compartir de tu teléfono, toca el ícono de Kipu.",
                                "Kipu lee el comprobante en tu teléfono. No se registra nada hasta que tú confirmes.",
                            )[s], style = MaterialTheme.typography.bodyMedium, color = palette.secondaryMuted, modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    if (footerP > 0.01f || bonus.value > 0f) {
                        Box(Modifier.padding(top = 12.dp, bottom = 8.dp).fillMaxWidth().graphicsLayer {
                            alpha = footerP; translationY = (1f - footerP) * 12.dp.toPx()
                        }.clip(RoundedCornerShape(16.dp)).background(palette.primaryDeep.copy(alpha = .1f)).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(30.dp).graphicsLayer {
                                        val gone = ((bonus.value - .45f) / .35f).coerceIn(0f, 1f)
                                        alpha = 1f - gone; scaleX = 1f - .7f * gone; scaleY = scaleX; rotationZ = -10f * gone
                                    }.clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(Color(0xFFBFE6DF), Color(0xFF9CC9E6)))),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(16.dp), tint = Color(0xFF0B5F58))
                                    }
                                    Image(painterResource(R.drawable.kipu_logo), null, Modifier.size(30.dp).graphicsLayer {
                                        val appear = ((bonus.value - .5f) / .3f).coerceIn(0f, 1f)
                                        alpha = appear
                                        val pop = if (appear < .6f) .7f + .42f * (appear / .6f) else 1.12f - .12f * ((appear - .6f) / .4f)
                                        scaleX = pop; scaleY = pop
                                    })
                                }
                                Column {
                                    Text("¿Tienes una captura?", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = palette.primaryText)
                                    Text("También sirve: compártela desde Fotos.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Surface(color = palette.surfaceCard, shadowElevation = 6.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (footerP > .01f) {
                            Box(Modifier.width(104.dp * footerP).padding(end = 10.dp * footerP).graphicsLayer { alpha = footerP }) {
                                Surface(onClick = { go(step - 1) }, enabled = step > 0 && footer, shape = RoundedCornerShape(16.dp), color = palette.surfaceCard,
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, palette.borderSubtle),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("receipt_guide_back")) {
                                    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp), tint = palette.primaryText)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Atrás", fontWeight = FontWeight.Bold, color = palette.primaryText, maxLines = 1)
                                    }
                                }
                            }
                        }
                        Surface(onClick = { if (footer) onDismiss() else go(step + 1) }, shape = RoundedCornerShape(16.dp), color = palette.primaryDeep,
                            contentColor = palette.onPrimaryDeep, modifier = Modifier.weight(1f).heightIn(min = 54.dp).testTag("receipt_guide_primary")) {
                            Crossfade(footer, animationSpec = tween(if (reducedMotion) 0 else 350), label = "guideCta") { done ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    if (done) {
                                        Icon(Icons.Filled.Check, null, Modifier.size(19.dp))
                                        Spacer(Modifier.width(9.dp))
                                        Text("Volver a comprobantes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    } else {
                                        Text("Siguiente", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Time helpers
// ---------------------------------------------------------------------------------------------

private fun ramp(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)
private fun easeOut(p: Float): Float = EaseOut.transform(p)

/** Rises 10 dp while fading in, like the CSS `up` keyframes of the design. */
private fun Modifier.rise(clock: () -> Float, start: Float, final: Boolean, dur: Float = .4f): Modifier = graphicsLayer {
    val p = if (final) 1f else easeOut(ramp(clock(), start, dur))
    alpha = p; translationY = (1f - p) * 10.dp.toPx()
}

private fun Modifier.at(x: Float, y: Float): Modifier = offset(x.dp, y.dp)

private data class FingerPose(val pos: Offset, val scale: Float, val alpha: Float)

private fun fingerPose(t: Float, start: Float, duration: Float, from: Offset, to: Offset): FingerPose? {
    if (t < start || t > start + duration) return null
    val f = (t - start) / duration
    val alpha = when { f < .18f -> f / .18f; f > .84f -> 1f - (f - .84f) / .16f; else -> 1f }
    val move = EaseInOut.transform(((f - .18f) / .44f).coerceIn(0f, 1f))
    val scale = when { f < .62f -> 1f; f < .72f -> 1f - .2f * ((f - .62f) / .1f); f < .84f -> .8f + .2f * ((f - .72f) / .12f); else -> 1f }
    return FingerPose(Offset(from.x + (to.x - from.x) * move, from.y + (to.y - from.y) * move), scale, alpha)
}

// ---------------------------------------------------------------------------------------------
// The phone (drawn in a 196 × 344 dp design space and scaled by the caller)
// ---------------------------------------------------------------------------------------------

@Composable
private fun Phone(step: Int, clock: () -> Float, reduced: Boolean, reveal: () -> Float, modifier: Modifier) {
    Box(modifier.shadow(16.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), ambientColor = Color(0xFF0F172A), spotColor = Color(0xFF0F172A))
        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color(0xFF111827))) {
        Box(Modifier.at(6f, 6f).size(SCREEN_W.dp, SCREEN_H.dp).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(Color(0xFFFBF8FD))) {
            YapeReceiptScreen(step, clock, reduced)
            if (step >= 1) ShareSheet(step, clock, reduced)
            KipuReviewScreen(step, clock, reduced, reveal)
            Overlay(step, clock, reduced)
            Box(Modifier.at(88f, 7f).size(8.dp).background(Color(0xFF111827), CircleShape))
        }
    }
}

@Composable
private fun YapeReceiptScreen(step: Int, clock: () -> Float, reduced: Boolean) {
    val final = reduced || step > 0
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.at(0f, 20f).width(SCREEN_W.dp), contentAlignment = Alignment.Center) {
            Text("Yape", Modifier.clip(RoundedCornerShape(50)).background(VioletTint).padding(horizontal = 10.dp, vertical = 1.dp),
                color = Violet, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.at(70f, 52f).size(44.dp).graphicsLayer {
            val p = if (final) 1f else ramp(clock(), .15f, .45f)
            val s = if (p < .6f) .55f + .53f * (p / .6f) else 1.08f - .08f * ((p - .6f) / .4f)
            scaleX = s; scaleY = s; alpha = (p / .5f).coerceIn(0f, 1f)
        }.background(Violet, CircleShape)) {
            Canvas(Modifier.fillMaxSize()) {
                val p = if (final) 1f else ramp(clock(), .4f, .4f)
                val u = size.width / 44f
                val path = Path().apply {
                    moveTo(13.5f * u, 22.5f * u); lineTo(19.8f * u, 28.8f * u); lineTo(31.5f * u, 15.8f * u)
                }
                val out = Path()
                val m = PathMeasure().apply { setPath(path, false) }
                m.getSegment(0f, m.length * p, out, true)
                drawPath(out, Color.White, style = Stroke(3.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text("¡Yapeaste!", Modifier.at(0f, 104f).width(SCREEN_W.dp).rise(clock, .5f, final), color = VioletInk, fontSize = 14.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text("S/ 25.00", Modifier.at(0f, 122f).width(SCREEN_W.dp).rise(clock, .62f, final), color = VioletInk, fontSize = 25.sp, lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Column(Modifier.at(16f, 162f).width(152.dp).rise(clock, .76f, final)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEADCF2)))
            Spacer(Modifier.height(7.dp))
            listOf("Para" to "Carlos M.", "14 jun" to "12:41").forEach { (a, b) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(a, color = VioletMuted, fontSize = 10.5.sp, lineHeight = 18.sp)
                    Text(b, color = VioletInk, fontSize = 10.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val pressed = if (final) 1f else ramp(clock(), 2.76f, .25f)
        Row(Modifier.at(14f, 206f).size(156.dp, 40.dp).rise(clock, .95f, final)
            .clip(RoundedCornerShape(12.dp)).background(lerp(Color.White, Violet, pressed))
            .border(1.5.dp, Violet, RoundedCornerShape(12.dp)),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val ink = lerp(Violet, Color.White, pressed)
            Icon(Icons.Filled.Share, null, Modifier.size(14.dp), tint = ink)
            Spacer(Modifier.width(7.dp))
            Text("Compartir", color = ink, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.at(14f, 256f).size(156.dp, 40.dp).clip(RoundedCornerShape(12.dp)).background(VioletTint))
    }
}

@Composable
private fun ShareSheet(step: Int, clock: () -> Float, reduced: Boolean) {
    val final = reduced || step == 2
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (final) 1f else ramp(clock(), 0f, .4f) }.background(Color(0x610F172A)))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(124.dp).graphicsLayer {
            translationY = if (final) 0f else (1f - easeOut(ramp(clock(), 0f, .5f))) * 131.dp.toPx()
        }.shadow(8.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color.White)) {
            Box(Modifier.padding(top = 7.dp).align(Alignment.CenterHorizontally).size(26.dp, 3.dp).background(Color(0xFFCBD5E1), RoundedCornerShape(2.dp)))
            Text("Compartir con", Modifier.padding(start = 12.dp, top = 9.dp), color = Color(0xFF334155), fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp).fillMaxWidth()) {
                listOf("Chat", "Correo", "Kipu", "Fotos").forEachIndexed { index, label ->
                    val kipu = label == "Kipu"
                    Column(Modifier.weight(1f).graphicsLayer {
                        val t = clock()
                        val enter = if (final) 1f else easeOut(ramp(t, .45f + index * .08f, .35f))
                        val dim = if (kipu) 1f else 1f - .62f * (if (final) 1f else ramp(t, 1.5f, .4f))
                        alpha = enter * dim; translationY = (1f - enter) * 10.dp.toPx()
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(36.dp).graphicsLayer {
                            if (kipu) { val s = 1f + .12f * (if (final) 1f else ramp(clock(), 1.5f, .4f)); scaleX = s; scaleY = s }
                        }, contentAlignment = Alignment.Center) {
                            if (kipu) Image(painterResource(R.drawable.kipu_logo), null, Modifier.size(36.dp))
                            else Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF2F6)), contentAlignment = Alignment.Center) {
                                Icon(when (index) { 0 -> Icons.Filled.Chat; 1 -> Icons.Filled.Email; else -> Icons.Filled.PhotoLibrary }, null, Modifier.size(20.dp), tint = Color(0xFF475569))
                            }
                        }
                        Text(label, color = if (kipu) Color(0xFF0B5F58) else Color(0xFF334155), fontSize = 9.5.sp, lineHeight = 12.sp,
                            fontWeight = if (kipu) FontWeight.ExtraBold else FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun KipuReviewScreen(step: Int, clock: () -> Float, reduced: Boolean, reveal: () -> Float) {
    if (step != 2 && reveal() <= 0f) return
    val final = reduced
    Box(Modifier.fillMaxSize().drawWithContent {
        val p = reveal()
        if (p > 0f) {
            val center = Offset(size.width * .61f, size.height * .78f)
            val radius = size.maxDimension * 1.5f * p
            clipPath(Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }) { this@drawWithContent.drawContent() }
        }
    }.background(Color(0xFFF8FAFC))) {
        Text("Revisar movimiento", Modifier.at(14f, 20f).rise(clock, .45f, final), color = Color(0xFF0F172A), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.ExtraBold)
        Row(Modifier.at(14f, 42f).height(20.dp).rise(clock, .6f, final).clip(RoundedCornerShape(50)).background(Color(0xFFE1F4F0)).padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Lock, null, Modifier.size(11.dp), tint = Color(0xFF0B5F58))
            Text("Leído en tu teléfono", color = Color(0xFF0B5F58), fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.at(14f, 70f).size(156.dp, 140.dp).rise(clock, .55f, final).shadow(2.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White)) {
            listOf("MONTO" to "S/ 25.00", "PARA" to "Carlos M.", "CUENTA" to "Yape", "FECHA" to "14 jun · 12:41").forEachIndexed { index, (label, value) ->
                Row(Modifier.fillMaxWidth().height(35.dp).rise(clock, .7f + index * .12f, final, .35f)
                    .background(if (index == 0 && !final) lerp(Color.White, Color(0xFFFDF0B8), flash(clock())) else Color.White)
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = Color(0xFF64748B), fontSize = 8.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (index == 2) Box(Modifier.size(8.dp).background(Violet, CircleShape))
                        Text(value, color = Color(0xFF0F172A), fontSize = if (index == 0) 13.sp else 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                if (index < 3) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFEEF2F6)))
            }
        }
        val ok = if (final) 1f else ramp(clock(), 3.7f, .25f)
        Box(Modifier.at(14f, 222f).size(156.dp, 40.dp).rise(clock, 1.2f, final).clip(RoundedCornerShape(12.dp)).background(lerp(GuideTeal, GuideGreen, ok)),
            contentAlignment = Alignment.Center) {
            Text("Confirmar", color = Color.White, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.graphicsLayer { alpha = 1f - ok })
            Row(Modifier.graphicsLayer { alpha = ok; val s = .7f + .3f * ok; scaleX = s; scaleY = s }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White)
                Text("Registrado", color = Color.White, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun flash(t: Float): Float {
    val p = ramp(t, 1.5f, 1.1f)
    return if (p <= 0f || p >= 1f) 0f else if (p < .3f) p / .3f else 1f - (p - .3f) / .7f
}

/** Finger, tap ripple and the focus pulse. Drawn last so it sits above every scene. */
@Composable
private fun Overlay(step: Int, clock: () -> Float, reduced: Boolean) {
    if (reduced) return
    Canvas(Modifier.fillMaxSize()) {
        val t = clock()
        val cue = when (step) {
            0 -> Cue(1.2f, 2.2f, Offset(152f, 270f), Offset(92f, 226f))
            1 -> Cue(1.4f, 2.4f, Offset(158f, 264f), Offset(112f, 216f))
            else -> Cue(2.1f, 2.2f, Offset(160f, 272f), Offset(92f, 242f))
        }
        val (start, duration, from, to) = cue
        val tint = if (step == 0) Violet else GuideTeal
        // Focus pulse around what must be tapped (two finite pulses, like the prototype).
        val pulseStart = when (step) { 0 -> 1.3f; 1 -> 1.7f; else -> -1f }
        if (pulseStart >= 0f) repeat(2) { n ->
            val p = ramp(t, pulseStart + n * (if (step == 0) 1.4f else 1.3f), if (step == 0) 1.4f else 1.3f)
            if (p > 0f && p < 1f) {
                val spread = 10.dp.toPx() * p
                if (step == 0) drawRoundRect(Violet.copy(alpha = .4f * (1f - p)), Offset(14.dp.toPx() - spread, 206.dp.toPx() - spread),
                    Size(156.dp.toPx() + spread * 2, 40.dp.toPx() + spread * 2), CornerRadius(12.dp.toPx() + spread), style = Stroke(2.dp.toPx()))
                else drawRoundRect(GuideTeal.copy(alpha = .45f * (1f - p)), Offset(112.dp.toPx() - 19.dp.toPx() - spread, 216.dp.toPx() - 19.dp.toPx() - spread),
                    Size(38.dp.toPx() + spread * 2, 38.dp.toPx() + spread * 2), CornerRadius(13.dp.toPx() + spread), style = Stroke(2.dp.toPx()))
            }
        }
        val pose = fingerPose(t, start, duration, from, to)
        val press = start + duration * .72f
        val ripple = ramp(t, press, .6f)
        if (ripple > 0f && ripple < 1f) drawCircle(tint.copy(alpha = .4f * (1f - ripple)), 22.dp.toPx() * (.3f + 2.1f * ripple), Offset(to.x.dp.toPx(), to.y.dp.toPx()))
        if (pose != null) {
            val c = Offset(pose.pos.x.dp.toPx(), pose.pos.y.dp.toPx())
            drawCircle(Color(0xFF0F172A).copy(alpha = .2f * pose.alpha), 17.dp.toPx() * pose.scale, c)
            drawCircle(Color.White.copy(alpha = .96f * pose.alpha), 17.dp.toPx() * pose.scale, c, style = Stroke(2.dp.toPx()))
        }
    }
}

private data class Cue(val start: Float, val duration: Float, val from: Offset, val to: Offset)
