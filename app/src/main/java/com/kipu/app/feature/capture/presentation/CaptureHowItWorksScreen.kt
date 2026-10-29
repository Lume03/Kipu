package com.kipu.app.feature.capture.presentation

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.KipuChipTone
import com.kipu.app.ui.component.KipuStatusChip
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import kotlin.math.min

/**
 * Seconds into the walkthrough at which each piece enters. One clock drives the whole animation, so the
 * order is the order of this list: bank notification, link, Kipu reads it, link, movement is registered.
 */
internal object CaptureHowTimeline {
    const val NOTIFICATION_BLOCK = 0.2f
    const val NOTIFICATION = 0.9f
    const val HIGHLIGHT_AMOUNT = 2.2f
    const val HIGHLIGHT_MERCHANT = 2.65f
    const val LINK_ONE = 3.5f
    const val KIPU_BLOCK = 4.2f
    const val READING_IN = 4.7f
    val TILES = floatArrayOf(4.9f, 5.6f, 6.3f, 7.0f, 7.7f)
    const val READING_OUT = 8.4f
    const val CONFIDENCE = 8.6f
    const val LINK_TWO = 9.1f
    const val LEDGER_BLOCK = 9.8f
    const val NEW_ROW = 10.5f
    const val NEW_CHIP_IN = 11.1f
    const val DONE_BADGE = 11.3f
    const val FOOTER = 11.9f
    const val NEW_CHIP_OUT = 12.6f
    const val END = 13.2f

    /** The footer buttons only react once they are fully visible. */
    const val FOOTER_ACTIVE = FOOTER + 0.5f

    fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)
}

private typealias Clock = () -> Float

private val EaseOut = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)
private val Pop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val BankBlue = Color(0xFF002A8F)

private fun Clock.at(start: Float, duration: Float): Float = CaptureHowTimeline.progress(invoke(), start, duration)

/** Fades and slides a piece in once the clock reaches [start]; before that it is invisible but keeps its space. */
private fun Modifier.reveal(clock: Clock, start: Float, duration: Float = 0.5f, dy: Dp = 10.dp, from: Float = 0.96f, pop: Boolean = false): Modifier =
    graphicsLayer {
        val p = clock.at(start, duration)
        val eased = if (pop) Pop.transform(p) else EaseOut.transform(p)
        alpha = p.coerceAtMost(1f).let { if (pop) min(1f, it * 2.5f) else it }
        translationY = (1f - eased) * dy.toPx()
        val s = from + (1f - from) * eased
        scaleX = s
        scaleY = s
    }

/**
 * "De tu banco a Kipu, automáticamente": an animated, fictional example of how a bank notification becomes
 * a registered movement. It changes nothing; closing it returns to the inbox.
 */
@Composable
fun CaptureHowItWorksScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val palette = rememberCalmEmeraldColors()
    val context = LocalContext.current
    val animationsOff = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var run by rememberSaveable { mutableIntStateOf(0) }
    val time = remember { Animatable(0f) }
    val clock: Clock = remember(time) { { time.value } }

    LaunchedEffect(run, animationsOff) {
        if (animationsOff) {
            time.snapTo(CaptureHowTimeline.END)
        } else {
            time.snapTo(0f)
            time.animateTo(CaptureHowTimeline.END, tween((CaptureHowTimeline.END * 1000).toInt(), easing = LinearEasing))
        }
    }
    val footerActive by remember { derivedStateOf { time.value >= CaptureHowTimeline.FOOTER_ACTIVE } }
    val ledgerShown by remember { derivedStateOf { time.value >= CaptureHowTimeline.LEDGER_BLOCK } }
    val scroll = rememberScrollState()
    // On short screens the last block would sit under the footer: follow the animation down, and back up on replay.
    LaunchedEffect(ledgerShown) { scroll.animateScrollTo(if (ledgerShown) scroll.maxValue else 0) }

    Scaffold(modifier = modifier.testTag("capture_how_it_works"), containerColor = palette.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KipuStatusChip(
                    text = "Captura automática",
                    tone = KipuChipTone.POSITIVE,
                    leading = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) },
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("capture_how_close")) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = palette.secondaryMuted)
                }
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "De tu banco a Kipu, automáticamente",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.primaryText,
                )
                Text(
                    "Descubre cómo Kipu detecta y registra tus movimientos automáticamente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryMuted,
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val minHeight = maxHeight
                Column(
                    Modifier
                        .verticalScroll(scroll)
                        .heightIn(min = minHeight)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clearAndSetSemantics {
                            contentDescription = "Ejemplo: BCP notifica un consumo de S/ 50.00 en Tambo. Kipu reconoce el banco, " +
                                "el tipo, el monto, el comercio y la cuenta, y registra el gasto en tu cuenta BCP."
                        },
                    verticalArrangement = Arrangement.Center,
                ) {
                    BankBlock(clock)
                    FlowConnector(clock, CaptureHowTimeline.LINK_ONE, 0.8f, 0.9f, CaptureHowTimeline.KIPU_BLOCK)
                    KipuBlock(clock)
                    FlowConnector(clock, CaptureHowTimeline.LINK_TWO, 0.7f, 0.8f, CaptureHowTimeline.LEDGER_BLOCK - 0.1f)
                    LedgerBlock(clock)
                }
            }
            Surface(
                color = palette.surfaceCard,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().reveal(clock, CaptureHowTimeline.FOOTER, 0.7f, dy = 30.dp, from = 1f),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = palette.primaryDeep, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                        Text(
                            "Simulación con datos ficticios. Si hay dudas, la captura pasa a revisión; las compras con tarjeta de crédito siempre se confirman.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryMuted,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { run += 1 },
                            enabled = footerActive,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("capture_how_replay"),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle),
                        ) { Text("Ver de nuevo", fontWeight = FontWeight.Bold, color = palette.primaryText) }
                        Button(
                            onClick = onClose,
                            enabled = footerActive,
                            modifier = Modifier.weight(1.3f).heightIn(min = 52.dp).testTag("capture_how_done"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primaryDeep, contentColor = palette.onPrimaryDeep),
                        ) { Text("Entendido", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    step: Int,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val palette = rememberCalmEmeraldColors()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = palette.surfaceCard,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(22.dp).background(palette.primaryDeep, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$step", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = palette.onPrimaryDeep)
                }
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = palette.primaryText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                trailing()
            }
            content()
        }
    }
}

