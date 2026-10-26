package com.kipu.app.feature.accounts.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.core.finance.domain.CreditCalculations
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.presentation.FinancialTerminology
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.components.colorFromHex
import com.kipu.app.ui.component.BrandLogo
import com.kipu.app.ui.component.CardArtThumbnail
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.KipuSkeletonBlock
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.component.MaskedCardReference
import com.kipu.app.ui.component.MoneyText
import com.kipu.app.ui.component.loadingSemantics
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.math.BigInteger
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val DASHBOARD_HERO_TAG = "dashboard_hero"
internal const val DASHBOARD_LOADING_TAG = "dashboard_loading"
internal const val DASHBOARD_ERROR_TAG = "dashboard_error"
internal const val DASHBOARD_PLAN_TAG = "dashboard_plan_card"

private val HeroLabel = Color(0xFFBDEFE6)
private val HeroDot = Color(0xFF7DE0CF)
private val HeroSubtitle = Color(0xFFCFEFE9)

/** A bank (or standalone) account and the wallets that spend from its balance. */
internal data class DashboardAccountGroup(
    val account: AccountWithBalance,
    val wallets: List<AccountWithBalance> = emptyList(),
)

/**
 * Yape/Plin affiliated to an account share its balance, so they are shown under it and never as
 * a separate amount. A wallet whose account is not in the list (archived) stays on its own row.
 */
internal fun groupAccountsWithWallets(accounts: List<AccountWithBalance>): List<DashboardAccountGroup> {
    val ids = accounts.map { it.account.id }.toSet()
    val walletsByParent = accounts
        .filter { it.account.linkedAccountId != null && it.account.linkedAccountId in ids }
        .groupBy { requireNotNull(it.account.linkedAccountId) }
    return accounts
        .filter { it.account.linkedAccountId == null || it.account.linkedAccountId !in ids }
        .map { DashboardAccountGroup(it, walletsByParent[it.account.id].orEmpty()) }
}

internal fun accountsCountLabel(groups: List<DashboardAccountGroup>): String {
    val active = groups.size
    val wallets = groups.sumOf { it.wallets.size }
    val activeText = "$active ${if (active == 1) "activa" else "activas"}"
    return if (wallets == 0) activeText else "$activeText · $wallets ${if (wallets == 1) "billetera" else "billeteras"}"
}

internal fun planUsageLabel(used: Int, max: Int): String = "$used de $max cuentas y tarjetas"

