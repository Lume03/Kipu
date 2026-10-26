package com.kipu.app.feature.home.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.home.domain.CurrencyNetWorth
import com.kipu.app.ui.component.KipuSkeletonBlock
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.loadingSemantics
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

internal const val NET_WORTH_CARD_TAG = "home_net_worth_card"
internal const val NET_WORTH_UNAVAILABLE_TAG = "home_net_worth_unavailable"

/**
 * Resumen patrimonial de Inicio (H1/H3/H6). Colapsado muestra solo el total; expandido, sus
 * componentes. Los importes pasan por [MoneyText], así que la máscara de privacidad solo oculta
 * la presentación sin alterar cálculos. Un error se muestra como «No disponible», nunca como cero.
 */
@Composable
fun NetWorthCard(
    state: NetWorthUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val colors = rememberCalmEmeraldColors()
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val content = state as? NetWorthUiState.Content
    val canExpand = content != null
    val reducedMotion = rememberReducedMotionEnabled()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 200),
        label = "netWorthChevron",
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag(NET_WORTH_CARD_TAG),
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceCard,
            shadowElevation = 1.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .then(
                            if (canExpand) {
                                Modifier
                                    .clickable(role = Role.Button) { expanded = !expanded }
                                    .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Patrimonio",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.Bold,
                            color = colors.secondaryMuted,
                            modifier = Modifier.semantics { heading() },
                        )
                        NetWorthHeadline(state)
                    }
                    if (canExpand) {
                        Text(
                            text = if (expanded) "Ocultar" else "Ver detalle",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryDeep,
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.primaryDeep,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
                if (content != null) {
                    AnimatedVisibility(
                        visible = expanded,
                        enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(180)) + fadeOut(tween(120)),
                    ) {
                        NetWorthBreakdown(content.byCurrency)
                    }
                }
            }
        }
        if (state is NetWorthUiState.Error) {
            NetWorthUnavailableNotice(message = state.message, onRetry = onRetry)
        }
    }
}

@Composable
private fun NetWorthHeadline(state: NetWorthUiState) {
    val colors = rememberCalmEmeraldColors()
    val headline = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum",
    )
    when (state) {
        NetWorthUiState.Loading -> Column(
            modifier = Modifier.padding(top = 6.dp).loadingSemantics("Calculando patrimonio"),
        ) {
            KipuSkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 24.dp)
        }
        NetWorthUiState.Empty -> Text(
            text = "Sin datos todavía. Registra una cuenta o una deuda para ver tu patrimonio.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryMuted,
        )
        is NetWorthUiState.Error -> Text(
            text = "No disponible",
            style = headline.copy(fontSize = 20.sp),
            color = colors.secondaryMuted,
            modifier = Modifier.testTag(NET_WORTH_UNAVAILABLE_TAG),
        )
        is NetWorthUiState.Content -> Column {
            val showLabel = state.byCurrency.size > 1
            state.byCurrency.forEach { item ->
                if (showLabel) {
                    Text(item.currency.netWorthLabel(), style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
                }
                MoneyText(
                    money = item.netWorth,
                    style = headline,
                    color = if (item.netWorth.minorUnits < 0L) colors.expenseText else colors.primaryText,
                )
            }
        }
    }
}

@Composable
private fun NetWorthBreakdown(byCurrency: List<CurrencyNetWorth>) {
    val colors = rememberCalmEmeraldColors()
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        byCurrency.forEachIndexed { index, item ->
            if (byCurrency.size > 1) {
                if (index > 0) Spacer(Modifier.height(4.dp))
                Text(item.currency.netWorthLabel(), style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                ComponentLine("Activos reales", item.realAssets)
                ComponentLine("Por cobrar", item.receivables)
                ComponentLine("Pasivos pendientes", item.liabilities)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = colors.borderSubtle)
        Text(
            text = "Activos reales + por cobrar − pasivos. La línea de crédito disponible y las reservas no se suman.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ComponentLine(label: String, money: Money) {
    val colors = rememberCalmEmeraldColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, modifier = Modifier.weight(1f))
        MoneyText(
            money = money,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = colors.primaryText,
        )
    }
}

/** H6: the figure is missing, so the copy says so instead of showing a zero. */
@Composable
private fun NetWorthUnavailableNotice(message: String, onRetry: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.warningBg)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = colors.warningText,
            modifier = Modifier.padding(top = 1.dp).size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(message) }
                    append(" El patrimonio no se muestra hasta tener el dato: «No disponible» no significa S/ 0.00.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.primaryText,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
            ) {
                Text("Reintentar", fontWeight = FontWeight.ExtraBold, color = colors.warningText)
            }
        }
    }
}

private fun Currency.netWorthLabel(): String = when (this) {
    Currency.PEN -> "Soles"
    Currency.USD -> "Dólares"
}
