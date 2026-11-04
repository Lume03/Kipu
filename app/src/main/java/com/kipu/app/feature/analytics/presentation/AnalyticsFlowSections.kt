package com.kipu.app.feature.analytics.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.analytics.domain.AnalyticsFilterOptions
import com.kipu.app.feature.analytics.domain.AnalyticsOverview
import com.kipu.app.feature.analytics.domain.BalancePoint
import com.kipu.app.feature.analytics.domain.GoalSnapshot
import com.kipu.app.feature.analytics.domain.PeriodResult
import com.kipu.app.feature.analytics.domain.WeekFlow
import com.kipu.app.feature.categories.presentation.components.MerchantAvatar
import com.kipu.app.feature.categories.presentation.components.MerchantLogoDrawables
import com.kipu.app.feature.categories.presentation.components.getMerchantVisualProfile
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.symbol
import com.kipu.app.ui.theme.PastelTone
import com.kipu.app.ui.theme.at
import com.kipu.app.ui.theme.rememberPastelTones
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Whole-percent change between two amounts; null when the earlier one is zero, which has no valid percentage. */
fun percentChange(current: Money, previous: Money): Int? {
    if (previous.minorUnits <= 0L) return null
    return BigDecimal.valueOf(current.minorUnits - previous.minorUnits).multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(previous.minorUnits), 0, RoundingMode.HALF_UP).toInt()
}

private fun signedPercent(value: Int): String = (if (value > 0) "+" else if (value < 0) "−" else "") + kotlin.math.abs(value) + "%"

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        fontWeight = FontWeight.Bold, color = rememberCalmEmeraldColors().primaryDeep,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = rememberCalmEmeraldColors().surfaceCard, shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun DeltaChip(percent: Int) {
    val tones = rememberPastelTones()
    val up = percent > 0
    val tone = if (up) tones.at(2) else tones.at(0)
    Text(
        signedPercent(percent), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tone.ink,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tone.tile).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ---------------------------------------------------------------- ingresos · gastos · neto

@Composable
internal fun FlowKpis(income: Money, expense: Money, previousExpense: Money?, premium: Boolean, hasPrevious: Boolean) {
    val tones = rememberPastelTones()
    val colors = rememberCalmEmeraldColors()
    val net = income - expense
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpiCard(stringResource(R.string.analytics_kpi_income), income, tones.at(0), Modifier.weight(1f)) {
            Text(stringResource(R.string.analytics_kpi_income_note), style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
        }
        KpiCard(stringResource(R.string.analytics_kpi_expenses), expense, tones.at(4), Modifier.weight(1f)) {
            val change = if (premium && hasPrevious && previousExpense != null) percentChange(expense, previousExpense) else null
            when {
                !premium -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.warningText, modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.analytics_kpi_locked), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.warningText)
                }
                change != null -> Text(stringResource(R.string.analytics_kpi_vs_prev, signedPercent(change)), style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted, maxLines = 2)
                hasPrevious -> Text(stringResource(R.string.analytics_kpi_no_base), style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted, maxLines = 2)
            }
        }
        KpiCard(stringResource(R.string.analytics_kpi_net), net, tones.at(1), Modifier.weight(1f)) {
            Text(stringResource(R.string.analytics_kpi_net_note), style = MaterialTheme.typography.labelSmall, color = colors.secondaryMuted)
        }
    }
}

@Composable
private fun KpiCard(label: String, amount: Money, tone: PastelTone, modifier: Modifier, note: @Composable () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    Column(
        modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(18.dp)).background(tone.tile).padding(12.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tone.ink)
        MoneyText(
            money = amount, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.ExtraBold,
            color = if (amount.minorUnits < 0L) colors.expenseText else colors.primaryText, modifier = Modifier.fillMaxWidth(),
        )
        note()
    }
}

// ---------------------------------------------------------------- semanas

