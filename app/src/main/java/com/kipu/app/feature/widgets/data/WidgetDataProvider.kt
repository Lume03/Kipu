package com.kipu.app.feature.widgets.data

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.analytics.data.AnalyticsRepository
import com.kipu.app.feature.analytics.domain.AnalyticsOverview
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.BalanceSeriesCalculator
import com.kipu.app.feature.analytics.domain.ExpenseRow
import com.kipu.app.feature.analytics.domain.MoneyDestinationCalculator
import com.kipu.app.feature.analytics.domain.DestinationKind
import com.kipu.app.feature.analytics.domain.ObserveAnalyticsOverview
import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetOutcome
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.GetBudgetConsumption
import com.kipu.app.feature.budgets.presentation.BudgetAppearanceStore
import com.kipu.app.feature.budgets.presentation.BudgetCategoryOptionsSource
import com.kipu.app.feature.capture.data.local.CaptureCandidateDao
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DueOrigin
import com.kipu.app.feature.debts.domain.model.DueStatus
import com.kipu.app.feature.debts.domain.usecase.ObserveUpcomingDues
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.home.domain.NetWorthSummary
import com.kipu.app.feature.home.domain.ObserveNetWorthSummary
import com.kipu.app.feature.plans.domain.VerifiedPlanAccess
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.ui.component.KipuBrand
import com.kipu.app.ui.component.normalizeBrandKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads, once, everything the widgets need. It reuses the use cases behind the Dinero, Planificación and Análisis
 * screens, so a widget can never disagree with the app. Nothing here writes.
 */
