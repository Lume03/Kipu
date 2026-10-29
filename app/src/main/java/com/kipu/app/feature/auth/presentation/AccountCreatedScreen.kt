package com.kipu.app.feature.auth.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.ui.theme.LightStatusBarIcons
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlin.math.hypot
import kotlin.math.max

private val RevealEase = CubicBezierEasing(0.5f, 0f, 0.15f, 1f)

/**
 * Grows (or, with a shrinking [progress], closes) a circle from [center]; only what is inside it is drawn.
 * [progress] is read while drawing, so the animation never recomposes.
 */
internal fun Modifier.circleReveal(progress: () -> Float, center: (Size) -> Offset): Modifier = drawWithCache {
    val path = Path()
    onDrawWithContent {
        val p = progress()
        when {
            p <= 0f -> Unit
            p >= 1f -> drawContent()
            else -> {
                val c = center(size)
                val reach = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y))
                path.reset()
                path.addOval(Rect(c, reach * p))
                clipPath(path) { this@onDrawWithContent.drawContent() }
            }
        }
    }
}

/** Where the circle that floods the screen is born: roughly where the "Crear cuenta" button sat. */
private const val ButtonCenterY = 0.765f

/**
 * "¡Cuenta creada!": the quipu is woven in white over the brand teal, its knots gather into a seal with a check,
 * and the copy and the "Continuar" button arrive. With animations disabled it appears already composed.
 */
@Composable
internal fun AccountCreatedScreen(email: String, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val clock = rememberSceneClock(active = true)
    val tl = AccountCreatedTimeline
    var consumed by remember { mutableStateOf(false) }
    val ctaReady by remember(clock) { derivedStateOf { clock() >= tl.CTA } }
    LightStatusBarIcons()
    // The account exists already; going back would only show a form that cannot be submitted again.
    BackHandler(true) {}
    BoxWithConstraints(
        modifier.fillMaxSize().testTag("auth-account-created")
            .circleReveal({ RevealEase.transform(clock.at(0f, tl.REVEAL)) }, { Offset(it.width / 2f, it.height * ButtonCenterY) })
            .drawBehind { drawRect(celebrationBrush(colors.isDark, size.width, size.height)) },
    ) {
        val sealY = maxHeight * 0.44f
        Canvas(Modifier.fillMaxSize()) {
            val t = clock()
            val s = size.width / QUIPU_REF_WIDTH
            withTransform({ scale(s, s, Offset.Zero) }) {
                val seal = Offset(QUIPU_REF_WIDTH / 2f, size.height * 0.44f / s)
                drawAmbientBeads(t)
                drawQuipuField(
                    t, tl, cordsAlpha = 1f - clock.at(tl.GATHER_START, tl.CORDS_FADE),
                    gather = { order -> OnboardingTimeline.progress(t, tl.GATHER_START + order * tl.GATHER_STEP, tl.GATHER_DURATION) },
                    target = seal,
                )
                drawAccountSeal(t, seal)
            }
        }
        CappedFontScale {
            Column(
                Modifier.fillMaxWidth().offset(y = sealY + 96.dp).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "¡Cuenta creada!", Modifier.sceneReveal(clock, tl.TITLE, 0.6f).semantics { heading() },
                    color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Te damos la bienvenida a Kipu.", Modifier.sceneReveal(clock, tl.SUBTITLE, 0.6f),
                    color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge, fontSize = 17.sp,
                )
                Spacer(Modifier.height(20.dp))
                EmailPill(email, Modifier.sceneReveal(clock, tl.EMAIL, 0.6f))
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Siguiente paso: elegir tu plan", Modifier.sceneReveal(clock, tl.NEXT, 0.6f),
                    color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { if (!consumed) { consumed = true; onContinue() } },
                    enabled = ctaReady && !consumed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).sceneReveal(clock, tl.CTA, 0.6f)
                        .testTag("auth-created-continue"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, contentColor = Color(0xFF0B5F58),
                        disabledContainerColor = Color.White, disabledContentColor = Color(0xFF0B5F58),
                    ),
                ) {
                    Text("Continuar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

/** The address the account was created with, as a translucent pill on the teal background. */
@Composable
internal fun EmailPill(email: String, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Email, null, Modifier.size(16.dp), tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(email, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