@Composable
internal fun WeeklySection(weeks: List<WeekFlow>, monthNames: List<String>) {
    if (weeks.isEmpty() || weeks.all { it.income.minorUnits == 0L && it.expense.minorUnits == 0L }) return
    val colors = rememberCalmEmeraldColors()
    val tones = rememberPastelTones()
    val incomeTone = tones.at(0)
    val expenseTone = tones.at(4)
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val max = weeks.maxOf { maxOf(it.income.minorUnits, it.expense.minorUnits) }.coerceAtLeast(1L)
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(stringResource(R.string.analytics_weekly_title))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(incomeTone.fill, stringResource(R.string.analytics_kpi_income))
                Legend(expenseTone.fill, stringResource(R.string.analytics_kpi_expenses))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            weeks.forEachIndexed { index, week ->
                val on = selected == index
                val range = rangeLabel(week, monthNames)
                val label = stringResource(R.string.analytics_weekly_week, index + 1)
                val description = stringResource(R.string.analytics_weekly_desc, label, range, maskedPlain(week.income), maskedPlain(week.expense))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) tones.at(6).tile else Color.Transparent)
                        .selectable(selected = on, role = Role.Button, onClick = { selected = if (on) -1 else index })
                        .padding(horizontal = 2.dp, vertical = 8.dp)
                        .semantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.height(122.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                        Bar(week.income.minorUnits, max, incomeTone.fill)
                        Bar(week.expense.minorUnits, max, expenseTone.fill)
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.secondaryMuted)
                }
            }
        }
        val picked = weeks.getOrNull(selected)
        if (picked == null) {
            Text(stringResource(R.string.analytics_weekly_hint), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tones.at(6).tile).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.analytics_weekly_week, selected + 1) + " · " + rangeLabel(picked, monthNames),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeekFigure(stringResource(R.string.analytics_kpi_income), picked.income, Modifier.weight(1f))
                    WeekFigure(stringResource(R.string.analytics_kpi_expenses), picked.expense, Modifier.weight(1f))
                    WeekFigure(stringResource(R.string.analytics_kpi_net), picked.net, Modifier.weight(1f))
                }
            }
        }
        Text(
            stringResource(R.string.analytics_weekly_note), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

private fun rangeLabel(week: WeekFlow, monthNames: List<String>): String =
    if (week.start == week.endInclusive) week.start.dayMonth(monthNames)
    else if (week.start.month == week.endInclusive.month) "${week.start.dayOfMonth} – ${week.endInclusive.dayOfMonth} ${monthNames[week.endInclusive.monthValue - 1]}"
    else "${week.start.dayMonth(monthNames)} – ${week.endInclusive.dayMonth(monthNames)}"

@Composable
private fun Bar(value: Long, max: Long, color: Color) {
    val height = if (value <= 0L) 0.dp else (value.toFloat() / max * 118f).coerceAtLeast(4f).dp
    Box(Modifier.width(14.dp).height(height).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp)).background(color))
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = rememberCalmEmeraldColors().secondaryMuted)
    }
}

@Composable
private fun WeekFigure(label: String, money: Money, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = rememberCalmEmeraldColors().secondaryMuted, fontWeight = FontWeight.SemiBold)
        MoneyText(money = money, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = rememberCalmEmeraldColors().primaryText)
    }
}

// ---------------------------------------------------------------- categorías y comercios