@Singleton
class WidgetDataProvider @Inject constructor(
    private val session: WidgetSessionStore,
    private val profileDao: ProfilePreferencesDao,
    private val overview: ObserveAnalyticsOverview,
    private val dues: ObserveUpcomingDues,
    private val netWorth: ObserveNetWorthSummary,
    private val analytics: AnalyticsRepository,
    private val budgets: BudgetRepository,
    private val consumption: GetBudgetConsumption,
    private val categoryOptions: BudgetCategoryOptionsSource,
    private val budgetAppearance: BudgetAppearanceStore,
    private val goals: GoalRepository,
    private val captureDao: CaptureCandidateDao,
    private val planAccess: VerifiedPlanAccess,
    private val zone: ZoneId,
) {
    suspend fun load(): WidgetState {
        val owner = session.ownerId ?: return WidgetState.SignedOut
        return try {
            withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { build(owner) } ?: WidgetState.Unavailable
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WidgetState.Unavailable
        }
    }

    private suspend fun build(owner: String): WidgetState {
        val today = LocalDate.now(zone)
        val month = AnalyticsPeriod.currentMonth(today, zone)
        val last30 = AnalyticsPeriod(today.minusDays(29), today, zone)
        val data = overview(owner, last30, today).first()
        val currency = data.currencies.firstOrNull { it == Currency.PEN } ?: data.currencies.firstOrNull() ?: Currency.PEN

        val hide = runCatching { UUID.fromString(owner) }.getOrNull()
            ?.let { profileDao.findProfile(it)?.hideBalances } ?: false

        val total = data.total(currency)
        val slices = MoneyDestinationCalculator.slices(total, data.reserved(currency), data.upcoming[currency] ?: Money.zero(currency))
        fun slice(kind: DestinationKind) = slices.first { it.kind == kind }.amount

        val accounts = data.accountsIn(currency).sortedByDescending { it.balance.minorUnits }.map { item ->
            val preset = AccountPreset.normalizedFor(item.account.type, item.account.preset)
            AccountRow(
                name = item.account.alias,
                balance = item.balance,
                brand = if (item.account.type == AccountType.CASH) null else KipuBrand.from(preset?.name),
                isCash = item.account.type == AccountType.CASH,
            )
        }
        val series = BalanceSeriesCalculator
            .series(last30, total, data.accountsIn(currency).map { it.account.id.value }.toSet(), data.deltas)
            .map { it.balance.minorUnits }

        val summary = netWorth(owner).first()
        val netWorthMoney = (summary as? NetWorthSummary.Available)?.byCurrency?.firstOrNull { it.currency == currency }?.netWorth

        return WidgetState.Ready(
            WidgetData(
                hideAmounts = hide,
                today = today,
                currency = currency,
                hasAccounts = data.accounts.isNotEmpty(),
                total = total,
                free = slice(DestinationKind.FREE),
                inGoals = slice(DestinationKind.GOALS),
                upcoming = slice(DestinationKind.UPCOMING),
                accounts = accounts,
                balanceSeries = series,
                dues = loadDues(owner, currency),
                budgets = loadBudgets(owner, currency, today),
                goal = loadGoal(owner, today),
                netWorth = netWorthMoney,
                owed = data.owed[currency] ?: Money.zero(currency),
                receivable = data.receivable[currency] ?: Money.zero(currency),
                pace = loadPace(owner, currency, today, month),
                captures = loadCaptures(owner, currency, today),
            ),
        )
    }

    private suspend fun loadDues(owner: String, currency: Currency): List<DueRow> =
        dues(owner).first().rows
            .filter { it.status != DueStatus.PAID && it.obligationType == DebtObligationType.PAYABLE && it.outstanding.currency == currency }
            .sortedBy { it.dueDate }
            .take(MAX_DUES)
            .map { due ->
                DueRow(
                    title = due.title,
                    amount = due.outstanding,
                    date = due.dueDate,
                    brand = if (due.origin == DueOrigin.CARD) brandIn(due.title) else null,
                    overdue = due.status == DueStatus.OVERDUE,
                    isCard = due.origin == DueOrigin.CARD,
                    destinationId = due.destinationId,
                )
            }

    private fun brandIn(text: String): KipuBrand? {
        val key = normalizeBrandKey(text)
        return KipuBrand.entries.firstOrNull { brand ->
            val name = normalizeBrandKey(brand.displayName)
            key == name || key.startsWith("$name ") || key.contains(" $name ") || key.endsWith(" $name")
        }
    }

    private suspend fun loadBudgets(owner: String, currency: Currency, today: LocalDate): List<BudgetRow> {
        val options = categoryOptions.observe(owner).first().associateBy { it.id }
        return budgets.listBudgets(owner)
            .filter { it.isActive && it.currency == currency.name }
            .mapNotNull { budget ->
                val current = (consumption.current(owner, budget.id, today, zone) as? BudgetOutcome.Success)?.value ?: return@mapNotNull null
                val categoryId = (budget.scope as? BudgetScope.Category)?.categoryId
                BudgetRow(
                    id = budget.id,
                    name = budget.name,
                    iconId = if (categoryId != null) options[categoryId]?.iconId else budgetAppearance.icon(owner, budget.id),
                    limit = Money(current.consumption.limitMinor, currency),
                    consumed = Money(current.consumption.consumedMinor, currency),
                    percent = current.consumption.percentWhole.coerceAtMost(999L).toInt(),
                    periodEnd = current.period.interval.lastDay,
                )
            }
            .sortedByDescending { it.percent }
    }

    private suspend fun loadGoal(owner: String, today: LocalDate): GoalRow? {
        val active = goals.observeGoals(owner).first().filter { it.status == GoalStatus.ACTIVE }
        if (active.isEmpty()) return null
        val progress = active.map { goal ->
            val events = goals.observeEvents(owner, goal.id).first()
            Triple(goal, GoalProgressCalculator.calculate(goal, events), events)
        }
        val best = progress.filter { it.second.reserved.minorUnits > 0L }.maxByOrNull { it.second.progressPercent } ?: progress.first()
        val (goal, result, events) = best
        val weeks = weeklyContributions(events, today)
        return GoalRow(
            id = goal.id,
            name = goal.name,
            iconKey = goal.iconKey,
            reserved = result.reserved,
            target = result.target,
            percent = result.progressPercent.toInt().coerceIn(0, 100),
            targetDate = goal.targetDate,
            weeks = weeks,
            streak = streakOf(weeks),
        )
    }

    private fun weeklyContributions(events: List<GoalEvent>, today: LocalDate): List<Boolean> {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val first = thisMonday.minusWeeks((WEEKS - 1).toLong())
        val hits = BooleanArray(WEEKS)
        events.asSequence()
            .filter { !it.transactionVoided && it.delta.minorUnits > 0L && it.type != GoalEventType.ADJUSTMENT }
            .forEach { event ->
                val date = event.occurredAt.atZone(zone).toLocalDate()
                val index = java.time.temporal.ChronoUnit.WEEKS.between(first, date.with(DayOfWeek.MONDAY)).toInt()
                if (index in 0 until WEEKS) hits[index] = true
            }
        return hits.toList()
    }

    /** Consecutive weeks with a contribution; the week in progress never breaks it. */
    private fun streakOf(weeks: List<Boolean>): Int {
        var index = weeks.lastIndex
        if (index >= 0 && !weeks[index]) index--
        var streak = 0
        while (index >= 0 && weeks[index]) { streak++; index-- }
        return streak
    }

    private suspend fun loadPace(owner: String, currency: Currency, today: LocalDate, month: AnalyticsPeriod): PaceData? {
        val rows = analytics.observe(owner, month).first().filter { it.currency == currency.name }
        val incomes = analytics.observeIncomes(owner, month).first().filter { it.currency == currency.name }
        val perDay = LongArray(today.dayOfMonth)
        rows.forEach { row ->
            val day = Instant.ofEpochMilli(row.occurredAtMillis).atZone(zone).dayOfMonth
            if (day in 1..perDay.size) perDay[day - 1] += net(row)
        }
        var running = 0L
        val cumulative = perDay.map { running += it; running }
        val previous = month.previous()
        val previousSame = analytics.observe(owner, previous).first().filter { it.currency == currency.name }.sumOf(::net)
        val daysInMonth = YearMonth.from(today).lengthOfMonth()

        val monthlyTotal = budgets.listBudgets(owner)
            .filter { it.isActive && it.currency == currency.name && it.scope is BudgetScope.Total && it.isMonthly() }
            .firstNotNullOfOrNull { budget ->
                (consumption.current(owner, budget.id, today, zone) as? BudgetOutcome.Success)?.value?.consumption?.limitMinor
            }
        val lastMonth = YearMonth.from(today).minusMonths(1)
        val lastMonthSpent = analytics.observe(owner, AnalyticsPeriod(lastMonth.atDay(1), lastMonth.atEndOfMonth(), zone)).first()
            .filter { it.currency == currency.name }.sumOf(::net)
        val reference = monthlyTotal?.takeIf { it > 0L } ?: lastMonthSpent.takeIf { it > 0L }
        return PaceData(
            spent = Money(cumulative.lastOrNull() ?: 0L, currency),
            income = Money(incomes.sumOf { it.amountMinor }, currency),
            reference = reference?.let { Money(it, currency) },
            referenceIsBudget = monthlyTotal != null && monthlyTotal > 0L,
            cumulative = cumulative,
            dayOfMonth = today.dayOfMonth,
            daysInMonth = daysInMonth,
            previousSameDays = previousSame.takeIf { it > 0L }?.let { Money(it, currency) },
        )
    }

    private fun Budget.isMonthly() = (schedule as? BudgetSchedule.Recurring)?.type == BudgetPeriodType.MONTHLY

    private fun net(row: ExpenseRow): Long = (row.amountMinor - row.refundedMinor).coerceAtLeast(0L)

    private suspend fun loadCaptures(owner: String, currency: Currency, today: LocalDate): CaptureInfo {
        val premium = planAccess.isPremiumVerified(owner)
        val pending = captureDao.observePending(owner).first()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val autoToday = captureDao.all(owner).count { it.status == "AUTO_REGISTERED" && (it.resolvedAt ?: 0L) >= startOfToday }
        return CaptureInfo(
            premium = premium,
            pending = pending.take(MAX_CAPTURES).map { row ->
                CaptureRow(
                    id = row.id,
                    provider = row.provider,
                    amount = row.amountMinor?.let { Money(it, row.currency?.let(Currency::fromCode) ?: currency) },
                    party = row.merchantSourceText,
                    whenMillis = row.occurredAt ?: row.createdAt,
                    needsCategory = row.categoryId == null,
                )
            },
            pendingCount = pending.size,
            autoToday = autoToday,
        )
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 12_000L
        const val MAX_DUES = 6
        const val MAX_CAPTURES = 3
        const val WEEKS = 12
    }
}
