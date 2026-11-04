package com.kipu.app.feature.analytics.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.analytics.data.AnalyticsRepository
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DueStatus
import com.kipu.app.feature.debts.domain.usecase.ObserveUpcomingDues
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Composes the money half of Análisis from the existing sources: balances and cards from the dashboard,
 * reserves from the goals, upcoming payments from the dues calendar and loans from the debts. Nothing here is
 * written or recalculated; a source that fails just leaves its part empty instead of blocking the screen.
 */
class ObserveAnalyticsOverview @Inject constructor(
    private val dashboard: ObserveFinancialDashboard,
    private val goals: GoalRepository,
    private val dues: ObserveUpcomingDues,
    private val debts: DebtRepository,
    private val analytics: AnalyticsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(owner: String, period: AnalyticsPeriod, today: LocalDate): Flow<AnalyticsOverview> {
        val endOfMonth = YearMonth.from(today).atEndOfMonth()
        val goalsPart = combine(goals.observeGoals(owner), goals.observeBackingAccounts(owner)) { all, backing -> all to backing }
            .flatMapLatest { (all, backing) ->
                val active = all.filter { it.status == GoalStatus.ACTIVE }
                val reserved = backing.associate { it.id to it.reserved.coerceAtMost(it.confirmedBalance) }
                if (active.isEmpty()) flowOf(reserved to null)
                else combine(active.map { goal -> goals.observeEvents(owner, goal.id).map { GoalProgressCalculator.calculate(goal, it) } }) { progress ->
                    val best = progress.filter { it.status == GoalStatus.ACTIVE && it.reserved.minorUnits > 0L }
                        .maxByOrNull { it.progressPercent } ?: progress.first()
                    val goal = active.first { it.id == best.goalId }
                    reserved to GoalSnapshot(goal.id, goal.name, best.reserved, best.target, goal.iconKey)
                }
            }.catch { emit(emptyMap<String, Money>() to null) }
        val duesPart = dues(owner).map { snapshot ->
            snapshot.rows.filter { it.status != DueStatus.PAID && it.obligationType == DebtObligationType.PAYABLE && it.dueDate <= endOfMonth }
                .groupBy { it.outstanding.currency }
                .mapValues { (currency, rows) -> rows.fold(Money.zero(currency)) { sum, row -> sum + row.outstanding } }
        }.catch { emit(emptyMap()) }
        val loansPart = debts.observeDebts(owner).map { all ->
            val active = all.filter { it.status == DebtLifecycleStatus.ACTIVE }
            fun total(type: DebtObligationType) = active.filter { it.obligationType == type }
                .groupBy { Currency.fromCode(it.currencyCode) }
                .mapValues { (currency, rows) -> Money(rows.sumOf { it.remainingPrincipalMinor.coerceAtLeast(0L) }, currency) }
            total(DebtObligationType.PAYABLE) to total(DebtObligationType.RECEIVABLE)
        }.catch { emit(emptyMap<Currency, Money>() to emptyMap()) }
        return combine(
            dashboard(), goalsPart, duesPart, loansPart, analytics.observeLedgerDeltas(owner, period).catch { emit(emptyList()) },
        ) { data, (reserved, goal), upcoming, (owed, receivable), deltas ->
            AnalyticsOverview(
                accounts = data.liquidAccounts.filter { !it.account.isArchived && it.account.linkedAccountId == null },
                reservedByAccount = reserved,
                upcoming = upcoming,
                upcomingUntil = endOfMonth,
                cards = data.creditCards.filter { !it.card.isArchived },
                owed = owed,
                receivable = receivable,
                goal = goal,
                deltas = deltas,
            )
        }
    }
}

private fun Money.coerceAtMost(limit: Money): Money = if (this > limit) limit else this
