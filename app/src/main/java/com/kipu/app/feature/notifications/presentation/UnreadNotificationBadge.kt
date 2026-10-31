package com.kipu.app.feature.notifications.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Counts above this value collapse to "99+" so the pill never grows past three glyphs. */
internal const val MAX_VISIBLE_UNREAD = 99

internal fun unreadBadgeText(count: Int): String =
    if (count > MAX_VISIBLE_UNREAD) "$MAX_VISIBLE_UNREAD+" else count.coerceAtLeast(0).toString()

/**
 * Bell button with the unread counter (KIP-16).
 *
 * The bell sits in its own 44dp circle and the counter is drawn as a sibling *above* that circle,
 * anchored to its top-end corner, so the circular shape can never clip it. The pill grows towards
 * the bell (start side) as digits are added: 1 digit is a perfect 18dp circle, 2 digits and "99+"
 * become a pill. A ring in the card colour separates it from the bell outline.
 */
@Composable
fun UnreadNotificationBadge(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean? = null,
) {
    val palette = rememberCalmEmeraldColors()
    val disableMotion = reducedMotion ?: rememberReducedMotionEnabled()
    val visibleCount = unreadCount.coerceAtLeast(0)
    val badgeText = unreadBadgeText(visibleCount)
    val description = when (visibleCount) {
        0 -> "Notificaciones"
        1 -> "1 aviso sin leer"
        else -> "$visibleCount avisos sin leer"
    }

    // 48dp keeps the minimum touch target; nothing in this box clips.
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = palette.surfaceCard,
            border = BorderStroke(1.dp, palette.borderSubtle),
            modifier = Modifier
                .size(44.dp)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = palette.primaryText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = visibleCount > 0,
            enter = if (disableMotion) EnterTransition.None else scaleIn(initialScale = 0.6f) + fadeIn(),
            exit = if (disableMotion) ExitTransition.None else scaleOut(targetScale = 0.8f) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Centre of a 1-digit badge lands on the circle's 45° edge; wider pills grow inward.
                .offset(x = 2.dp, y = (-1).dp)
                .zIndex(1f),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.primaryDeep,
                contentColor = palette.onPrimaryDeep,
                border = BorderStroke(2.dp, palette.background),
            ) {
                AnimatedContent(
                    targetState = badgeText,
                    label = "unread-count",
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        if (disableMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                                (scaleOut(targetScale = 0.85f) + fadeOut())
                        }
                    },
                ) { text ->
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .padding(horizontal = if (text.length > 1) 5.dp else 0.dp, vertical = 2.dp)
                            .wrapContentSize(Alignment.Center),
                    )
                }
            }
        }
    }
}
