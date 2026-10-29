package com.kipu.app.feature.auth.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/** Seconds since the page became the current one. Read it inside draw/graphicsLayer lambdas to avoid recomposition. */
internal typealias SceneClock = () -> Float

/**
 * Timings (seconds) of the three onboarding scenes. Pieces enter in sequence, 120–220 ms apart, and the
 * clock keeps running for [LOOP_END] so the gentle loops (float, pulse, dashes) stay alive without
 * infinite transitions.
 */
internal object OnboardingTimeline {
    const val LOOP_END = 40f

    /** Value used when the system asks for no animations: everything is already in place. */
    const val SETTLED = 20f

    fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

    object Copy {
        const val TITLE = 0.1f
        const val BODY = 0.28f
    }

    object Money {
        const val TOTAL = 0.1f
        const val COUNT_START = 0.3f
        const val COUNT_DURATION = 1.1f
        const val LIVE = 0.6f
        const val SAVINGS = 0.45f
        const val CARD = 0.6f
        const val TRANSACTION = 0.95f
    }

    object Ledger {
        val SOURCES = floatArrayOf(0.15f, 0.33f, 0.51f)
        const val LINK_ONE = 0.85f
        const val DOT_ONE = 1.05f
        const val MOVEMENT = 1.45f
        const val LINK_TWO = 2.35f
        const val DOT_TWO = 2.55f
        const val KIPU = 2.85f
        const val WORKING = 3.3f
        const val WORKING_END = 4.2f
        const val DONE = 4.35f
    }

    object Privacy {
        const val CORE = 0.1f
        const val CHIP = 0.5f
        val LINES = floatArrayOf(0.75f, 1.0f, 1.25f)
        val MODULES = floatArrayOf(1.1f, 1.55f, 1.8f)
        const val HIGHLIGHT = 2.1f
        const val FLOAT = 2.0f
    }
}

/** "S/ 8,420.50" — grouping and decimals fixed so the counter reads the same on every device locale. */
internal fun formatSoles(cents: Long): String =
    "S/ " + String.format(Locale.US, "%,.2f", cents / 100.0)

private val SceneEase = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)

internal fun SceneClock.at(start: Float, duration: Float): Float =
    OnboardingTimeline.progress(invoke(), start, duration)

internal fun easeScene(progress: Float): Float = SceneEase.transform(progress)

/**
 * Starts from zero each time the page becomes the current one and holds its last frame while it leaves.
 * With animations off in the system settings the scene is shown already composed.
 */
@Composable
internal fun rememberSceneClock(active: Boolean): SceneClock {
    val reduced = rememberReducedMotionEnabled()
    val time = remember { Animatable(0f) }
    LaunchedEffect(active, reduced) {
        if (!active) return@LaunchedEffect
        if (reduced) {
            time.snapTo(OnboardingTimeline.SETTLED)
        } else {
            time.snapTo(0f)
            time.animateTo(
                OnboardingTimeline.LOOP_END,
                tween((OnboardingTimeline.LOOP_END * 1000).toInt(), easing = LinearEasing),
            )
        }
    }
    return remember(time) { { time.value } }
}

/** Fade + slide (+ optional scale) once the clock reaches [start]; invisible, but keeping its space, before that. */
internal fun Modifier.sceneReveal(
    clock: SceneClock,
    start: Float,
    duration: Float = 0.5f,
    dy: Dp = 18.dp,
    dx: Dp = 0.dp,
    from: Float = 1f,
): Modifier = graphicsLayer {
    val p = clock.at(start, duration)
    val e = SceneEase.transform(p)
    alpha = p
    translationY = (1f - e) * dy.toPx()
    translationX = (1f - e) * dx.toPx()
    val s = from + (1f - from) * e
    scaleX = s
    scaleY = s
}

/** Breathing float of a few dp that fades in after [delay] and then repeats every [period] seconds. */
internal fun Modifier.sceneFloat(clock: SceneClock, delay: Float, period: Float, amplitude: Dp = 5.dp): Modifier =
    graphicsLayer {
        val t = clock() - delay
        if (t > 0f) {
            val ramp = min(1f, t)
            translationY = -amplitude.toPx() * 0.5f * (1f - cos(2f * PI.toFloat() * t / period)) * ramp
        }
    }

/** Decorative diagrams keep their proportions: text inside them grows with the user's scale, but only up to a limit. */
@Composable
internal fun CappedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, min(density.fontScale, 1.15f)), content)
}
