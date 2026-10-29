package com.kipu.app.feature.auth.presentation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Reference width (dp) of the quipu drawings; every coordinate below lives in this 390-wide space. */
internal const val QUIPU_REF_WIDTH = 390f

private val DrawEase = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)
private val GatherEase = CubicBezierEasing(0.55f, 0f, 0.85f, 0.4f)
private val OutEase = CubicBezierEasing(0.1f, 0.7f, 0.2f, 1f)
private val SoftOut = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

private fun progress(t: Float, start: Float, duration: Float) = OnboardingTimeline.progress(t, start, duration)

/** Timings (seconds) of the strokes that weave the cords; both celebration screens share the drawing. */
internal interface FieldTiming {
    val mainStart: Float
    val mainDuration: Float
    val pendantDuration: Float
    val popDuration: Float
    val rightCapStart: Float
    fun pendantStart(cord: Int): Float
    fun knotPop(cord: Int, index: Int): Float
}

/** "¡Cuenta creada!": the quipu is woven, its knots gather into one seal, then the copy arrives. */
internal object AccountCreatedTimeline : FieldTiming {
    const val REVEAL = 0.95f

    override val mainStart = 0.75f
    override val mainDuration = 0.7f
    override val pendantDuration = 0.6f
    override val popDuration = 0.5f
    override val rightCapStart = 1.4f
    override fun pendantStart(cord: Int) = 0.95f + cord * 0.09f
    override fun knotPop(cord: Int, index: Int) = 1.35f + cord * 0.11f + index * 0.1f

    const val GATHER_START = 3.05f
    const val GATHER_STEP = 0.015f
    const val GATHER_DURATION = 0.66f
    const val CORDS_FADE = 0.5f

    const val DISC = 3.85f
    const val DISC_DURATION = 0.65f
    const val WAVE_ONE = 3.9f
    const val WAVE_TWO = 4.1f
    const val BURST = 3.92f
    const val ORBIT = 4.5f
    const val CHECK = 4.25f
    const val CHECK_DURATION = 0.5f

    const val TITLE = 4.5f
    const val SUBTITLE = 4.65f
    const val EMAIL = 4.8f
    const val NEXT = 5.05f
    const val CTA = 5.2f
}

/** "¡Hola de nuevo!": the cords hang, a light wave waits while the home loads, then everything closes on the logo. */
internal object WelcomeBackTimeline : FieldTiming {
    const val REVEAL = 0.95f

    override val mainStart = 0.7f
    override val mainDuration = 0.55f
    override val pendantDuration = 0.45f
    override val popDuration = 0.4f
    override val rightCapStart = 1.2f
    override fun pendantStart(cord: Int) = 0.85f + cord * 0.06f
    override fun knotPop(cord: Int, index: Int) = 1.05f + cord * 0.07f + index * 0.06f

    const val TILE = 0.95f
    const val TITLE = 1.1f
    const val EMAIL = 1.25f
    const val CAPTION = 1.4f

    const val WAVE_START = 1.7f
    const val WAVE_CORD_STEP = 0.2f
    const val WAVE_KNOT_STEP = 0.06f
    const val WAVE_PERIOD = 2.6f

    // Seconds since the app reported it is ready.
    const val EXIT_CORDS_FADE = 0f
    const val EXIT_CORDS_FADE_DURATION = 0.35f
    const val EXIT_GATHER = 0.05f
    const val EXIT_GATHER_STEP = 0.01f
    const val EXIT_GATHER_DURATION = 0.5f
    const val EXIT_TILE_PULSE = 0.35f
    const val EXIT_LOGO_FADE = 0.3f
    const val EXIT_CHECK = 0.4f
    const val EXIT_CAPTION_OUT = 0.2f
    const val EXIT_CAPTION_IN = 0.3f
    const val EXIT_IRIS = 0.85f
    const val EXIT_IRIS_DURATION = 0.6f
    const val EXIT_TOTAL = EXIT_IRIS + EXIT_IRIS_DURATION
}

