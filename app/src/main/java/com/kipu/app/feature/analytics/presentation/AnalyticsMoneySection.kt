package com.kipu.app.feature.analytics.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.presentation.components.AccountIdentityIcon
import com.kipu.app.feature.analytics.domain.AccountFlow
import com.kipu.app.feature.analytics.domain.AnalyticsOverview
import com.kipu.app.feature.analytics.domain.DestinationKind
import com.kipu.app.feature.analytics.domain.MoneyDestinationCalculator
import com.kipu.app.feature.analytics.domain.PeriodResult
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.formatMinorUnits
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.PastelTone
import com.kipu.app.ui.theme.at
import com.kipu.app.ui.theme.rememberPastelTones
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.hypot

/** Where each tap in Análisis leads; the screen itself never knows the navigation graph. */
data class AnalyticsNavigation(
    val onOpenAccount: (String) -> Unit = {},
    val onOpenCard: (String) -> Unit = {},
    val onOpenAccounts: () -> Unit = {},
    val onOpenGoals: () -> Unit = {},
    val onOpenDues: () -> Unit = {},
    val onOpenDebts: () -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onOpenMovements: () -> Unit = {},
)

enum class MoneyView { ACCOUNT, DESTINATION }

private data class RingSlice(
    val key: String,
    val title: String,
    val subtitle: String,
    val amount: Money,
    val tone: PastelTone,
    val accountItem: AccountWithBalance? = null,
    val destination: DestinationKind? = null,
    val icon: ImageVector? = null,
)

/** Largest-remainder rounding, so the whole percentages of one ring always add up to 100. */
fun wholePercentages(values: List<Long>): List<Int> {
    val total = values.sumOf { it.coerceAtLeast(0L) }
    if (total <= 0L) return values.map { 0 }
    val raw = values.map { it.coerceAtLeast(0L) * 100.0 / total }
    val floors = raw.map { it.toInt() }.toMutableList()
    var rest = 100 - floors.sum()
    raw.indices.sortedByDescending { raw[it] - floors[it] }.forEach { index ->
        if (rest > 0) { floors[index] += 1; rest-- }
    }
    return floors
}