@Composable
internal fun CategoriesSection(
    result: PeriodResult,
    previous: PeriodResult?,
    premium: Boolean,
    currency: Currency,
    options: AnalyticsFilterOptions,
    previousLabel: String,
    onOpenMovements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = options.categories.associate { it.id to it.name }
    val shares = result.categoryShares(currency, names)
    if (shares.isEmpty()) return
    val colors = rememberCalmEmeraldColors()
    val tones = rememberPastelTones()
    val uncategorized = stringResource(R.string.analytics_uncategorized)
    val unknown = stringResource(R.string.analytics_unknown_category)
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    SectionCard(modifier) {
        SectionLabel(stringResource(R.string.analytics_cats_title))
        Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            shares.forEachIndexed { index, share ->
                Box(Modifier.weight(share.share.coerceAtLeast(0.02f)).height(12.dp).clip(RoundedCornerShape(4.dp)).background(tones.at(index).fill))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            shares.forEachIndexed { index, share ->
                val tone = tones.at(index)
                val on = selected == index
                val name = share.name ?: if (share.categoryId == null) uncategorized else unknown
                val before = previous?.byCategory?.get(currency)?.get(share.categoryId)
                val change = if (premium && previous != null) before?.let { percentChange(share.amount, it) } else null
                val icon = options.categories.firstOrNull { it.id == share.categoryId }?.icon
                val rowDescription = stringResource(R.string.analytics_category_row_description, name, maskedPlain(share.amount), share.percentText)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) tone.tile else Color.Transparent)
                        .selectable(selected = on, role = Role.Button, onClick = { selected = if (on) -1 else index })
                        .padding(horizontal = 10.dp, vertical = 9.dp).semantics { contentDescription = rowDescription },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTile(tone, 36) {
                            Icon(if (icon != null) resolveCategoryIcon(icon) else Icons.Outlined.Category, contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp))
                        }
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when {
                            !premium -> Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.analytics_kpi_locked), tint = colors.warningText, modifier = Modifier.size(14.dp))
                            change != null -> DeltaChip(change)
                        }
                        MoneyText(money = share.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.pillTrack)) {
                        Box(Modifier.fillMaxWidth(share.share.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(tone.fill))
                    }
                }
            }
        }
        val picked = shares.getOrNull(selected)
        if (picked != null) {
            val tone = tones.at(selected)
            val merchants = result.byMerchant[currency]?.get(picked.categoryId).orEmpty().entries.sortedByDescending { it.value.minorUnits }
            val top = merchants.firstOrNull()?.value?.minorUnits?.coerceAtLeast(1L) ?: 1L
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tone.tile).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(picked.name ?: if (picked.categoryId == null) uncategorized else unknown, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tone.ink, modifier = Modifier.weight(1f))
                    MoneyText(money = picked.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
                }
                val before = previous?.byCategory?.get(currency)?.get(picked.categoryId)
                Text(
                    stringResource(R.string.analytics_cats_share, picked.percentText) + " · " + when {
                        !premium -> stringResource(R.string.analytics_cats_prev_locked)
                        before != null && before.minorUnits > 0L -> stringResource(R.string.analytics_cats_prev, maskedPlain(before), maskedPlain(picked.amount), previousLabel)
                        else -> stringResource(R.string.analytics_cats_prev_none)
                    },
                    style = MaterialTheme.typography.bodySmall, color = colors.primaryText,
                )
                if (merchants.isNotEmpty()) {
                    Text(stringResource(R.string.analytics_cats_where).uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp), fontWeight = FontWeight.Bold, color = tone.ink)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        merchants.forEach { (merchantId, amount) ->
                            val visual = options.merchants.firstOrNull { it.id == merchantId }?.merchant
                            val merchantName = visual?.name ?: stringResource(R.string.analytics_cats_no_merchant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (visual != null) {
                                    MerchantAvatar(
                                        drawableId = MerchantLogoDrawables.resolve(visual.logoKey), remoteLogoUrl = visual.logoUrl, remoteDarkLogoUrl = visual.logoDarkUrl,
                                        profile = getMerchantVisualProfile(visual.name, visual.brandColor), size = 34.dp,
                                    )
                                } else {
                                    IconTile(tone, 34) { Icon(Icons.Outlined.Storefront, contentDescription = null, tint = tone.ink, modifier = Modifier.size(18.dp)) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(merchantName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.primaryText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        MoneyText(money = amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText)
                                    }
                                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceCard.copy(alpha = 0.7f))) {
                                        Box(Modifier.fillMaxWidth((amount.minorUnits.toFloat() / top).coerceIn(0.03f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(tone.fill))
                                    }
                                }
                            }
                        }
                    }
                }
                DetailAction(stringResource(R.string.analytics_cats_cta), onOpenMovements)
            }
        }
    }
}

@Composable
internal fun IconTile(tone: PastelTone, size: Int, content: @Composable () -> Unit) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(tone.tile), contentAlignment = Alignment.Center) { content() }
}

// ---------------------------------------------------------------- saldo día a día