private class KnotRef(val x: Float, val y: Float, val radius: Float, val cord: Int, val index: Int, val order: Int)

/** Seven cords hanging from a slightly sagging main cord, in the 390-wide reference space. */
private object QuipuField {
    val xs = floatArrayOf(50f, 100f, 150f, 195f, 240f, 290f, 340f)
    val lengths = floatArrayOf(170f, 120f, 210f, 150f, 200f, 125f, 180f)
    private val fractions = arrayOf(
        floatArrayOf(0.36f, 0.64f, 0.9f), floatArrayOf(0.5f, 0.82f), floatArrayOf(0.3f, 0.55f, 0.78f, 0.94f),
        floatArrayOf(0.42f, 0.8f), floatArrayOf(0.34f, 0.62f, 0.9f), floatArrayOf(0.55f, 0.86f),
        floatArrayOf(0.38f, 0.68f, 0.94f),
    )
    val mainStart = Offset(24f, 118f)
    val mainControl = Offset(195f, 142f)
    val mainEnd = Offset(366f, 114f)

    val main: Path = Path().apply {
        moveTo(mainStart.x, mainStart.y)
        quadraticBezierTo(mainControl.x, mainControl.y, mainEnd.x, mainEnd.y)
    }

    private fun mainY(x: Float): Float {
        var lo = 0f
        var hi = 1f
        repeat(40) {
            val t = (lo + hi) / 2f
            val bx = (1 - t) * (1 - t) * mainStart.x + 2 * (1 - t) * t * mainControl.x + t * t * mainEnd.x
            if (bx < x) lo = t else hi = t
        }
        val t = (lo + hi) / 2f
        return (1 - t) * (1 - t) * mainStart.y + 2 * (1 - t) * t * mainControl.y + t * t * mainEnd.y
    }

    fun bow(cord: Int) = if (cord % 2 == 0) 4f else -4f
    val tops = FloatArray(xs.size) { mainY(xs[it]) }

    val pendants: List<Path> = xs.indices.map { i ->
        Path().apply {
            moveTo(xs[i], tops[i])
            quadraticBezierTo(xs[i] + bow(i), tops[i] + lengths[i] / 2f, xs[i], tops[i] + lengths[i])
        }
    }

    val knots: List<KnotRef> = buildList {
        var order = 0
        for (i in xs.indices) for (j in fractions[i].indices) {
            add(KnotRef(xs[i] + bow(i) * 0.5f, tops[i] + lengths[i] * fractions[i][j], 5.6f - j * 0.6f, i, j, order++))
        }
    }
}

/** The shorter cords that hang from the top of the sign-in header. */
private object QuipuHeroCords {
    val xs = floatArrayOf(214f, 240f, 266f, 292f, 318f, 344f, 370f)
    val lengths = floatArrayOf(150f, 104f, 188f, 124f, 168f, 112f, 146f)
    private val fractions = arrayOf(
        floatArrayOf(0.34f, 0.62f, 0.9f), floatArrayOf(0.5f, 0.8f), floatArrayOf(0.3f, 0.55f, 0.78f, 0.94f),
        floatArrayOf(0.44f, 0.84f), floatArrayOf(0.36f, 0.64f, 0.92f), floatArrayOf(0.58f, 0.88f),
        floatArrayOf(0.4f, 0.7f, 0.95f),
    )
    val start = Offset(150f, 38f)
    val end = Offset(410f, 30f)
    val main: Path = Path().apply {
        moveTo(start.x, start.y)
        quadraticBezierTo(270f, 56f, end.x, end.y)
    }
    fun top(x: Float) = 44f - (x - 150f) * 0.05f
    val pendants: List<Path> = xs.indices.map { i ->
        Path().apply {
            val y0 = top(xs[i])
            moveTo(xs[i], y0)
            // Same shape as the design: a relative "q 3 L/2 0 L".
            quadraticBezierTo(xs[i] + 3f, y0 + lengths[i] / 2f, xs[i], y0 + lengths[i])
        }
    }
    val knotCount = fractions.map { it.size }
    fun fraction(cord: Int, index: Int) = fractions[cord][index]
}