/** Sums own-money balances by currency; affiliated wallets are excluded because they share a balance. */
internal fun sumByCurrency(accounts: List<AccountWithBalance>, include: (AccountWithBalance) -> Boolean): Map<Currency, Long> {
    val map = linkedMapOf<Currency, BigInteger>()
    accounts.filter(include).forEach { item ->
        val currency = item.balance.currency
        map[currency] = map.getOrDefault(currency, BigInteger.ZERO).add(BigInteger.valueOf(item.balance.minorUnits))
    }
    if (map.isEmpty()) map[Currency.PEN] = BigInteger.ZERO
    return map.mapValues { (_, total) ->
        total.coerceIn(BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }
}

/** H1/H2: own money only (accounts and cash); credit is never part of this figure. */
@Composable
internal fun DashboardHero(
    totalPen: Money,
    totalUsd: Money?,
    bankByCurrency: Map<Currency, Long>,
    cashByCurrency: Map<Currency, Long>,
    isMasked: Boolean,
    onToggleMasked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val fontScale = LocalDensity.current.fontScale
    val hasUsd = totalUsd != null
    Surface(
        modifier = modifier.fillMaxWidth().testTag(DASHBOARD_HERO_TAG),
        shape = RoundedCornerShape(26.dp),
        color = colors.heroGradientStart,
        contentColor = Color.White,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(HeroDot))
                Text(
                    text = FinancialTerminology.REAL_MONEY_AVAILABLE,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.7.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = HeroLabel,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Switch, onClick = onToggleMasked)
                        .semantics {
                            contentDescription = if (isMasked) "Mostrar importes" else "Ocultar importes"
                            stateDescription = if (isMasked) "Importes ocultos" else "Importes visibles"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isMasked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            val heroSize = when {
                hasUsd -> 28.sp
                fontScale >= 1.25f -> 32.sp
                else -> 38.sp
            }
            if (hasUsd) {
                Text("Soles (PEN)", style = MaterialTheme.typography.labelSmall, color = HeroLabel)
            }
            MoneyText(
                money = totalPen,
                isMasked = isMasked,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = heroSize,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    fontFeatureSettings = "tnum",
                ),
                color = Color.White,
            )
            if (totalUsd != null) {
                Text("Dólares (USD)", style = MaterialTheme.typography.labelSmall, color = HeroLabel)
                MoneyText(
                    money = totalUsd,
                    isMasked = isMasked,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                    color = Color.White,
                )
            }
            Text(
                text = "Dinero propio en cuentas y efectivo · No incluye crédito",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = HeroSubtitle,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(14.dp))
            val stack = fontScale >= 1.3f
            val panels: @Composable (Modifier, Modifier) -> Unit = { first, second ->
                HeroPanel("En cuentas bancarias", bankByCurrency, isMasked, first)
                HeroPanel("En efectivo", cashByCurrency, isMasked, second)
            }
            if (stack) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    panels(Modifier.fillMaxWidth(), Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    panels(Modifier.weight(1f), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroPanel(label: String, byCurrency: Map<Currency, Long>, isMasked: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = FontWeight.SemiBold, color = HeroLabel)
        byCurrency.forEach { (currency, minor) ->
            MoneyText(
                minorUnits = minor,
                currency = currency,
                isMasked = isMasked,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                color = Color.White,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun DashboardCalendarEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("home_calendar_entry"),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceCard,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(Icons.Outlined.CalendarMonth, background = colors.navSelectedPill.copy(alpha = if (colors.isDark) 1f else 0.12f), tint = if (colors.isDark) colors.navSelectedContent else colors.primaryDeep)
            Column(Modifier.weight(1f)) {
                Text("Calendario financiero", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.ExtraBold, color = colors.primaryText)
                Text(
                    "Movimientos, cierres, vencimientos y cuotas del mes",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.secondaryMuted,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.secondaryMuted)
        }
    }
}

/** Uses the real Free quota ([FreePlanLimits.instruments]); the CTA only routes to plans. */
@Composable
internal fun DashboardPlanCard(used: Int, max: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val reached = max in 1..used
    val title = if (colors.isDark) colors.warningText else Color(0xFF5A3200)
    val chipBg = if (colors.isDark) colors.warningBorder else Color(0xFFFDEBB0)
    val filled = colors.warningAmber
    val empty = if (colors.isDark) colors.pillTrack else Color(0xFFF3E3B0)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(DASHBOARD_PLAN_TAG),
        shape = RoundedCornerShape(20.dp),
        color = if (colors.isDark) colors.warningBg else Color(0xFFFFF8E1),
        border = BorderStroke(1.5.dp, if (colors.isDark) colors.warningBorder else Color(0xFFF3D58A)),
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).width(200.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(chipBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = if (colors.isDark) colors.warningText else Color(0xFF8A4B00), modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Plan Básico · ${planUsageLabel(used, max)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = title,
                    )
                    Row(
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().semantics {
                            contentDescription = "${used.coerceAtMost(max)} de $max espacios usados"
                        },
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        repeat(max.coerceIn(1, 12)) { index ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (index < used) filled else empty),
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(chipBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    if (reached) "Mejorar a PRO" else "Disponibles",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = title,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun DashboardSectionHeader(
    title: String,
    count: String,
    addLabel: String,
    onAdd: () -> Unit,
    manageLabel: String,
    onManage: () -> Unit,
    showManage: Boolean = true,
    addTag: String? = null,
) {
    val colors = rememberCalmEmeraldColors()
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            fontWeight = FontWeight.ExtraBold,
            color = colors.primaryText,
            modifier = Modifier.semantics { heading() },
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.pillTrack)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(count, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = FontWeight.Bold, color = colors.secondaryMuted)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.heightIn(min = 48.dp).then(if (addTag != null) Modifier.testTag(addTag) else Modifier),
            ) {
                Text(addLabel, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), fontWeight = FontWeight.Bold, color = colors.primaryDeep)
            }
            if (showManage) {
                TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(manageLabel, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), fontWeight = FontWeight.Bold, color = colors.primaryDeep)
                }
            }
        }
    }
}

@Composable
internal fun DashboardAccountGroupCard(
    group: DashboardAccountGroup,
    isMasked: Boolean,
    onAccountClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceCard,
        shadowElevation = 1.dp,
    ) {
        Column {
            AccountRow(group.account, isMasked) { onAccountClick(group.account.account.id.value) }
            group.wallets.forEach { wallet ->
                WalletRow(wallet = wallet, parentName = group.account.account.alias) {
                    onAccountClick(wallet.account.id.value)
                }
            }
        }
    }
}

