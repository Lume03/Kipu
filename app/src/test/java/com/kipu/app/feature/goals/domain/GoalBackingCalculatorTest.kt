package com.kipu.app.feature.goals.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalBackingCalculator.ReservationValidation
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalBackingCalculatorTest {
    @Test
    fun oneGoalAggregatesSuccessiveReservationsFromTwoAccounts() {
        val goal = com.kipu.app.feature.goals.domain.model.Goal(
            id = "goal-1", userId = "user-1", name = "Viaje", target = Money(100_000, Currency.PEN),
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        val events = listOf(event("a", goal.id, "account-a", 20_000), event("b", goal.id, "account-b", 30_000))
        val progress = GoalProgressCalculator.calculate(goal, events)
        assertEquals(50_000L, progress.reserved.minorUnits)
        assertEquals(0, progress.progressPercent.compareTo(java.math.BigDecimal("50")))
        assertEquals(20_000L, GoalBackingCalculator.position("account-a", Money(80_000, Currency.PEN), events).reserved.minorUnits)
        assertEquals(30_000L, GoalBackingCalculator.position("account-b", Money(90_000, Currency.PEN), events).reserved.minorUnits)
    }

    @Test
    fun laterExpenseCreatesSharedDeficitAndBlocksNewReservesWithoutReducingEitherGoal() {
        val events = listOf(event("a", "goal-1", "account-a", 20_000), event("b", "goal-2", "account-a", 30_000))
        val before = events.toList()
        val funded = GoalBackingCalculator.position("account-a", Money(60_000, Currency.PEN), events)
        val afterExpense = GoalBackingCalculator.position("account-a", Money(40_000, Currency.PEN), events)
        assertEquals(0L, funded.shortfall.minorUnits)
        assertEquals(10_000L, afterExpense.shortfall.minorUnits)
        assertEquals(50_000L, afterExpense.reserved.minorUnits)
        assertEquals(-10_000L, afterExpense.available.minorUnits)
        assertEquals(ReservationValidation.InsufficientBacking,
            GoalBackingCalculator.validateReservation(Money(1, Currency.PEN), afterExpense.available, Currency.PEN))
        assertEquals(before, events)
    }

    @Test
    fun sharedBackingSubtractsReservationsAcrossGoalsOnlyOncePerAccount() {
        val events = listOf(
            event("e1", "goal-1", "account-a", 20_000),
            event("e2", "goal-2", "account-a", 10_000),
            event("e3", "goal-3", "account-b", 5_000),
        )

        val position = GoalBackingCalculator.position(
            accountId = "account-a",
            confirmedBalance = Money(100_000, Currency.PEN),
            events = events,
        )

        assertEquals(30_000L, position.reserved.minorUnits)
        assertEquals(70_000L, position.available.minorUnits)
        assertEquals(0L, position.shortfall.minorUnits)
    }

    @Test
    fun confirmedBalanceBelowReservationsSurfacesShortfallWithoutClampingReserve() {
        val position = GoalBackingCalculator.position(
            accountId = "account-a",
            confirmedBalance = Money(25_000, Currency.PEN),
            events = listOf(event("e1", "goal-1", "account-a", 40_000)),
        )

        assertEquals(40_000L, position.reserved.minorUnits)
        assertEquals(-15_000L, position.available.minorUnits)
        assertEquals(15_000L, position.shortfall.minorUnits)
    }

    @Test
    fun reserveIsRejectedAsAWholeWhenFreeBackingIsInsufficientOrCurrencyDiffers() {
        val insufficient = GoalBackingCalculator.validateReservation(
            amount = Money(20_000, Currency.PEN),
            freeBacking = Money(10_000, Currency.PEN),
            accountCurrency = Currency.PEN,
        )
        val mismatch = GoalBackingCalculator.validateReservation(
            amount = Money(20_000, Currency.PEN),
            freeBacking = Money(50_000, Currency.PEN),
            accountCurrency = Currency.USD,
        )

        assertEquals(ReservationValidation.InsufficientBacking, insufficient)
        assertEquals(ReservationValidation.CurrencyMismatch, mismatch)
    }

    @Test
    fun voidedTransferDoesNotRemainInBackingReservation() {
        val position = GoalBackingCalculator.position(
            accountId = "account-a",
            confirmedBalance = Money(100_000, Currency.PEN),
            events = listOf(event("e1", "goal-1", "account-a", 40_000, transactionVoided = true)),
        )

        assertEquals(0L, position.reserved.minorUnits)
        assertTrue(position.available.minorUnits == position.confirmedBalance.minorUnits)
    }

    private fun event(
        id: String,
        goalId: String,
        accountId: String?,
        delta: Long,
        transactionVoided: Boolean = false,
    ) = GoalEvent(
        id = id,
        userId = "user-1",
        goalId = goalId,
        type = if (delta < 0) GoalEventType.RELEASE else GoalEventType.ALLOCATION,
        delta = Money(delta, Currency.PEN),
        backingAccountId = accountId,
        operationId = "operation-$id",
        transactionVoided = transactionVoided,
        occurredAt = Instant.parse("2026-10-17T12:00:00Z"),
    )
}
