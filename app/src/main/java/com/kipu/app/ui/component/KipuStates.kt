package com.kipu.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Placeholder block for loading states. Pulses softly unless the user asked to reduce motion. */
@Composable
fun KipuSkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    color: Color = rememberCalmEmeraldColors().pillTrack,
    cornerRadius: Dp = 10.dp,
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val alpha = if (reducedMotion) {
        1f
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "skeleton_alpha",
        ).value
    }
    Box(
        modifier
            .heightIn(min = height)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(cornerRadius))
            .background(color),
    )
}

/** Announces a loading region once to TalkBack instead of reading every placeholder. */
fun Modifier.loadingSemantics(label: String): Modifier =
    clearAndSetSemantics { contentDescription = label }

/**
 * Blocking error card (H8, C11, M7): the data is safe, nothing is shown as zero, and the
 * only action is retrying.
 */
@Composable
fun KipuErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Reintentar",
) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(colors.expenseBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.expenseText, modifier = Modifier.size(32.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, textAlign = TextAlign.Center)
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(retryLabel, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Amber notice for offline work (M8): "Sin conexión." in bold followed by what is pending. */
@Composable
fun KipuOfflineBanner(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Sin conexión.",
) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color = colors.warningBg,
        border = BorderStroke(1.dp, colors.warningBorder),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = colors.warningText, modifier = Modifier.size(20.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
                    append(" ")
                    append(message)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.warningText,
            )
        }
    }
}

/** Small amber tag for a row that is saved locally but not synced yet. */
@Composable
fun KipuPendingSyncTag(modifier: Modifier = Modifier, label: String = "Pendiente de sincronizar") {
    val colors = rememberCalmEmeraldColors()
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = colors.warningText,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.warningBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Shared 50/80/100 usage scale (card utilization, budgets): teal, amber, orange and red, always
 * paired with an icon and a label so the state never depends on color alone.
 */
enum class UsageLevel(val label: String) {
    NORMAL("En control"),
    ATTENTION("Atención"),
    NEAR_LIMIT("Cerca del límite"),
    EXCEEDED("Límite alcanzado");

    val icon: ImageVector
        get() = when (this) {
            NORMAL -> Icons.Outlined.CheckCircle
            ATTENTION -> Icons.Outlined.Schedule
            NEAR_LIMIT -> Icons.Outlined.WarningAmber
            EXCEEDED -> Icons.Outlined.ErrorOutline
        }

    fun color(colors: CalmEmeraldColors): Color = when (this) {
        NORMAL -> colors.primaryDeep
        ATTENTION -> colors.warningAmber
        NEAR_LIMIT -> colors.nearLimitOrange
        EXCEEDED -> colors.expenseCoral
    }

    /** Readable foreground for text on the surface (amber itself is too light for body text). */
    fun textColor(colors: CalmEmeraldColors): Color = when (this) {
        NORMAL -> colors.primaryDeep
        ATTENTION, NEAR_LIMIT -> colors.warningText
        EXCEEDED -> colors.expenseText
    }

    companion object {
        fun forPercent(percent: Double): UsageLevel = when {
            percent >= 100.0 -> EXCEEDED
            percent >= 80.0 -> NEAR_LIMIT
            percent >= 50.0 -> ATTENTION
            else -> NORMAL
        }
    }
}

@Composable
fun UsageLevelLabel(level: UsageLevel, modifier: Modifier = Modifier, text: String = level.label) {
    val colors = rememberCalmEmeraldColors()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(level.icon, contentDescription = null, tint = level.textColor(colors), modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = level.textColor(colors))
    }
}