/** Hanging cords of the header: they sway a couple of degrees and their knots twinkle in turn. Seconds in [t]. */
internal fun DrawScope.drawHeroCords(t: Float) {
    // The drawing is authored in dp: scale it by the screen density, and a little by the width on tablets.
    // The shorter register header gets proportionally shorter cords so they never reach the selector.
    val byWidth = (size.width / density / QUIPU_REF_WIDTH).coerceIn(0.9f, 1.25f)
    val byHeight = (0.78f * size.height / density / 232f).coerceAtLeast(0.6f)
    val scale = minOf(byWidth, byHeight) * density
    val white = Color.White
    withTransform({
        translate(size.width - QUIPU_REF_WIDTH * scale, 0f)
        scale(scale, scale, Offset.Zero)
    }) {
        drawPath(QuipuHeroCords.main, white.copy(alpha = 0.55f), style = Stroke(3.2f, cap = StrokeCap.Round))
        for (i in QuipuHeroCords.xs.indices) {
            val x = QuipuHeroCords.xs[i]
            val y0 = QuipuHeroCords.top(x)
            val angle = 2.2f * sin(PI.toFloat() * (t + 1.3f * i) / (5.2f + 0.7f * i))
            rotate(angle, Offset(x, y0)) {
                drawPath(QuipuHeroCords.pendants[i], white.copy(alpha = 0.5f), style = Stroke(2f, cap = StrokeCap.Round))
                for (j in 0 until QuipuHeroCords.knotCount[i]) {
                    val twinkle = 0.65f + 0.3f * cos(2f * PI.toFloat() * (t - (i * 0.45f + j * 0.9f)) / 4.6f)
                    drawCircle(white.copy(alpha = twinkle), 4.4f - j * 0.5f,
                        Offset(x + 1f, y0 + QuipuHeroCords.lengths[i] * QuipuHeroCords.fraction(i, j)))
                }
                drawCircle(white.copy(alpha = 0.7f), 2.6f, Offset(x, y0 + QuipuHeroCords.lengths[i]))
            }
        }
        drawCircle(white.copy(alpha = 0.9f), 5f, QuipuHeroCords.start)
        drawCircle(white.copy(alpha = 0.9f), 5f, QuipuHeroCords.end)
    }
}

private fun popScale(p: Float): Float = when {
    p <= 0f -> 0f
    p < 0.6f -> lerp(0f, 1.55f, OutEase.transform(p / 0.6f))
    else -> lerp(1.55f, 1f, (p - 0.6f) / 0.4f)
}

