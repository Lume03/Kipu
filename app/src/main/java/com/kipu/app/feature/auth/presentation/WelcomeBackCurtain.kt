package com.kipu.app.feature.auth.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kipu.app.R
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.LightStatusBarIcons
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

private val IrisEase = CubicBezierEasing(0.7f, 0f, 0.4f, 1f)
private val DrawEase = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)
private val ButtonCenterY = 0.765f
private val SealCenterY = 0.48f

/** Size of the white frame around the real Kipu logo at the centre of the curtain. */
private val TileSize = 96.dp
private val LogoSize = 80.dp

/**
 * "¡Hola de nuevo!": a teal curtain that hides the first load after signing in. The cords hang and a light wave
 * runs along their knots until [ready]; then the knots fold into the logo, which turns into a check, and the curtain
 * closes like an iris over the logo, leaving the loaded screen behind. [onFinished] runs once it is gone.
 */
@Composable
internal fun WelcomeBackCurtain(email: String, ready: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val intro = rememberSceneClock(active = true)
    val exit = rememberSceneClock(active = ready)
    val tl = WelcomeBackTimeline
    LightStatusBarIcons()
    BackHandler(true) {}
    LaunchedEffect(ready) {
        if (ready) {
            if (!reducedMotion) delay((tl.EXIT_TOTAL * 1000).toLong() + 80L)
            onFinished()
        }
    }
    BoxWithConstraints(
        modifier.fillMaxSize().testTag("auth-welcome-curtain")
            .pointerInput(Unit) { detectTapGestures { } }
            .circleReveal({ 1f - IrisEase.transform(exit.at(tl.EXIT_IRIS, tl.EXIT_IRIS_DURATION)) }, { Offset(it.width / 2f, it.height * SealCenterY) })
            .circleReveal({ easeReveal(intro.at(0f, tl.REVEAL)) }, { Offset(it.width / 2f, it.height * ButtonCenterY) })
            .drawBehind { drawRect(celebrationBrush(colors.isDark, size.width, size.height)) },
    ) {
        val sealY = maxHeight * SealCenterY
        Canvas(Modifier.fillMaxSize()) {
            val t = intro()
            val e = exit()
            val s = size.width / QUIPU_REF_WIDTH
            withTransform({ scale(s, s, Offset.Zero) }) {
                val seal = Offset(QUIPU_REF_WIDTH / 2f, size.height * SealCenterY / s)
                drawAmbientBeads(t)
                val glow = OnboardingTimeline.progress(t, 0.9f, 0.9f)
                if (glow > 0f) {
                    val breathe = if (t > 1.8f) 1f + 0.08f * sin(PI.toFloat() * (t - 1.8f) / 4.5f) else 1f
                    val radius = 170f * breathe
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * glow), Color.Transparent), seal, radius), radius, seal)
                }
                drawQuipuField(
                    t, tl,
                    cordsAlpha = 1f - OnboardingTimeline.progress(e, tl.EXIT_CORDS_FADE, tl.EXIT_CORDS_FADE_DURATION),
                    gather = { order -> OnboardingTimeline.progress(e, tl.EXIT_GATHER + order * tl.EXIT_GATHER_STEP, tl.EXIT_GATHER_DURATION) },
                    target = seal,
                    halo = { cord, index ->
                        val local = t - (tl.WAVE_START + cord * tl.WAVE_CORD_STEP + index * tl.WAVE_KNOT_STEP)
                        if (local < 0f) -1f else (local % tl.WAVE_PERIOD) / tl.WAVE_PERIOD
                    },
                )
            }
        }
        LogoTile(intro, exit, Modifier.align(Alignment.TopCenter).offset(y = sealY - TileSize / 2))
        CappedFontScale {
            Column(
                Modifier.fillMaxWidth().offset(y = sealY + TileSize / 2 + 48.dp).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "¡Hola de nuevo!", Modifier.sceneReveal(intro, tl.TITLE, 0.6f).semantics { heading() },
                    color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(20.dp))
                EmailPill(email, Modifier.sceneReveal(intro, tl.EMAIL, 0.6f))
                Spacer(Modifier.height(26.dp))
                Box(Modifier.sceneReveal(intro, tl.CAPTION, 0.6f).height(26.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.graphicsLayer { alpha = 1f - exit.at(tl.EXIT_CAPTION_OUT, 0.2f) }
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Preparando tu resumen", color = Color.White.copy(alpha = 0.88f), fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(6.dp))
                        repeat(3) { index -> WaitingDot(intro, index * 0.15f) }
                    }
                    Text(
                        "¡Todo listo!",
                        Modifier.graphicsLayer {
                            val p = exit.at(tl.EXIT_CAPTION_IN, 0.35f)
                            alpha = p
                            translationY = (1f - p) * 18.dp.toPx()
                        }.semantics { liveRegion = LiveRegionMode.Polite },
                        color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private fun easeReveal(p: Float) = RevealEaseCurve.transform(p)
private val RevealEaseCurve = CubicBezierEasing(0.5f, 0f, 0.15f, 1f)

/** The real logo in a white frame; once the app is ready it fades out and a check is drawn in its place. */
@Composable
private fun LogoTile(intro: SceneClock, exit: SceneClock, modifier: Modifier = Modifier) {
    val tl = WelcomeBackTimeline
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.size(TileSize)
            .graphicsLayer {
                val pop = easeOutBack(intro.at(tl.TILE, 0.65f))
                val pulseP = exit.at(tl.EXIT_TILE_PULSE, 0.5f)
                val pulse = if (pulseP < 0.4f) lerp(1f, 1.16f, pulseP / 0.4f) else lerp(1.16f, 1f, (pulseP - 0.4f) / 0.6f)
                scaleX = pop * pulse
                scaleY = pop * pulse
            }
            .shadow(16.dp, shape, ambientColor = Color(0xFF042824), spotColor = Color(0xFF042824))
            .clip(shape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.kipu_logo), null,
            Modifier.size(LogoSize).clip(RoundedCornerShape(24.dp)).testTag("auth-welcome-logo")
                .graphicsLayer { alpha = 1f - exit.at(tl.EXIT_LOGO_FADE, 0.2f) },
        )
        Canvas(Modifier.fillMaxSize()) {
            val p = DrawEase.transform(exit.at(tl.EXIT_CHECK, 0.4f))
            if (p > 0f) {
                val unit = size.width / 88f
                val c = Offset(size.width / 2f, size.height / 2f)
                val path = Path().apply {
                    moveTo(c.x - 17f * unit, c.y + 2f * unit)
                    lineTo(c.x - 5f * unit, c.y + 14f * unit)
                    lineTo(c.x + 18f * unit, c.y - 12f * unit)
                }
                val out = Path()
                val measure = PathMeasure().apply { setPath(path, false) }
                measure.getSegment(0f, measure.length * p, out, true)
                drawPath(out, Color(0xFF0F766E), style = Stroke(7f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun WaitingDot(clock: SceneClock, delay: Float) {
    Box(
        Modifier.padding(start = 4.dp).size(4.dp)
            .graphicsLayer {
                val v = (((clock() - delay) % 1.2f) + 1.2f) % 1.2f / 1.2f
                val high = if (v < 0.4f) v / 0.4f else 1f - (v - 0.4f) / 0.6f
                alpha = lerp(0.25f, 1f, high)
                translationY = -3.dp.toPx() * high
            }
            .background(Color.White, CircleShape),
    )
}
