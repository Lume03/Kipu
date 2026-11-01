package com.kipu.app.feature.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import com.kipu.app.R
import com.kipu.app.feature.auth.presentation.CappedFontScale
import com.kipu.app.ui.theme.LightStatusBarIcons
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** The three numbered steps of the setup; the summary and the code come after them. */
enum class SetupStepId(val label: String) { ACCOUNT("Cuenta"), CATEGORIES("Categorías"), PLAN("Plan") }

private val HeroTealTop = Color(0xFF0A5A54)
private val HeroTealMid = Color(0xFF0F766E)
private val HeroTealEnd = Color(0xFF2C9A8E)

/** Same wide ellipse as the sign-in header. */
internal object SetupHeroShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(-0.9f * size.width, -size.height, 1.9f * size.width, size.height)) })
}

internal fun heroBrush(dark: Boolean): Brush = if (dark) {
    Brush.linearGradient(listOf(Color(0xFF062D27), Color(0xFF0B463E), Color(0xFF0F6B62)))
} else {
    Brush.linearGradient(listOf(HeroTealTop, HeroTealMid, HeroTealEnd))
}

/**
 * Screen frame shared by the setup steps: the teal header with the quipu rope as progress bar, the title, the
 * body and a footer pinned above the keyboard. The body only scrolls when it does not fit, and never past its
 * content: the footer sits below it instead of covering it. [current] is the knot being worked on; with
 * [allTied] every knot is already tied (summary).
 */
@Composable
internal fun SetupFrame(
    current: SetupStepId?,
    allTied: Boolean,
    pill: String,
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    LightStatusBarIcons()
    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        SetupHero(current, allTied, pill, onBack)
        val scroll = rememberScrollState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CappedFontScale {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            title, Modifier.semantics { heading() }, color = colors.primaryText,
                            fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, color = colors.secondaryMuted, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                content()
            }
            // Hints that more content waits above the footer; gone once the end is reached.
            if (scroll.canScrollForward) Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))),
            )
        }
        Column(
            Modifier.fillMaxWidth().background(colors.background).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = footer,
        )
    }
}

@Composable
private fun SetupHero(current: SetupStepId?, allTied: Boolean, pill: String, onBack: (() -> Unit)?) {
    val colors = rememberCalmEmeraldColors()
    Box(
        Modifier.fillMaxWidth().clip(SetupHeroShape).background(heroBrush(colors.isDark)),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 0.dp).size(190.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape),
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 20.dp, top = 4.dp, bottom = 26.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClickLabel = "Volver", onClick = onBack).testTag("setup-back"),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Color.White) }
                } else Spacer(Modifier.width(12.dp))
                Spacer(Modifier.width(10.dp))
                SetupLogo()
                Spacer(Modifier.width(8.dp))
                Text("Kipu", Modifier.weight(1f), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    pill, Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
            QuipuRope(current, allTied)
        }
    }
}

/** Kipu mark on the teal header, without a frame around it. */
@Composable
internal fun SetupLogo() {
    Image(painterResource(R.drawable.kipu_logo), null, Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)))
}

/**
 * Three knots on a cord: tied ones turn white with a check, the current one breathes, the rest wait dashed.
 * The cord stops short of each knot so it never shows through one.
 */