/** Overshoots like a spring: 0 → 1.08 → 1. */
internal fun easeOutBack(p: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = p - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private fun DrawScope.drawPartial(path: Path, p: Float, color: Color, width: Float, scratch: Path, measure: PathMeasure) {
    if (p <= 0f) return
    scratch.reset()
    measure.setPath(path, false)
    measure.getSegment(0f, measure.length * p, scratch, true)
    drawPath(scratch, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Weaves the cords and their knots. Everything is drawn in the 390-wide reference space (apply the scale first).
 * [gather] gives, per knot, how far it has travelled to [target] (0..1, before easing); [halo] optionally gives a
 * light-wave phase (0..1, or a negative value for none).
 */
internal fun DrawScope.drawQuipuField(
    t: Float,
    timing: FieldTiming,
    cordsAlpha: Float,
    gather: (order: Int) -> Float,
    target: Offset,
    halo: ((cord: Int, index: Int) -> Float)? = null,
) {
    val white = Color.White
    val scratch = Path()
    val measure = PathMeasure()
    if (cordsAlpha > 0f) {
        drawPartial(QuipuField.main, DrawEase.transform(progress(t, timing.mainStart, timing.mainDuration)),
            white.copy(alpha = 0.7f * cordsAlpha), 3.4f, scratch, measure)
        for (i in QuipuField.xs.indices) {
            drawPartial(QuipuField.pendants[i],
                DrawEase.transform(progress(t, timing.pendantStart(i), timing.pendantDuration)),
                white.copy(alpha = 0.6f * cordsAlpha), 2.2f, scratch, measure)
        }
        val leftCap = popScale(progress(t, timing.mainStart, 0.5f))
        val rightCap = popScale(progress(t, timing.rightCapStart, 0.5f))
        if (leftCap > 0f) drawCircle(white.copy(alpha = cordsAlpha), 5.5f * leftCap, QuipuField.mainStart)
        if (rightCap > 0f) drawCircle(white.copy(alpha = cordsAlpha), 5.5f * rightCap, QuipuField.mainEnd)
    }
    for (knot in QuipuField.knots) {
        val pop = progress(t, timing.knotPop(knot.cord, knot.index), timing.popDuration)
        val scale = popScale(pop)
        if (scale <= 0f) continue
        val g = GatherEase.transform(gather(knot.order).coerceIn(0f, 1f))
        val at = Offset(lerp(knot.x, target.x, g), lerp(knot.y, target.y, g))
        val alpha = if (g < 0.78f) 1f else 1f - (g - 0.78f) / 0.22f
        val shrink = lerp(1f, 0.5f, g)
        val ring = progress(t, timing.knotPop(knot.cord, knot.index), 0.8f)
        if (ring in 0.001f..0.999f) {
            val ringAlpha = if (ring < 0.15f) ring / 0.15f * 0.85f else 0.85f * (1f - (ring - 0.15f) / 0.85f)
            drawCircle(white.copy(alpha = ringAlpha * alpha), 7f * lerp(0.4f, 2.6f, OutEase.transform(ring)) * shrink, at,
                style = Stroke(1.6f))
        }
        val phase = halo?.invoke(knot.cord, knot.index) ?: -1f
        if (phase >= 0f) {
            val haloAlpha = when {
                phase < 0.18f -> phase / 0.18f * 0.95f
                phase < 0.45f -> 0.95f * (1f - (phase - 0.18f) / 0.27f)
                else -> 0f
            }
            val haloScale = when {
                phase < 0.18f -> lerp(0.7f, 1.9f, phase / 0.18f)
                phase < 0.45f -> lerp(1.9f, 2.6f, (phase - 0.18f) / 0.27f)
                else -> 2.6f
            }
            if (haloAlpha > 0f) drawCircle(white.copy(alpha = haloAlpha * alpha), (knot.radius + 1f) * haloScale * shrink, at)
        }
        drawCircle(white.copy(alpha = alpha), knot.radius * scale * shrink, at)
    }
}

/** Slow translucent beads that rise behind everything; purely atmospheric. */
internal fun DrawScope.drawAmbientBeads(t: Float) {
    for (b in AmbientBeads) {
        val phase = (((t + b.offset) % b.period) + b.period) % b.period / b.period
        drawCircle(Color.White.copy(alpha = 0.22f), b.radius, Offset(b.x, 860f - phase * 920f))
    }
}

private class Bead(val x: Float, val radius: Float, val period: Float, val offset: Float)

private val AmbientBeads = listOf(
    Bead(30f, 3f, 11f, 0f), Bead(82f, 2f, 14f, 1.7f), Bead(140f, 4f, 9.5f, 3.4f), Bead(210f, 2.5f, 12.5f, 5.1f),
    Bead(262f, 3.5f, 10f, 6.8f), Bead(318f, 2f, 15f, 8.5f), Bead(362f, 3f, 11.5f, 10.2f), Bead(110f, 2f, 13f, 11.9f),
    Bead(290f, 2.5f, 9f, 13.6f),
)

private class Spark(val angle: Float, val distance: Float, val radius: Float, val color: Color, val delay: Float)

private val SparkColors = listOf(Color.White, Color(0xFFFDE68A), Color(0xFF99F6E4), Color.White)

private val Sparks = List(18) { k ->
    val angle = (k / 18f) * 2f * PI.toFloat() + if (k % 2 == 1) 0.18f else 0f
    Spark(angle, 120f + (k * 37) % 70, 3f + (k * 5) % 5, SparkColors[k % 4], (k % 3) * 0.03f)
}

private val SealTeal = Color(0xFF0F766E)

/** The seal of "¡Cuenta creada!": halo, ripples, a dotted orbit, a white disc, a drawn check and a burst of beads. */
internal fun DrawScope.drawAccountSeal(t: Float, center: Offset) {
    val tl = AccountCreatedTimeline
    val glow = progress(t, tl.DISC, 0.9f)
    if (glow > 0f) {
        val breathe = if (t > 4.8f) 1f + 0.08f * sin(PI.toFloat() * (t - 4.8f) / 4.5f) else 1f
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * glow), Color.Transparent), center, 150f * breathe),
            150f * breathe, center)
    }
    for ((start, duration, width) in listOf(Triple(tl.WAVE_ONE, 1.3f, 2.4f), Triple(tl.WAVE_TWO, 1.5f, 1.6f))) {
        val p = progress(t, start, duration)
        if (p in 0.001f..0.999f) {
            val alpha = if (p < 0.12f) p / 0.12f * 0.75f else 0.75f * (1f - (p - 0.12f) / 0.88f)
            drawCircle(Color.White.copy(alpha = alpha), 54f * lerp(1f, 3.1f, SoftOut.transform(p)), center, style = Stroke(width))
        }
    }
    val orbit = progress(t, tl.ORBIT, 0.8f)
    if (orbit > 0f) {
        rotate(((t - tl.ORBIT) / 26f) * 360f, center) {
            drawCircle(Color.White.copy(alpha = 0.6f * orbit), 70f, center,
                style = Stroke(3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 11f))))
        }
    }
    val disc = easeOutBack(progress(t, tl.DISC, tl.DISC_DURATION))
    if (disc > 0f) {
        drawCircle(Color(0x33042824), 56f * disc, center + Offset(0f, 12f))
        drawCircle(Color.White, 54f * disc, center)
    }
    val check = DrawEase.transform(progress(t, tl.CHECK, tl.CHECK_DURATION))
    if (check > 0f) {
        val path = Path().apply {
            moveTo(center.x - 22f, center.y + 3f)
            lineTo(center.x - 6f, center.y + 19f)
            lineTo(center.x + 24f, center.y - 17f)
        }
        drawPartial(path, check, SealTeal, 8f, Path(), PathMeasure())
    }
    for (spark in Sparks) {
        val p = progress(t, tl.BURST + spark.delay, 1.1f)
        if (p <= 0f || p >= 1f) continue
        val alpha = if (p < 0.12f) p / 0.12f else 1f - (p - 0.12f) / 0.88f
        val travel = OutEase.transform(p) * spark.distance
        drawCircle(spark.color.copy(alpha = alpha), spark.radius * lerp(0.4f, 1f, p),
            center + Offset(cos(spark.angle) * travel, sin(spark.angle) * travel))
    }
}

/** Brush behind both celebration screens; dark theme keeps the brand teal but deeper. */
internal fun celebrationBrush(dark: Boolean, width: Float, height: Float): Brush {
    val colors = if (dark) {
        listOf(Color(0xFF052E2A), Color(0xFF0B463E), Color(0xFF0F766E))
    } else {
        listOf(Color(0xFF0A5A54), Color(0xFF0F766E), Color(0xFF2C9A8E))
    }
    return Brush.linearGradient(colors, Offset.Zero, Offset(width * 0.27f, height))
}
