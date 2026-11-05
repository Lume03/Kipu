package com.kipu.app.feature.widgets.data

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.ui.component.KipuBrand
import java.time.LocalDate

/** What a home screen widget renders. Built once per refresh from the same sources the app screens use. */
sealed interface WidgetState {
    /** No verified owner on this phone: the widget invites to open Kipu. */
    data object SignedOut : WidgetState

    /** The local data could not be read right now; the widget keeps a neutral message instead of zeros. */
    data object Unavailable : WidgetState

    data class Ready(val data: WidgetData) : WidgetState
}

data class WidgetData(
    val hideAmounts: Boolean,
    val today: LocalDate,
    val currency: Currency,
    val hasAccounts: Boolean,
    val total: Money,
    val free: Money,
    val inGoals: Money,
    val upcoming: Money,
    val accounts: List<AccountRow>,
    /** Daily balance of the liquid accounts for the last 30 days, in minor units, oldest first. */
    val balanceSeries: List<Long>,
    val dues: List<DueRow>,
    val budgets: List<BudgetRow>,
    val goal: GoalRow?,
    val netWorth: Money?,
    val owed: Money,
    val receivable: Money,
    val pace: PaceData?,
    val captures: CaptureInfo,
)

data class AccountRow(val name: String, val balance: Money, val brand: KipuBrand?, val isCash: Boolean)

data class DueRow(
    val title: String,
    val amount: Money,
    val date: LocalDate,
    val brand: KipuBrand?,
    val overdue: Boolean,
    val isCard: Boolean,
    val destinationId: String,
)

data class BudgetRow(
    val id: String,
    val name: String,
    val iconId: String?,
    val limit: Money,
    val consumed: Money,
    val percent: Int,
    val periodEnd: LocalDate?,
) {
    val remaining: Money get() = limit - consumed
}

data class GoalRow(
    val id: String,
    val name: String,
    val iconKey: String?,
    val reserved: Money,
    val target: Money,
    val percent: Int,
    val targetDate: LocalDate?,
    /** Twelve weeks, oldest first: true when something was set aside that week. */
    val weeks: List<Boolean>,
    val streak: Int,
)

data class PaceData(
    val spent: Money,
    val income: Money,
    /** Monthly total budget when there is one, otherwise last month's spending; null when neither exists. */
    val reference: Money?,
    val referenceIsBudget: Boolean,
    /** Cumulative net expense at the end of each day of the month so far, minor units. */
    val cumulative: List<Long>,
    val dayOfMonth: Int,
    val daysInMonth: Int,
    val previousSameDays: Money?,
) {
    /** Where the reference would be today if it were spread evenly over the month. */
    val idealToday: Money?
        get() = reference?.let { Money(it.minorUnits * dayOfMonth / daysInMonth, it.currency) }
}

data class CaptureRow(
    val id: String,
    val provider: String,
    val amount: Money?,
    val party: String?,
    val whenMillis: Long?,
    val needsCategory: Boolean,
)

data class CaptureInfo(
    val premium: Boolean,
    val pending: List<CaptureRow>,
    val pendingCount: Int,
    val autoToday: Int,
)