@Composable
private fun AccountRow(item: AccountWithBalance, isMasked: Boolean, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val account = item.account
    val isCash = account.type == AccountType.CASH
    val preset = AccountPreset.normalizedFor(account.type, account.preset)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isCash) {
            IconTile(
                Icons.Outlined.Payments,
                background = if (colors.isDark) colors.incomeBg else Color(0xFFDDF5E6),
                tint = if (colors.isDark) colors.incomeText else Color(0xFF14532D),
                size = 48,
            )
        } else {
            BrandLogo(
                brand = KipuBrand.from(preset?.takeIf { it != AccountPreset.GENERIC }?.name),
                fallbackName = account.alias,
                contentDescription = null,
                size = 48.dp,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    account.alias,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (account.preset == AccountPreset.BCP && account.alias.contains("Sueldo", ignoreCase = true)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(colors.incomeBg).padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text("Principal", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold, color = colors.incomeText)
                    }
                }
            }
            val issuer = preset?.takeIf { it != AccountPreset.CASH && it != AccountPreset.GENERIC }?.defaultName
            val typeLabel = account.type.dashboardLabel()
            Text(
                text = when {
                    account.linkedAccountId != null -> "${issuer ?: typeLabel} · Saldo compartido · ${account.currency.name}"
                    issuer != null -> "$issuer · $typeLabel · ${account.currency.name}"
                    else -> "$typeLabel · ${account.currency.name}"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.secondaryMuted,
            )
            if (account.isPlanLocked) {
                Text("Bloqueado por el plan Free", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.expenseText)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                money = item.balance,
                isMasked = isMasked,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                color = colors.primaryText,
            )
            Text(
                text = if (isCash) "En bolsillo" else "● Disponible",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = if (isCash) FontWeight.SemiBold else FontWeight.Bold,
                color = if (isCash) colors.secondaryMuted else colors.incomeText,
            )
        }
    }
}

@Composable
private fun WalletRow(wallet: AccountWithBalance, parentName: String, onClick: () -> Unit) {
    val colors = rememberCalmEmeraldColors()
    val brand = KipuBrand.from(wallet.account.preset?.name)
    val isYape = brand == KipuBrand.YAPE
    val rowBg = when {
        colors.isDark -> colors.pillTrack
        isYape -> Color(0xFFF6F1FB)
        else -> Color(0xFFF1F9FB)
    }
    val divider = if (colors.isDark) colors.borderSubtle else if (isYape) Color(0xFFE9E1F3) else Color(0xFFE1EEF3)
    val chipBg = if (colors.isDark) colors.surfaceCard else if (isYape) Color(0xFFE8DAF5) else Color(0xFFCFF5F3)
    val chipText = if (colors.isDark) colors.primaryText else if (isYape) Color(0xFF5B1F8F) else Color(0xFF0B6B68)
    val name = (brand?.displayName ?: wallet.account.alias).uppercase(Locale.ROOT)
    val stack = LocalDensity.current.fontScale > 1.15f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick),
    ) {
        HorizontalDivider(color = divider)
        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = if (stack) Modifier.fillMaxWidth() else Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BrandLogo(brand = brand, fallbackName = wallet.account.alias, contentDescription = brand?.displayName ?: wallet.account.alias, size = 32.dp)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                        append(" · billetera con saldo compartido")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.primaryText,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(chipBg).padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("Usa el saldo de $parentName", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = FontWeight.Bold, color = chipText)
            }
        }
    }
}

