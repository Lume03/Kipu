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