@Composable
private fun QuipuRope(current: SetupStepId?, allTied: Boolean) {
    val steps = SetupStepId.entries
    val index = if (allTied) steps.size else current?.ordinal ?: 0
    val reducedMotion = rememberReducedMotionEnabled()
    // Starts one knot behind so arriving at a step ties the previous knot and fills its cord in view.
    val progress = remember { Animatable((index - 1).coerceAtLeast(0).toFloat()) }
    LaunchedEffect(index, reducedMotion) {
        if (reducedMotion) progress.snapTo(index.toFloat())
        else progress.animateTo(index.toFloat(), tween(560, delayMillis = 120, easing = FastOutSlowInEasing))
    }
    val breathing = !reducedMotion && index < steps.size
    val breath = rememberInfiniteTransition(label = "rope-breath").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart), label = "rope-breath-phase",
    )
    val teal = Color(0xFF0F766E)
    Column(Modifier.fillMaxWidth().semantics {
        contentDescription = if (allTied) "Los tres pasos completos"
        else "Paso ${index + 1} de ${steps.size}: ${steps[index.coerceAtMost(steps.size - 1)].label}"
    }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val fill = progress.value
            val centers = List(3) { size.width * (it * 2 + 1) / 6f }
            val y = size.height / 2f
            val gap = 4.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 7.dp.toPx()))
            for (i in 0 until 2) {
                val start = centers[i] + knotRadius(i, fill) + gap
                val end = centers[i + 1] - knotRadius(i + 1, fill) - gap
                if (end <= start) continue
                val tied = (fill - i).coerceIn(0f, 1f)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(start, y), Offset(end, y), 3.dp.toPx(), StrokeCap.Round, dash)
                if (tied > 0f) drawLine(Color.White, Offset(start, y), Offset(start + (end - start) * tied, y), 3.5.dp.toPx(), StrokeCap.Round)
            }
            centers.forEachIndexed { i, x -> drawKnot(i, fill, x, y, teal, if (breathing) breath.value else null) }
        }
        Row(Modifier.fillMaxWidth()) {
            steps.forEachIndexed { i, step ->
                Text(
                    step.label, Modifier.weight(1f), color = Color.White.copy(alpha = if (i <= index) 1f else 0.62f),
                    fontSize = 12.sp, fontWeight = if (i == index) FontWeight.ExtraBold else FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

/** 0 = waiting, 1 = current, 2 = tied; fractional while the rope animates between steps. */
private fun knotState(i: Int, fill: Float): Float = (fill - i + 1f).coerceIn(0f, 2f)

private fun DrawScope.knotRadius(i: Int, fill: Float): Float {
    val s = knotState(i, fill)
    return if (s <= 1f) lerpPx(9.dp.toPx(), 13.dp.toPx(), s) else lerpPx(13.dp.toPx(), 12.dp.toPx(), s - 1f)
}

private fun lerpPx(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun DrawScope.drawKnot(i: Int, fill: Float, x: Float, y: Float, teal: Color, breath: Float?) {
    val s = knotState(i, fill)
    val center = Offset(x, y)
    val r = knotRadius(i, fill)
    when {
        s > 1f -> {
            // Tying: the dot shrinks away and the check draws itself.
            val t = s - 1f
            drawCircle(Color.White, r, center)
            if (t < 1f) drawCircle(teal, 5.dp.toPx() * (1f - t), center)
            if (t > 0.2f) {
                val p = ((t - 0.2f) / 0.8f).coerceIn(0f, 1f)
                val a = Offset(x - 5.dp.toPx(), y)
                val b = Offset(x - 1.5.dp.toPx(), y + 3.5.dp.toPx())
                val c = Offset(x + 5.5.dp.toPx(), y - 3.5.dp.toPx())
                val path = Path().apply {
                    moveTo(a.x, a.y)
                    if (p < 0.4f) lineTo(a.x + (b.x - a.x) * p / 0.4f, a.y + (b.y - a.y) * p / 0.4f)
                    else {
                        lineTo(b.x, b.y)
                        val q = (p - 0.4f) / 0.6f
                        lineTo(b.x + (c.x - b.x) * q, b.y + (c.y - b.y) * q)
                    }
                }
                drawPath(path, teal, style = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        s == 1f -> {
            // Current knot: a soft ring that grows and fades, like a breath.
            if (breath != null) {
                drawCircle(Color.White.copy(alpha = 0.5f * (1f - breath)), r + 3.dp.toPx() + 6.dp.toPx() * breath, center, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(Color.White.copy(alpha = 0.4f), r + 5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
            drawCircle(Color.White, r, center)
            drawCircle(teal, 5.dp.toPx(), center)
        }
        else -> {
            // Waiting (or becoming current): an opaque disc so the cord never shows through.
            drawCircle(lerp(HeroTealMid, Color.White, 0.16f + 0.84f * s), r, center)
            drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * s), r, center, style = Stroke(2.dp.toPx()))
            if (s > 0f) drawCircle(teal, 5.dp.toPx() * s, center)
        }
    }
}

/** Brand tile used by the setup lists: icon on a tinted rounded square. */
@Composable
internal fun SetupIconTile(icon: ImageVector, tint: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val shown = if (colors.isDark) androidx.compose.ui.graphics.lerp(tint, Color.White, 0.35f) else tint
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(shown.copy(alpha = if (colors.isDark) 0.2f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.5f), tint = shown) }
}

/** A pastel family: soft background and a darker icon of the same hue. */
internal data class PastelTone(val soft: Color, val deep: Color)

/** Twelve harmonious families, one per catalog slot, so every category keeps its colour wherever it is shown. */
private val CategoryTones = listOf(
    PastelTone(Color(0xFFFFEDD5), Color(0xFFC2410C)), // peach
    PastelTone(Color(0xFFE0F2FE), Color(0xFF0369A1)), // sky
    PastelTone(Color(0xFFEDE9FE), Color(0xFF6D28D9)), // violet
    PastelTone(Color(0xFFFCE7F3), Color(0xFFBE185D)), // rose
    PastelTone(Color(0xFFDCFCE7), Color(0xFF15803D)), // mint
    PastelTone(Color(0xFFFEF3C7), Color(0xFFB45309)), // amber
    PastelTone(Color(0xFFFAE8FF), Color(0xFFA21CAF)), // orchid
    PastelTone(Color(0xFFCCFBF1), Color(0xFF0F766E)), // teal
    PastelTone(Color(0xFFE0E7FF), Color(0xFF4338CA)), // indigo
    PastelTone(Color(0xFFDBEAFE), Color(0xFF1D4ED8)), // blue
    PastelTone(Color(0xFFECFCCB), Color(0xFF4D7C0F)), // lime
    PastelTone(Color(0xFFE2E8F0), Color(0xFF475569)), // slate
)

internal fun categoryTone(index: Int): PastelTone = CategoryTones[index.coerceAtLeast(0) % CategoryTones.size]

/** Category tile: pastel square with the icon in the deeper shade; in dark mode the hue is kept but dimmed. */
@Composable
internal fun SetupPastelTile(icon: ImageVector, tone: PastelTone, size: Dp = 40.dp, muted: Boolean = false) {
    val colors = rememberCalmEmeraldColors()
    val bg = when {
        muted -> colors.secondaryMuted.copy(alpha = 0.12f)
        colors.isDark -> tone.deep.copy(alpha = 0.28f)
        else -> tone.soft
    }
    val fg = when {
        muted -> colors.secondaryMuted
        colors.isDark -> lerp(tone.deep, tone.soft, 0.62f)
        else -> tone.deep
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = fg)
    }
}

/** Rounded card with the setup's border: selected cards get the brand ring. */
@Composable
internal fun SetupCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dashed: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val shape = RoundedCornerShape(20.dp)
    val border = if (selected) colors.primaryDeep else colors.borderSubtle
    Box(
        modifier.clip(shape)
            .background(if (dashed) Color.Transparent else colors.surfaceCard)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) { content() }
}

/** Inline notice used for errors and offline hints. */
@Composable
internal fun SetupNotice(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    val colors = rememberCalmEmeraldColors()
    val bg = if (warning) colors.warningBg else colors.expenseBg
    val border = if (warning) colors.warningBorder else colors.expenseBorder
    val fg = if (warning) colors.warningText else colors.expenseText
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).border(1.5.dp, border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = fg)
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Check mark badge for selected cards. */
@Composable
internal fun SetupCheck(selected: Boolean, size: Dp = 24.dp) {
    val colors = rememberCalmEmeraldColors()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .then(if (selected) Modifier.background(colors.primaryDeep) else Modifier.border(2.dp, colors.borderSubtle, RoundedCornerShape(size * 0.32f))),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(size * 0.66f), tint = colors.onPrimaryDeep) }
}