@Composable
internal fun DashboardWalletFootnote() {
    Text(
        "Plin y Yape no suman saldo aparte: sus movimientos se descuentan de la cuenta bancaria afiliada.",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
        color = rememberCalmEmeraldColors().secondaryMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** H5: no instruments yet; the first step is a single clear action. */
@Composable
internal fun DashboardNoAccountsCard(onAddAccount: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier = modifier.fillMaxWidth().testTag("dashboard_empty_setup"),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceCard,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(colors.navSelectedPill.copy(alpha = if (colors.isDark) 1f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = if (colors.isDark) colors.navSelectedContent else colors.primaryDeep, modifier = Modifier.size(34.dp))
            }
            Text("Aún no tienes cuentas", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = colors.primaryText)
            Text(
                "Agrega una cuenta, tu efectivo o una billetera para empezar a ver tu dinero real.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.secondaryMuted,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onAddAccount,
                modifier = Modifier.padding(top = 6.dp).heightIn(min = 52.dp).testTag("dashboard_empty_add_account"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryDeep, contentColor = colors.onPrimaryDeep),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar mi primera cuenta", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun DashboardNoCardsNotice() {
    val colors = rememberCalmEmeraldColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceCard,
        shadowElevation = 1.dp,
    ) {
        Text(
            "Sin tarjetas de crédito. Puedes registrar una con su línea y fechas de corte y vencimiento.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = colors.secondaryMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(18.dp),
        )
    }
}

/** Credit is shown as debt and available line, never as own money. */
@Composable
internal fun DashboardCreditCardRow(item: CreditCardWithSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val card = item.card
    val isMasked = LocalBalanceMasked.current
    val utilization = item.utilizationPercentage
    val style = CardStylePresets.byId(card.stylePresetId)
    val title = card.alias ?: "${card.issuer} ${card.network}"
    val nextDue = remember(card.dueDay) { CreditCalculations.calculateNextDate(card.dueDay) }
    val dueText = remember(nextDue) { nextDue.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))) }
    val (statusLabel, statusBg, statusText) = when {
        card.isArchived -> Triple("Archivada · Deuda pendiente", colors.expenseBg, colors.expenseText)
        isMasked -> Triple("Uso oculto", colors.pillTrack, colors.secondaryMuted)
        utilization > 100.0 -> Triple("Sobreutilizada", colors.expenseBg, colors.expenseText)
        utilization >= 80.0 -> Triple("Uso alto", colors.warningBg, colors.warningText)
        utilization >= 50.0 -> Triple("Uso moderado", colors.warningBg, colors.warningText)
        else -> Triple("En uso normal", colors.incomeBg, colors.incomeText)
    }
    val barColor = when {
        utilization >= 80.0 -> colors.expenseText
        utilization >= 50.0 -> colors.warningAmber
        else -> colors.incomeText
    }
    val reducedMotion = rememberReducedMotionEnabled()
    val progress by animateFloatAsState(
        targetValue = if (isMasked) 0f else (utilization / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "dashboardCardUtilization",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceCard,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).width(200.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CardArtThumbnail(
                        presetId = card.stylePresetId,
                        tierLabel = style?.tierLabel ?: card.network.name,
                        startColor = style?.let { colorFromHex(it.gradientStartHex) } ?: Color(0xFF0F172A),
                        endColor = style?.let { colorFromHex(it.gradientEndHex) } ?: Color(0xFF334155),
                        contentDescription = null,
                        width = 64.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.ExtraBold, color = colors.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${card.issuer} · ${card.network} ·", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.secondaryMuted)
                            MaskedCardReference(card.lastFourDigits, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.secondaryMuted)
                        }
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusBg)
                        .border(1.dp, statusText.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(statusLabel, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), fontWeight = FontWeight.Bold, color = statusText)
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardFigure("Deuda actual", item.debt, isMasked, Alignment.Start)
                CardFigure("Crédito disponible", item.availableCredit, isMasked, Alignment.End)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uso de línea · ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = FontWeight.SemiBold, color = colors.secondaryMuted)
                    MoneyText(
                        money = Money(card.creditLimitMinorUnits, card.currency),
                        isMasked = isMasked,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.secondaryMuted,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .width(80.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.pillTrack),
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(8.dp).background(barColor))
                }
                Text(
                    if (isMasked) "••%" else "${utilization.toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isMasked) colors.secondaryMuted else statusText,
                )
            }
            Row(
                modifier = Modifier.padding(top = 12.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null, tint = colors.primaryDeep, modifier = Modifier.size(16.dp))
                Text(
                    buildAnnotatedString {
                        append("Próximo pago: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(dueText) }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.primaryText,
                )
            }
        }
    }
}