@Composable
internal fun MoneyRingSection(
    overview: AnalyticsOverview,
    result: PeriodResult,
    currencies: List<Currency>,
    currency: Currency,
    onCurrency: (Currency) -> Unit,
    asOfLabel: String,
    untilLabel: String?,
    navigation: AnalyticsNavigation,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val tones = rememberPastelTones()
    var view by rememberSaveable { mutableStateOf(MoneyView.ACCOUNT) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }

    val accounts = overview.accountsIn(currency)
    val total = overview.total(currency)
    val flows = result.byAccount[currency].orEmpty()
    val destination = MoneyDestinationCalculator.slices(
        total, overview.reserved(currency), overview.upcoming[currency] ?: Money.zero(currency),
    )

    val slices: List<RingSlice> = if (view == MoneyView.ACCOUNT) {
        accounts.mapIndexed { index, item ->
            RingSlice(
                key = item.account.id.value, title = item.account.alias, subtitle = accountTypeLabel(item.account.type),
                amount = item.balance, tone = tones.at(index), accountItem = item,
            )
        }
    } else {
        destination.map { slice ->
            val (title, subtitle, icon, tone) = when (slice.kind) {
                DestinationKind.FREE -> DestinationVisual(stringResource(R.string.analytics_dest_free), stringResource(R.string.analytics_dest_free_sub), Icons.Outlined.AccountBalanceWallet, tones.at(0))
                DestinationKind.GOALS -> DestinationVisual(stringResource(R.string.analytics_dest_goals), stringResource(R.string.analytics_dest_goals_sub), Icons.Outlined.Flag, tones.at(1))
                DestinationKind.UPCOMING -> DestinationVisual(stringResource(R.string.analytics_dest_upcoming), stringResource(R.string.analytics_dest_upcoming_sub, untilLabel.orEmpty()), Icons.AutoMirrored.Outlined.EventNote, tones.at(2))
            }
            RingSlice(slice.kind.name, title, subtitle, slice.amount, tone, destination = slice.kind, icon = icon)
        }
    }
    val visible = slices
    val percents = wholePercentages(visible.map { it.amount.minorUnits })
    val picked = visible.getOrNull(selected)
    LaunchedEffect(view, currency, visible.size) { if (selected >= visible.size) selected = -1 }

    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = colors.surfaceCard, shadowElevation = 1.dp) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.analytics_money_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                    fontWeight = FontWeight.Bold, color = colors.primaryDeep,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.analytics_money_sub, asOfLabel), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            }
            if (currencies.size > 1) {
                Segmented(
                    labels = currencies.map { stringResource(if (it == Currency.PEN) R.string.analytics_currency_pen else R.string.analytics_currency_usd) },
                    selectedIndex = currencies.indexOf(currency).coerceAtLeast(0),
                    description = stringResource(R.string.analytics_currency_selector),
                    onSelect = { onCurrency(currencies[it]); selected = -1 },
                )
            }
            Segmented(
                labels = listOf(stringResource(R.string.analytics_view_account), stringResource(R.string.analytics_view_destination)),
                selectedIndex = view.ordinal,
                description = stringResource(R.string.analytics_view_group),
                onSelect = { view = MoneyView.entries[it]; selected = -1 },
            )
            val ringDescription = stringResource(R.string.analytics_ring_desc, visible.indices.joinToString { "${visible[it].title} ${percents[it]}%" })
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Ring(
                    values = visible.map { it.amount.minorUnits },
                    tones = visible.map { it.tone },
                    selected = selected,
                    onPick = { selected = if (selected == it) -1 else it },
                    description = ringDescription,
                    centerContent = {
                        val countLabel = if (accounts.size == 1) stringResource(R.string.analytics_ring_one_account) else stringResource(R.string.analytics_ring_accounts, accounts.size)
                        Text(picked?.title ?: stringResource(R.string.analytics_ring_own), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.secondaryMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        MoneyText(money = picked?.amount ?: total, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
                        Text(
                            if (picked != null) stringResource(R.string.analytics_ring_share, percents[selected].toString())
                            else if (view == MoneyView.DESTINATION) stringResource(R.string.analytics_ring_destination) else countLabel,
                            style = MaterialTheme.typography.labelMedium, color = colors.secondaryMuted, textAlign = TextAlign.Center,
                        )
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                visible.forEachIndexed { index, slice ->
                    val isSelected = index == selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) slice.tone.tile else Color.Transparent)
                            .selectable(selected = isSelected, role = Role.Button, onClick = { selected = if (isSelected) -1 else index })
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SliceIcon(slice)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(slice.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(slice.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            MoneyText(money = slice.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
                            Text("${percents[index]}%", style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
                        }
                    }
                }
            }
            if (picked == null) {
                Text(
                    stringResource(R.string.analytics_ring_hint), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SliceDetail(picked, flows[picked.key], percents[selected], navigation)
            }
        }
    }
}

private data class DestinationVisual(val title: String, val subtitle: String, val icon: ImageVector, val tone: PastelTone)

@Composable
private fun SliceIcon(slice: RingSlice, size: Dp = 40.dp) {
    when {
        slice.accountItem != null -> {
            val account = slice.accountItem.account
            val brand = KipuBrand.from(AccountPreset.normalizedFor(account.type, account.preset)?.name)
            if (brand != null && account.type != AccountType.CASH) AccountIdentityIcon(account, size)
            else Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(slice.tone.tile), contentAlignment = Alignment.Center) {
                Icon(
                    when (account.type) {
                        AccountType.CASH -> Icons.Outlined.Payments
                        AccountType.DIGITAL_WALLET -> Icons.Outlined.AccountBalanceWallet
                        else -> Icons.Outlined.AccountBalance
                    },
                    contentDescription = null, tint = slice.tone.ink, modifier = Modifier.size(size * 0.5f),
                )
            }
        }
        slice.icon != null -> Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(slice.tone.tile), contentAlignment = Alignment.Center) {
            Icon(slice.icon, contentDescription = null, tint = slice.tone.ink, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
private fun SliceDetail(slice: RingSlice, flow: AccountFlow?, percent: Int, navigation: AnalyticsNavigation) {
    val colors = rememberCalmEmeraldColors()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(slice.tone.tile).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(slice.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = slice.tone.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$percent%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = slice.tone.ink)
        }
        if (slice.accountItem != null) {
            val currency = slice.amount.currency
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailFigure(stringResource(R.string.analytics_account_income), flow?.income ?: Money.zero(currency), slice.tone, Modifier.weight(1f))
                DetailFigure(stringResource(R.string.analytics_account_expenses), flow?.expense ?: Money.zero(currency), slice.tone, Modifier.weight(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.analytics_account_movements), style = MaterialTheme.typography.labelMedium, color = slice.tone.ink)
                    Text((flow?.movements ?: 0).toString(), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
                }
            }
            DetailAction(stringResource(R.string.analytics_account_cta)) { navigation.onOpenAccount(slice.key) }
        } else {
            val (text, cta, action) = when (slice.destination) {
                DestinationKind.GOALS -> Triple(R.string.analytics_dest_goals_text, R.string.analytics_dest_goals_cta, navigation.onOpenGoals)
                DestinationKind.UPCOMING -> Triple(R.string.analytics_dest_upcoming_text, R.string.analytics_dest_upcoming_cta, navigation.onOpenDues)
                else -> Triple(R.string.analytics_dest_free_text, R.string.analytics_dest_free_cta, navigation.onOpenAccounts)
            }
            Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = colors.primaryText)
            DetailAction(stringResource(cta), action)
        }
    }
}

@Composable
private fun DetailFigure(label: String, money: Money, tone: PastelTone, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = tone.ink)
        MoneyText(money = money, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = rememberCalmEmeraldColors().primaryText)
    }
}

