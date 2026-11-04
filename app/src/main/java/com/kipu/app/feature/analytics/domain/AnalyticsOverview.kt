package com.kipu.app.feature.analytics.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.AccountWithBalance
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import java.time.LocalDate

/** Signed effect of one ledger entry on a liquid account, dated by the movement it belongs to. */
data class LedgerDelta(val accountId: String, val currency: String, val deltaMinor: Long, val atMillis: Long)

data class BalancePoint(val date: LocalDate, val balance: Money)

object BalanceSeriesCalculator {
    /**
     * Balance at the end of every day of [period], walking back from [currentBalance] by undoing the entries
     * that happened later. Only [accountIds] count, so the last point of a period that ends today equals the
     * balance the rest of the app shows.
     */
    fun series(period: AnalyticsPeriod, currentBalance: Money, accountIds: Set<String>, deltas: List<LedgerDelta>): List<BalancePoint> {
        val relevant = deltas.filter { it.accountId in accountIds && Currency.fromCode(it.currency) == currentBalance.currency }
        val days = generateSequence(period.start) { it.plusDays(1) }.takeWhile { !it.isAfter(period.endInclusive) }.toList()
        return days.map { day ->
            val endOfDay = day.plusDays(1).atStartOfDay(period.zone).toInstant().toEpochMilli()
            val later = relevant.filter { it.atMillis >= endOfDay }.sumOf { it.deltaMinor }
            BalancePoint(day, Money(currentBalance.minorUnits - later, currentBalance.currency))
        }
    }
}

enum class DestinationKind { FREE, GOALS, UPCOMING }

data class DestinationSlice(val kind: DestinationKind, val amount: Money)

object MoneyDestinationCalculator {
    /**
     * Splits [total] into what is set aside for goals, what is about to be paid and what is free. Each part is
     * capped by what remains, so the slices always add up to [total] and a debt never makes the free part negative.
     */
    fun slices(total: Money, reserved: Money, upcoming: Money): List<DestinationSlice> {
        val currency = total.currency
        val safeTotal = total.minorUnits.coerceAtLeast(0L)
        val goals = reserved.minorUnits.coerceIn(0L, safeTotal)
        val payments = upcoming.minorUnits.coerceIn(0L, safeTotal - goals)
        val free = safeTotal - goals - payments
        return listOf(
            DestinationSlice(DestinationKind.FREE, Money(free, currency)),
            DestinationSlice(DestinationKind.GOALS, Money(goals, currency)),
            DestinationSlice(DestinationKind.UPCOMING, Money(payments, currency)),
        )
    }
}

/** Branding data of a merchant used in the expenses; the logo URL is resolved where the owner session is known. */
data class MerchantVisual(
    val id: String, val name: String, val logoKey: String?, val brandColor: String?,
    val logoUrl: String?, val logoDarkUrl: String?,
)

data class GoalSnapshot(val goalId: String, val name: String, val reserved: Money, val target: Money, val iconKey: String?) {
    val percent: Int get() = if (target.minorUnits <= 0L) 0 else (reserved.minorUnits * 100 / target.minorUnits).toInt().coerceIn(0, 100)
}

/** Everything the "money" half of Análisis shows that does not depend on the movements of the chosen period. */
data class AnalyticsOverview(
    val accounts: List<AccountWithBalance> = emptyList(),
    val reservedByAccount: Map<String, Money> = emptyMap(),
    val upcoming: Map<Currency, Money> = emptyMap(),
    val upcomingUntil: LocalDate? = null,
    val cards: List<CreditCardWithSummary> = emptyList(),
    val owed: Map<Currency, Money> = emptyMap(),
    val receivable: Map<Currency, Money> = emptyMap(),
    val goal: GoalSnapshot? = null,
    val deltas: List<LedgerDelta> = emptyList(),
) {
    fun accountsIn(currency: Currency): List<AccountWithBalance> = accounts.filter { it.account.currency == currency }
    fun total(currency: Currency): Money = accountsIn(currency).fold(Money.zero(currency)) { sum, item -> sum + item.balance }
    fun reserved(currency: Currency): Money = accountsIn(currency).fold(Money.zero(currency)) { sum, item ->
        sum + (reservedByAccount[item.account.id.value] ?: Money.zero(currency))
    }
    val currencies: List<Currency> get() = accounts.map { it.account.currency }.distinct().sortedBy { it.ordinal }

    companion object { val Empty = AnalyticsOverview() }
}
