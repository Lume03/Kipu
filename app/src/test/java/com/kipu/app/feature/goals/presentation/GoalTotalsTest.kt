package com.kipu.app.feature.goals.presentation

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalProgress
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalTotalsTest {
    private fun item(id: String, target: Long, reserved: Long, currency: Currency = Currency.PEN,
        status: GoalStatus = GoalStatus.ACTIVE): GoalListItemState {
        val goal = Goal(id, "owner", id, Money(target, currency), status = status,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        return GoalListItemState(goal, GoalProgress(id, Money(reserved, currency), goal.target, BigDecimal.ZERO, status))
    }

    @Test fun `totals include only visible active goals and keep currencies separate`() {
        val state = GoalManagementUiState(goals = listOf(
            item("one", 100_000, 20_000), item("two", 100_000, 30_000),
            item("three", 100_000, 0), item("four", 100_000, 0),
            item("usd", 50_000, 7_000, Currency.USD),
            item("completed", 100_000, 100_000, status = GoalStatus.COMPLETED),
            item("archived", 100_000, 10_000, status = GoalStatus.ARCHIVED),
            item("locked", 100_000, 10_000, status = GoalStatus.LOCKED_BY_PLAN),
            item("paused", 100_000, 10_000), item("removed", 100_000, 10_000),
        ), pausedGoalIds = setOf("paused"), removedGoalIds = setOf("removed"))

        assertEquals(mapOf(Currency.PEN to GoalCurrencyTotals(400_000, 50_000),
            Currency.USD to GoalCurrencyTotals(50_000, 7_000)), goalTotals(state.displaySections().active))
    }

    @Test fun `no active goals yields no currency totals`() {
        assertEquals(emptyMap<Currency, GoalCurrencyTotals>(), goalTotals(listOf(
            item("completed", 100_000, 100_000, status = GoalStatus.COMPLETED),
            item("locked", 100_000, 0, status = GoalStatus.LOCKED_BY_PLAN),
        )))
    }
}