@Composable
private fun CardFigure(label: String, money: Money, isMasked: Boolean, alignment: Alignment.Horizontal) {
    val colors = rememberCalmEmeraldColors()
    Column(horizontalAlignment = alignment, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = FontWeight.SemiBold, color = colors.secondaryMuted)
        MoneyText(
            money = money,
            isMasked = isMasked,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            color = colors.primaryText,
        )
    }
}

internal data class ArchivedRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val debt: Money? = null,
    val isCard: Boolean,
)

/** H4: archived instruments keep their history; opening one lets the user reactivate it. */
@Composable
internal fun DashboardArchivedSection(
    rows: List<ArchivedRow>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (ArchivedRow) -> Unit,
) {
    val colors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(if (reducedMotion) 0 else 200), label = "archivedChevron")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
            shape = RoundedCornerShape(18.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.5.dp, colors.borderSubtle),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Archive, contentDescription = null, tint = colors.secondaryMuted, modifier = Modifier.size(22.dp))
                Text("Cuentas y tarjetas archivadas", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), fontWeight = FontWeight.Bold, color = colors.primaryText, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(colors.pillTrack).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("${rows.size}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), fontWeight = FontWeight.Bold, color = colors.secondaryMuted)
                }
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.secondaryMuted,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = colors.surfaceCard,
                shadowElevation = 1.dp,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { onOpen(row) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold, color = colors.primaryText)
                                Text(row.subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.secondaryMuted)
                                if (row.debt != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Deuda pendiente: ", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.expenseText)
                                        MoneyText(money = row.debt, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.expenseText)
                                    }
                                }
                            }
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(colors.pillTrack).padding(horizontal = 9.dp, vertical = 3.dp)) {
                                Text("Archivada", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = FontWeight.Bold, color = colors.secondaryMuted)
                            }
                        }
                        HorizontalDivider(color = colors.pillTrack)
                    }
                    Text(
                        "Conservan su historial y no ocupan un cupo de tu plan. Ábrelas para reactivarlas.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.secondaryMuted,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** H7: placeholders with the same shape as the content; nothing is shown as zero while loading. */
@Composable
internal fun DashboardLoadingSkeleton(modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    val onHero = Color.White.copy(alpha = 0.16f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DASHBOARD_LOADING_TAG)
            .loadingSemantics("Cargando tu dinero"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.heroGradientStart.copy(alpha = 0.9f))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KipuSkeletonBlock(Modifier.fillMaxWidth(0.45f), height = 12.dp, color = onHero)
            KipuSkeletonBlock(Modifier.fillMaxWidth(0.6f), height = 38.dp, color = onHero)
            KipuSkeletonBlock(Modifier.fillMaxWidth(0.8f), height = 12.dp, color = onHero)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KipuSkeletonBlock(Modifier.weight(1f), height = 58.dp, color = onHero, cornerRadius = 16.dp)
                KipuSkeletonBlock(Modifier.weight(1f), height = 58.dp, color = onHero, cornerRadius = 16.dp)
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceCard).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KipuSkeletonBlock(Modifier.fillMaxWidth(0.35f), height = 14.dp)
            KipuSkeletonBlock(Modifier.fillMaxWidth(0.5f), height = 26.dp)
        }
        repeat(2) { KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 64.dp, cornerRadius = 18.dp) }
        repeat(2) { KipuSkeletonBlock(Modifier.fillMaxWidth(), height = 72.dp, cornerRadius = 18.dp) }
    }
}

@Composable
private fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, tint: Color, size: Int = 46) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

internal fun AccountType.dashboardLabel(): String = when (this) {
    AccountType.CASH -> "Efectivo"
    AccountType.SAVINGS -> "Ahorros"
    AccountType.BANK -> "Corriente"
    AccountType.DIGITAL_WALLET -> "Billetera"
    AccountType.CREDIT_LIABILITY -> "Pasivo de tarjeta"
}