@Composable
internal fun BalanceLineSection(series: List<BalancePoint>, monthNames: List<String>) {
    // Before the accounts existed every day is zero: a flat line at zero says nothing, so the card stays out.
    if (series.size < 2 || series.all { it.balance.minorUnits == 0L }) return
    val colors = rememberCalmEmeraldColors()
    val tone = rememberPastelTones().at(0)
    var day by rememberSaveable(series.size) { mutableFloatStateOf((series.size - 1).toFloat()) }
    val index = day.toInt().coerceIn(0, series.lastIndex)
    val point = series[index]
    val values = series.map { it.balance.minorUnits }
    val min = values.min()
    val max = values.max()
    val delta = point.balance - series.first().balance
    val currency = point.balance.currency
    val lineDescription = stringResource(R.string.analytics_balance_desc)
    val scrubDescription = stringResource(R.string.analytics_balance_scrub) + ": " + point.date.dayOfMonth + " " + monthNames[point.date.monthValue - 1]
    SectionCard {
        SectionLabel(stringResource(R.string.analytics_balance_title))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MoneyText(money = point.balance, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
                Text(stringResource(R.string.analytics_balance_day, point.date.dayOfMonth, monthNames[point.date.monthValue - 1]), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            }
            val up = delta.minorUnits >= 0L
            val chipTone = rememberPastelTones().at(if (up) 0 else 2)
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(chipTone.tile).padding(horizontal = 10.dp, vertical = 4.dp)) {
                MoneyText(
                    amount = (if (up) "+" else "−") + com.kipu.app.ui.component.formatMinorUnits(kotlin.math.abs(delta.minorUnits)),
                    currencySymbol = currency.symbol(),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = chipTone.ink,
                )
            }
        }
        val track = colors.borderSubtle
        Canvas(Modifier.fillMaxWidth().height(112.dp).clearAndSetSemantics { contentDescription = lineDescription }) {
            val span = (max - min).coerceAtLeast(1L).toFloat()
            fun x(i: Int) = size.width * i / (series.size - 1)
            fun y(v: Long) = size.height * 0.92f - (v - min) / span * size.height * 0.78f
            for (g in 0..2) drawLine(track, Offset(0f, size.height * (0.2f + g * 0.3f)), Offset(size.width, size.height * (0.2f + g * 0.3f)), 1.dp.toPx())
            val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
            val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(tone.fill.copy(alpha = 0.45f), tone.fill.copy(alpha = 0f))))
            drawPath(line, tone.fill, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(colors.primaryText.copy(alpha = 0.25f), Offset(x(index), 0f), Offset(x(index), size.height), 1.5.dp.toPx())
            drawCircle(colors.surfaceCard, 7.dp.toPx(), Offset(x(index), y(values[index])))
            drawCircle(tone.ink, 7.dp.toPx(), Offset(x(index), y(values[index])), style = Stroke(width = 3.dp.toPx()))
        }
        Slider(
            value = day, onValueChange = { day = it }, valueRange = 0f..(series.size - 1).toFloat(), steps = (series.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = tone.ink, activeTrackColor = tone.fill, inactiveTrackColor = colors.pillTrack, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = scrubDescription },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.analytics_balance_min, maskedPlain(Money(min, currency))), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
            Text(stringResource(R.string.analytics_balance_max, maskedPlain(Money(max, currency))), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
        }
    }
}

// ---------------------------------------------------------------- crédito, deudas y meta

@Composable
internal fun CreditSection(overview: AnalyticsOverview, currency: Currency, navigation: AnalyticsNavigation) {
    val card = overview.cards.filter { it.card.currency == currency }.maxByOrNull { it.utilizationPercentage }
    val owed = overview.owed[currency]?.takeIf { it.minorUnits > 0L }
    val receivable = overview.receivable[currency]?.takeIf { it.minorUnits > 0L }
    val goal = overview.goal?.takeIf { it.target.currency == currency }
    if (card == null && owed == null && receivable == null && goal == null) return
    val colors = rememberCalmEmeraldColors()
    val tones = rememberPastelTones()
    SectionCard {
        SectionLabel(stringResource(R.string.analytics_credit_title))
        if (card != null) CardUsage(card, navigation.onOpenCard)
        if (owed != null || receivable != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LoanTile(stringResource(R.string.analytics_credit_owe), owed ?: Money.zero(currency), tones.at(2), Modifier.weight(1f), navigation.onOpenDebts)
                LoanTile(stringResource(R.string.analytics_credit_owed), receivable ?: Money.zero(currency), tones.at(0), Modifier.weight(1f), navigation.onOpenDebts)
            }
        }
        if (goal != null) GoalProgress(goal, navigation.onOpenGoals)
        if (card != null) Text(stringResource(R.string.analytics_credit_note), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
    }
}

