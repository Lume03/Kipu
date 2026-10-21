package com.kipu.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Root navigation styled to match the compact Planning reference and selected-state indicator. */
@Composable
fun KipuNavigationBar(
    currentRoute: String?,
    onNavigateToDinero: () -> Unit,
    onNavigateToMovimientos: () -> Unit,
    modifier: Modifier = Modifier,
    onRegisterClick: (() -> Unit)? = null,
    onNavigateToDeudas: () -> Unit = {},
    onNavigateToPlanificacion: () -> Unit = {},
) {
    val isDineroSelected = currentRoute == ACCOUNTS_DASHBOARD_ROUTE
    val isMovimientosSelected = currentRoute == MOVEMENTS_HISTORY_PATTERN ||
        currentRoute == MOVEMENTS_HISTORY_ROUTE ||
        currentRoute?.startsWith("movements/history") == true
    val isDeudasSelected = currentRoute == DEBT_LIST_ROUTE
    val isPlanificacionSelected = currentRoute == GOALS_LIST_ROUTE
    val colors = rememberCalmEmeraldColors()
    val largeText = LocalDensity.current.fontScale > 1.3f
    val isDebtTabSelected = isDeudasSelected

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("kipu_bottom_navigation_bar"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f).testTag("kipu_bottom_navigation_pill"),
                shape = RoundedCornerShape(32.dp),
                color = colors.navCapsuleBg,
                border = BorderStroke(1.dp, colors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KipuRootNavigationItem(
                        selected = isDineroSelected,
                        onClick = onNavigateToDinero,
                        icon = Icons.Default.AccountBalanceWallet,
                        label = "Dinero",
                        tag = "nav_item_dinero",
                        largeText = largeText,
                        modifier = Modifier.weight(1f),
                    )
                    KipuRootNavigationItem(
                        selected = isMovimientosSelected,
                        onClick = onNavigateToMovimientos,
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        label = "Movimientos",
                        tag = "nav_item_movimientos",
                        largeText = largeText,
                        modifier = Modifier.weight(1f),
                    )
                    KipuRootNavigationItem(
                        selected = isDeudasSelected,
                        onClick = onNavigateToDeudas,
                        icon = Icons.Default.AccountBalance,
                        label = "Deudas",
                        tag = "nav_item_deudas",
                        largeText = largeText,
                        modifier = Modifier.weight(1f),
                    )
                    KipuRootNavigationItem(
                        selected = isPlanificacionSelected,
                        onClick = onNavigateToPlanificacion,
                        icon = Icons.Default.PowerSettingsNew,
                        label = "Planificación",
                        tag = "nav_item_planificacion",
                        largeText = largeText,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (onRegisterClick != null) {
                FloatingActionButton(
                    onClick = onRegisterClick,
                    shape = CircleShape,
                    containerColor = colors.primaryDeep,
                    contentColor = colors.onPrimaryDeep,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 6.dp,
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("btn_root_register")
                        .semantics {
                            contentDescription = if (isDebtTabSelected) {
                                "Agregar deuda o préstamo"
                            } else {
                                "Registrar movimiento"
                            }
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = colors.onPrimaryDeep,
                    )
                }
            }
        }
    }
}

@Composable
private fun KipuRootNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    tag: String,
    largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val transitionMillis = if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis
    val indicatorColor by animateColorAsState(
        targetValue = when {
            selected -> colors.navSelectedPill
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = transitionMillis),
        label = "nav_indicator_$label",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> colors.navSelectedContent
            else -> colors.navUnselectedContent
        },
        animationSpec = tween(durationMillis = transitionMillis),
        label = "nav_content_$label",
    )

    Column(
        modifier = modifier
            .heightIn(min = if (largeText) 60.dp else 56.dp)
            .clip(CircleShape)
            .background(indicatorColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(tag)
            .then(if (selected) Modifier.testTag("nav_selected_indicator") else Modifier)
            .semantics {
                contentDescription = label
                this.selected = selected
                role = Role.Tab
            }
            .padding(horizontal = 1.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        if (selected) {
            Text(
                text = label,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (largeText) 10.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.testTag("nav_label_${tag.removePrefix("nav_item_")}"),
            )
        }
    }
}
