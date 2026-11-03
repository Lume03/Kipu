package com.kipu.app.feature.analytics

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditCardWithSummary
import com.kipu.app.feature.accounts.domain.model.FinancialDashboardData
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.goals.domain.GoalBackingCalculator
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.home.domain.NetWorthSummary
import com.kipu.app.feature.home.domain.ObserveNetWorthSummary
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsNetWorthFixtureTest {
    @Test fun reservationAndCreditLineAreNotAdditionalAssetsButReceivableIsIncluded() = runTest {
        val money = { value: Long -> Money(value, Currency.PEN) }
        val reservation = GoalEvent("event", "owner", "goal", GoalEventType.ALLOCATION, money(4000), "cash", null, occurredAt = Instant.EPOCH)
        val backing = GoalBackingCalculator.position("cash", money(10000), listOf(reservation))
        assertEquals(money(4000), backing.reserved)
        val dashboard = FinancialDashboardData(
            totalPen = backing.confirmedBalance, totalUsd = Money.zero(Currency.USD), liquidAccounts = emptyList(),
            creditCards = listOf(CreditCardWithSummary(mockk<CreditCard>(), money(3000), money(17000), 15.0)), activeComputableCount = 1,
        )
        val dashboardSource = mockk<ObserveFinancialDashboard>()
        every { dashboardSource.invoke() } returns flowOf(dashboard)
        val receivable = DebtSummary("debt", "owner", DebtObligationType.RECEIVABLE, "A", 2000, 2000, "PEN", LocalDate.of(2026,1,1), null, null, null, DebtLifecycleStatus.ACTIVE, "SYNCED", 1)
        val debts = mockk<DebtRepository>()
        every { debts.observeDebts("owner") } returns flowOf(listOf(receivable))
        val summary = ObserveNetWorthSummary(dashboardSource, debts)("owner").first() as NetWorthSummary.Available
        val position = summary.byCurrency.single()
        assertEquals(money(10000), position.realAssets)
        assertEquals(money(2000), position.receivables)
        assertEquals(money(3000), position.liabilities)
        assertEquals(money(9000), position.netWorth)
    }
}