@Composable
private fun CardUsage(item: CreditCardWithSummary, onOpen: (String) -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val tone = rememberPastelTones().at(if (item.utilizationPercentage >= 80.0) 4 else 3)
    val percent = item.utilizationPercentage.toInt().coerceIn(0, 100)
    val name = item.card.alias?.takeIf { it.isNotBlank() } ?: item.card.issuer
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tone.tile).clickable(role = Role.Button, onClick = { onOpen(item.card.id.value) }).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrandLogo(KipuBrand.from(item.card.issuer), item.card.issuer, null, size = 34.dp)
            Column(Modifier.weight(1f)) {
                Text("$name •••• ${item.card.lastFourDigits}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.analytics_credit_usage, percent), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tone.ink)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tone.ink)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.surfaceCard.copy(alpha = 0.7f))) {
            Box(Modifier.fillMaxWidth((percent / 100f).coerceAtLeast(0.02f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(tone.fill))
        }
        Text(
            stringResource(R.string.analytics_credit_debt, maskedPlain(item.debt), maskedPlain(Money(item.card.creditLimitMinorUnits, item.card.currency))),
            style = MaterialTheme.typography.bodySmall, color = colors.primaryText,
        )
    }
}

@Composable
private fun LoanTile(label: String, amount: Money, tone: PastelTone, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(tone.tile).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tone.ink)
        MoneyText(money = amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = rememberCalmEmeraldColors().primaryText)
    }
}

@Composable
private fun GoalProgress(goal: GoalSnapshot, onOpen: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val tone = rememberPastelTones().at(1)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tone.tile).clickable(role = Role.Button, onClick = onOpen).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.primaryText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.analytics_credit_goal, goal.percent), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = tone.ink)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.surfaceCard.copy(alpha = 0.7f))) {
            Box(Modifier.fillMaxWidth((goal.percent / 100f).coerceAtLeast(0.02f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(tone.fill))
        }
    }
}

// ---------------------------------------------------------------- avisos

@Composable
internal fun CompareBanner(expense: Money, previous: Money, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val diff = expense - previous
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.primaryDeep).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.analytics_compare_cta), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.onPrimaryDeep)
            Text(
                stringResource(R.string.analytics_compare_cta_sub, maskedPlain(Money(kotlin.math.abs(diff.minorUnits), diff.currency))),
                style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryDeep.copy(alpha = 0.92f),
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.onPrimaryDeep)
    }
}

@Composable
internal fun PremiumBanner(onClick: () -> Unit) {
    val tone = rememberPastelTones().at(5)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tone.tile).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(tone.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.analytics_premium_banner_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = tone.ink)
            Text(stringResource(R.string.analytics_premium_banner_sub), style = MaterialTheme.typography.bodySmall, color = tone.ink)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tone.ink)
    }
}

@Composable
internal fun NoBaseBanner() {
    val colors = rememberCalmEmeraldColors()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceCard).padding(16.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.analytics_no_base_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
        Text(stringResource(R.string.analytics_no_base_body), style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted)
    }
}

@Composable
internal fun EmptyMovementsSection() {
    val colors = rememberCalmEmeraldColors()
    val tone = rememberPastelTones().at(0)
    SectionCard(Modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(tone, 72) { Icon(Icons.Outlined.Category, contentDescription = null, tint = tone.ink, modifier = Modifier.size(34.dp)) }
            Text(stringResource(R.string.analytics_empty_movements_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = colors.primaryText, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.analytics_empty_movements_body), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
