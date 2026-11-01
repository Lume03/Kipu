package com.kipu.app.feature.setup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.kipu.app.R
import com.kipu.app.feature.auth.domain.model.OtpFailure
import com.kipu.app.feature.auth.presentation.AuthCtaButton
import com.kipu.app.feature.auth.presentation.CappedFontScale
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.LightStatusBarIcons
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** How long the green "Verificado" state stays on screen before the setup opens. */
const val OTP_VERIFIED_HOLD_MILLIS = 950L

/** "Revisa tu correo": six cells that fill as the owner types the code mailed at registration. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetupOtpContent(
    email: String,
    state: SetupOtpState,
    onCode: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LightStatusBarIcons()
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    val error = state.failure != null && state.failure != OtpFailure.OFFLINE
    val compact = WindowInsets.isImeVisible
    val reducedMotion = rememberReducedMotionEnabled()
    val view = LocalView.current
    // A wrong code is shown in red for a moment (shake + haptic) and then the cells clear for the next try.
    var lastFull by remember { mutableStateOf("") }
    LaunchedEffect(state.code) { if (state.code.length == OTP_DIGITS) lastFull = state.code }
    val wrong = state.failure == OtpFailure.INVALID || state.failure == OtpFailure.LOCKED
    var flashing by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (!wrong) { flashing = false; shake.snapTo(0f); return@LaunchedEffect }
        flashing = true
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
        if (reducedMotion) delay(420)
        else listOf(-12f, 11f, -9f, 7f, -4f, 2f, 0f).forEach { shake.animateTo(it, tween(48)) }
        delay(320)
        flashing = false
    }
    val shownCode = if (flashing && state.code.isEmpty()) lastFull else state.code
    val cellError = flashing || (error && !wrong)
    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        run {
            OtpHero(compact)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
            ) {
                CappedFontScale {
                    Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Revisa tu correo", color = colors.primaryText, fontSize = if (compact) 22.sp else 27.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildAnnotatedString {
                                append("Enviamos un código de $OTP_DIGITS dígitos a\n")
                                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = colors.primaryText)) { append(email) }
                            },
                            color = colors.secondaryMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                BasicTextField(
                    value = state.code, onValueChange = onCode, enabled = !state.busy && state.lockedSeconds == 0,
                    singleLine = true, cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.focusRequester(focus).padding(horizontal = 20.dp).fillMaxWidth().testTag("setup-otp-field")
                        .semantics { contentDescription = "Código de confirmación de $OTP_DIGITS dígitos" },
                    decorationBox = {
                        Row(
                            Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value.dp.toPx() },
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            repeat(OTP_DIGITS) { i ->
                                OtpCell(
                                    char = shownCode.getOrNull(i),
                                    active = i == shownCode.length && !state.busy && state.lockedSeconds == 0 && !flashing,
                                    error = cellError, ok = state.busy, verified = state.verified, index = i, reducedMotion = reducedMotion,
                                    modifier = Modifier.weight(1f).widthIn(max = 54.dp).height(62.dp),
                                )
                            }
                        }
                    },
                )
                if (state.verified) VerifiedBadge(reducedMotion) else OtpMessage(state)
                if (!state.verified) OtpTimer(state, onResend)
                if (!compact) Row(
                    Modifier.clip(RoundedCornerShape(23.dp)).border(1.5.dp, colors.borderSubtle, RoundedCornerShape(23.dp)).background(colors.surfaceCard)
                        .clickable { openMailApp(context) }.heightIn(min = 46.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(Icons.Rounded.Mail, null, Modifier.size(18.dp), tint = colors.primaryText)
                    Text("Abrir correo", color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                }
                if (!compact) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¿Correo equivocado?", color = colors.secondaryMuted, style = MaterialTheme.typography.bodyMedium)
                    Text("Cámbialo", Modifier.heightIn(min = 44.dp).clickable(onClick = onChangeEmail).padding(horizontal = 6.dp, vertical = 12.dp),
                        color = colors.primaryDeep, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            AuthCtaButton(
                if (state.verified) "Verificado" else "Confirmar", "Verificando…", loading = state.busy && !state.verified,
                enabled = state.canSubmit, onClick = onSubmit,
            )
        }
    }
}

@Composable
private fun OtpHero(compact: Boolean) {
    val colors = rememberCalmEmeraldColors()
    Box(Modifier.fillMaxWidth().clip(SetupHeroShape).background(heroBrush(colors.isDark))) {
        Box(Modifier.align(Alignment.TopEnd).size(190.dp).background(Color.White.copy(alpha = 0.06f), CircleShape))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = if (compact) 28.dp else 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SetupLogo()
                Spacer(Modifier.width(8.dp))
                Text("Kipu", Modifier.weight(1f), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Confirmar correo", Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (!compact) Spacer(Modifier.height(22.dp))
            if (!compact) Box(Modifier.size(84.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mail, null, Modifier.size(42.dp), tint = Color(0xFF0F766E))
                Box(
                    Modifier.align(Alignment.TopEnd).size(26.dp).background(Color(0xFF0F766E), CircleShape).border(3.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = Color.White) }
            }
        }
    }
}

@Composable
private fun OtpCell(
    char: Char?,
    active: Boolean,
    error: Boolean,
    ok: Boolean,
    verified: Boolean,
    index: Int,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val shape = RoundedCornerShape(16.dp)
    val border = when {
        verified -> colors.incomeText
        error -> colors.expenseText
        active -> colors.primaryDeep
        char != null || ok -> colors.primaryDeep.copy(alpha = 0.55f)
        else -> colors.borderSubtle
    }
    val bg = when {
        verified -> colors.incomeBg
        error -> colors.expenseBg
        char != null -> colors.primaryDeep.copy(alpha = if (colors.isDark) 0.12f else 0.06f)
        else -> colors.surfaceCard
    }
    // On success the cells turn green one after another, left to right.
    val spec = if (reducedMotion) snap<Color>() else tween<Color>(220, delayMillis = if (verified) index * 60 else 0)
    val shownBorder by animateColorAsState(border, spec, label = "otp-cell-border")
    val shownBg by animateColorAsState(bg, spec, label = "otp-cell-bg")
    val textColor by animateColorAsState(
        when { verified -> colors.incomeText; error -> colors.expenseText; else -> colors.primaryText }, spec, label = "otp-cell-text",
    )
    Box(
        modifier.background(shownBg, shape).border(if (active || error || verified) 2.dp else 1.5.dp, shownBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (char != null) Text(char.toString(), color = textColor, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        else if (active) Box(Modifier.size(width = 2.dp, height = 28.dp).background(colors.primaryDeep))
    }
}

/** Green check that pops in and draws itself, next to "Verificado". */
@Composable
private fun VerifiedBadge(reducedMotion: Boolean) {
    val colors = rememberCalmEmeraldColors()
    val pop = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val stroke = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reducedMotion) return@LaunchedEffect
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
    }
    LaunchedEffect(Unit) {
        if (reducedMotion) return@LaunchedEffect
        delay(140)
        stroke.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    Row(
        Modifier.padding(horizontal = 24.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("setup-otp-verified"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val circle = colors.incomeText
        val mark = colors.incomeBg
        Canvas(Modifier.size(22.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            drawCircle(circle)
            val w = size.width
            val a = Offset(w * 0.28f, w * 0.52f)
            val b = Offset(w * 0.44f, w * 0.67f)
            val c = Offset(w * 0.73f, w * 0.36f)
            val p = stroke.value
            val path = Path().apply {
                moveTo(a.x, a.y)
                if (p < 0.4f) lineTo(a.x + (b.x - a.x) * p / 0.4f, a.y + (b.y - a.y) * p / 0.4f)
                else {
                    lineTo(b.x, b.y)
                    val q = (p - 0.4f) / 0.6f
                    lineTo(b.x + (c.x - b.x) * q, b.y + (c.y - b.y) * q)
                }
            }
            if (p > 0f) drawPath(path, mark, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text("Verificado", Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) }, color = colors.incomeText, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@Composable
private fun OtpMessage(state: SetupOtpState) {
    val colors = rememberCalmEmeraldColors()
    val (text, tone) = when (state.failure) {
        OtpFailure.INVALID -> (state.attemptsLeft?.let {
            if (it > 0) "Código incorrecto. Te quedan $it intentos." else "Código incorrecto."
        } ?: "Código incorrecto.") to colors.expenseText
        OtpFailure.EXPIRED -> "Este código venció. Pide uno nuevo." to colors.warningText
        OtpFailure.LOCKED -> "Demasiados intentos. Vuelve a probar en ${formatClock(state.lockedSeconds)}." to colors.expenseText
        OtpFailure.OFFLINE -> "Sin conexión. Conéctate para confirmar tu código." to colors.warningText
        OtpFailure.UNKNOWN -> "No pudimos comprobar el código. Inténtalo de nuevo." to colors.expenseText
        null -> if (state.notice == OtpNotice.RESENT) "Te enviamos un código nuevo." to colors.primaryDeep else return
    }
    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(
            when (state.failure) { OtpFailure.OFFLINE -> Icons.Rounded.CloudOff; null -> Icons.Rounded.Check; else -> Icons.Rounded.Warning },
            null, Modifier.size(16.dp), tint = tone,
        )
        Text(text, color = tone, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OtpTimer(state: SetupOtpState, onResend: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    if (state.resendSeconds > 0) {
        Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp), tint = colors.secondaryMuted)
            Text("Podrás pedir otro en ${formatClock(state.resendSeconds)}", color = colors.secondaryMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    } else if (state.lockedSeconds == 0) {
        Row(
            Modifier.heightIn(min = 44.dp).clickable(onClick = onResend).padding(horizontal = 12.dp).testTag("setup-otp-resend"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp), tint = colors.primaryDeep)
            Text("Reenviar código", color = colors.primaryDeep, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

internal fun formatClock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun openMailApp(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { /* no mail app installed */ }
}