@Composable
internal fun DetailAction(label: String, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(colors.primaryDeep).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.onPrimaryDeep)
        Spacer(Modifier.size(4.dp))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onPrimaryDeep, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun Segmented(labels: List<String>, selectedIndex: Int, description: String, onSelect: (Int) -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val tone = rememberPastelTones().at(0)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.pillTrack).padding(3.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selectedIndex
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (on) tone.tile else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { if (!on) onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) tone.ink else colors.secondaryMuted)
            }
        }
    }
}

/** Ring chart whose segments grow in once; tapping one selects it and the rows below follow. */
@Composable
private fun Ring(
    values: List<Long>,
    tones: List<PastelTone>,
    selected: Int,
    onPick: (Int) -> Unit,
    description: String,
    centerContent: @Composable () -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val total = values.sumOf { it.coerceAtLeast(0L) }
    val progress = remember { Animatable(0f) }
    val signature = values.hashCode()
    LaunchedEffect(signature) { progress.snapTo(0f); progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val widths = values.indices.map { if (it == selected) 36f else 28f }
    Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(236.dp).clearAndSetSemantics { contentDescription = description }.pointerInput(values, selected) {
                detectTapGestures { tap ->
                    if (total <= 0L) return@detectTapGestures
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    val dx = tap.x - centre.x
                    val dy = tap.y - centre.y
                    val radius = hypot(dx, dy)
                    val outer = size.width / 2f
                    if (radius < outer * 0.5f || radius > outer) return@detectTapGestures
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angle < 0f) angle += 360f
                    var start = 0f
                    values.forEachIndexed { index, value ->
                        val sweep = value.coerceAtLeast(0L).toFloat() / total * 360f
                        if (angle >= start && angle < start + sweep) { onPick(index); return@detectTapGestures }
                        start += sweep
                    }
                }
            },
        ) {
            val stroke = 28.dp.toPx()
            val inset = 18.dp.toPx() + stroke / 2f
            val diameter = size.minDimension - inset * 2f
            val topLeft = Offset(inset, inset)
            drawCircle(color = colors.pillTrack, radius = diameter / 2f, center = center, style = Stroke(width = stroke))
            if (total <= 0L) return@Canvas
            val gap = if (values.count { it > 0L } > 1) 3f else 0f
            var start = -90f
            values.forEachIndexed { index, value ->
                val sweep = value.coerceAtLeast(0L).toFloat() / total * 360f
                val shown = ((sweep - gap) * progress.value).coerceAtLeast(0f)
                if (shown > 0f) {
                    val width = widths[index].dp.toPx()
                    val dim = selected >= 0 && selected != index
                    drawArc(
                        color = tones[index].fill.copy(alpha = if (dim) 0.35f else 1f),
                        startAngle = start + gap / 2f, sweepAngle = shown, useCenter = false,
                        topLeft = Offset(center.x - diameter / 2f - (width - stroke) / 2f, center.y - diameter / 2f - (width - stroke) / 2f),
                        size = Size(diameter + (width - stroke), diameter + (width - stroke)),
                        style = Stroke(width = width, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        Column(Modifier.padding(horizontal = 52.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { centerContent() }
    }
}

@Composable
internal fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CASH -> R.string.analytics_account_cash
        AccountType.SAVINGS -> R.string.analytics_account_savings
        AccountType.DIGITAL_WALLET -> R.string.analytics_account_wallet
        else -> R.string.analytics_account_bank
    },
)

@Composable
internal fun NoAccountsSection(onAdd: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = colors.surfaceCard, shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(44.dp)).background(rememberPastelTones().at(0).tile), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = rememberPastelTones().at(0).ink, modifier = Modifier.size(40.dp))
            }
            Text(stringResource(R.string.analytics_no_accounts_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.primaryText, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.analytics_no_accounts_body), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, textAlign = TextAlign.Center)
            Box(Modifier) { DetailAction(stringResource(R.string.analytics_no_accounts_cta), onAdd) }
        }
    }
}

@Composable
internal fun OneAccountBanner(onAdd: () -> Unit) {
    val tone = rememberPastelTones().at(0)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tone.tile).clickable(role = Role.Button, onClick = onAdd).defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(rememberCalmEmeraldColors().surfaceCard), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = tone.ink)
        }
        Text(stringResource(R.string.analytics_one_account_banner), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tone.ink, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tone.ink)
    }
}

/** Short date label like "24 nov" taken from the localized month abbreviations. */
internal fun LocalDate.dayMonth(monthNames: List<String>): String = "$dayOfMonth ${monthNames[monthValue - 1]}"

internal fun plainAmount(money: Money): String = "${money.currency.symbol()} ${formatMinorUnits(money.minorUnits)}"

@Composable
internal fun maskedPlain(money: Money): String =
    if (LocalBalanceMasked.current) stringResource(R.string.analytics_masked_amount) else plainAmount(money)