@Composable
private fun BankBlock(clock: Clock) {
    val palette = rememberCalmEmeraldColors()
    val amountHighlight by remember(clock) { derivedStateOf { clock.at(CaptureHowTimeline.HIGHLIGHT_AMOUNT, 0.7f) } }
    val merchantHighlight by remember(clock) { derivedStateOf { clock.at(CaptureHowTimeline.HIGHLIGHT_MERCHANT, 0.7f) } }
    StepCard(1, "Tu banco te notifica", Modifier.reveal(clock, CaptureHowTimeline.NOTIFICATION_BLOCK, 0.6f, dy = 22.dp, from = 1f)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.surfaceCard,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier.fillMaxWidth().reveal(clock, CaptureHowTimeline.NOTIFICATION, 0.8f, dy = (-34).dp, from = 0.97f),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandLogo(brand = KipuBrand.BCP, fallbackName = "BCP", contentDescription = null, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BCP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.secondaryMuted)
                        Text(" · Banca móvil", style = MaterialTheme.typography.labelSmall, color = palette.secondaryMuted, modifier = Modifier.weight(1f))
                        Text("12:41", style = MaterialTheme.typography.labelSmall, color = palette.secondaryMuted)
                    }
                    Text(
                        text = buildAnnotatedString {
                            append("Realizaste un consumo de ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = palette.warningBg.copy(alpha = amountHighlight))) { append("S/ 50.00") }
                            append(" en ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = palette.warningBg.copy(alpha = merchantHighlight))) { append("TAMBO") }
                            append(".")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.primaryText,
                    )
                    KipuStatusChip(
                        text = "Tarjeta de débito",
                        tone = KipuChipTone.NEUTRAL,
                        leading = { Icon(Icons.Outlined.CreditCard, contentDescription = null, modifier = Modifier.size(12.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KipuBlock(clock: Clock) {
    StepCard(
        step = 2,
        title = "Kipu interpreta tu movimiento",
        modifier = Modifier.reveal(clock, CaptureHowTimeline.KIPU_BLOCK, 0.6f, dy = 22.dp, from = 1f),
        trailing = {
            Box(Modifier.heightIn(min = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Row(
                    Modifier.graphicsLayer {
                        alpha = min(clock.at(CaptureHowTimeline.READING_IN, 0.3f), 1f - clock.at(CaptureHowTimeline.READING_OUT, 0.3f))
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = rememberCalmEmeraldColors().primaryDeep)
                    Text("Leyendo…", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = rememberCalmEmeraldColors().primaryDeep)
                }
                ConfidenceChip(CaptureConfidenceLevel.HIGH, Modifier.reveal(clock, CaptureHowTimeline.CONFIDENCE, 0.5f, dy = 0.dp, from = 0.6f, pop = true))
            }
        },
    ) {
        val palette = rememberCalmEmeraldColors()
        val tiles = CaptureHowTimeline.TILES
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HowTile(
                    icon = { BrandLogo(brand = KipuBrand.BCP, fallbackName = "BCP", contentDescription = null, size = 28.dp) },
                    label = "Banco", value = "BCP", modifier = Modifier.weight(1f).reveal(clock, tiles[0]),
                )
                HowTile(
                    icon = { TileIcon(Icons.Outlined.NorthEast, palette.expenseBg, palette.expenseText) },
                    label = "Tipo", value = "Gasto", modifier = Modifier.weight(1f).reveal(clock, tiles[1]),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HowTile(
                    icon = { TileIcon(Icons.Outlined.Payments, palette.incomeBg, palette.primaryDeep) },
                    label = "Monto", value = "S/ 50.00", modifier = Modifier.weight(1f).reveal(clock, tiles[2]),
                )
                HowTile(
                    icon = { TamboLogo(28.dp) },
                    label = "Comercio", value = "Tambo", modifier = Modifier.weight(1f).reveal(clock, tiles[3]),
                )
            }
            HowTile(
                icon = { TileIcon(Icons.Outlined.AccountBalanceWallet, palette.incomeBg, palette.primaryDeep) },
                label = "Cuenta identificada", value = "BCP Débito",
                modifier = Modifier.fillMaxWidth().reveal(clock, tiles[4]),
                trailing = { Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.incomeEmerald, modifier = Modifier.size(20.dp)) },
            )
        }
    }
}

@Composable
private fun LedgerBlock(clock: Clock) {
    val palette = rememberCalmEmeraldColors()
    StepCard(
        step = 3,
        title = "Tu movimiento queda registrado",
        modifier = Modifier.reveal(clock, CaptureHowTimeline.LEDGER_BLOCK, 0.6f, dy = 22.dp, from = 1f),
        trailing = {
            Box(
                Modifier.size(24.dp).reveal(clock, CaptureHowTimeline.DONE_BADGE, 0.5f, dy = 0.dp, from = 0.6f, pop = true).background(palette.incomeEmerald, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        },
    ) {
        val shape = RoundedCornerShape(16.dp)
        val flashFrom = palette.incomeBg
        val flashTo = palette.surfaceCard
        Row(
            Modifier
                .fillMaxWidth()
                .reveal(clock, CaptureHowTimeline.NEW_ROW, 0.8f, dy = (-16).dp, from = 0.96f)
                .clip(shape)
                .drawBehind { drawRect(lerp(flashFrom, flashTo, EaseOut.transform(clock.at(CaptureHowTimeline.NEW_ROW, 2.4f)))) }
                .border(1.dp, palette.incomeBorder, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TamboLogo(40.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tambo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryText)
                    Box(
                        Modifier.graphicsLayer {
                            alpha = min(clock.at(CaptureHowTimeline.NEW_CHIP_IN, 0.4f), 1f - clock.at(CaptureHowTimeline.NEW_CHIP_OUT, 0.5f))
                        },
                    ) { KipuStatusChip("Nuevo", KipuChipTone.POSITIVE) }
                }
                Text("Gasto · Cuenta BCP", style = MaterialTheme.typography.bodySmall, color = palette.secondaryMuted)
            }
            Text("-S/ 50.00", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.expenseText)
        }
    }
}

@Composable
private fun HowTile(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = rememberCalmEmeraldColors()
    Row(
        modifier.heightIn(min = 50.dp).background(palette.pillTrack, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.secondaryMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = palette.primaryText, maxLines = 1)
        }
        trailing?.invoke()
    }
}

@Composable
private fun TileIcon(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.size(28.dp).background(background, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** The bundled Tambo mark, the same one the merchant catalog shows. */
@Composable
private fun TamboLogo(size: Dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Image(
        painter = painterResource(R.drawable.ic_merchant_tambo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size).clip(shape).background(Color.White),
    )
}

/** Teal line that draws downward, with a dot riding along it and an arrow head once it lands. */
@Composable
private fun FlowConnector(clock: Clock, start: Float, lineDuration: Float, dotDuration: Float, arrowStart: Float) {
    val palette = rememberCalmEmeraldColors()
    val teal = palette.primaryDeep
    Canvas(Modifier.fillMaxWidth().height(30.dp)) {
        val cx = size.width / 2f
        val line = EaseOut.transform(clock.at(start, lineDuration))
        if (line > 0f) {
            drawLine(
                brush = Brush.verticalGradient(listOf(teal.copy(alpha = 0.55f), teal), 0f, size.height),
                start = Offset(cx, 0f),
                end = Offset(cx, size.height * line),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val dot = clock.at(start, dotDuration)
        if (dot > 0f && dot < 1f) {
            val visible = min(min(dot / 0.15f, 1f), min((1f - dot) / 0.15f, 1f))
            val y = -2.dp.toPx() + dot * (size.height + 4.dp.toPx())
            drawCircle(teal.copy(alpha = 0.2f * visible), radius = 9.dp.toPx(), center = Offset(cx, y))
            drawCircle(teal.copy(alpha = visible), radius = 5.dp.toPx(), center = Offset(cx, y))
        }
        val arrow = clock.at(arrowStart, 0.3f)
        if (arrow > 0f) {
            val c = teal.copy(alpha = arrow)
            val w = 2.5.dp.toPx()
            val bottom = size.height
            drawLine(c, Offset(cx - 6.dp.toPx(), bottom - 6.dp.toPx()), Offset(cx, bottom), w, StrokeCap.Round)
            drawLine(c, Offset(cx + 6.dp.toPx(), bottom - 6.dp.toPx()), Offset(cx, bottom), w, StrokeCap.Round)
        }
    }
}
