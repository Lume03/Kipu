package com.kipu.app.feature.goals.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.usecase.GoalPlanResult
import com.kipu.app.feature.goals.domain.usecase.GoalTransferIntent
import com.kipu.app.feature.goals.domain.usecase.ReserveGoalFunds
import com.kipu.app.feature.goals.domain.usecase.ReserveGoalFundsCommand
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReserveGoalFundsTest {
    @Test
    fun linkedAccountIsDefaultBackingButVirtualGoalRequiresExplicitSelection() {
        val linkedGoalResult = ReserveGoalFunds().execute(
            goal(linkedAccountId = "account-1"),
            command(backingAccountId = null),
        )
        val virtualGoalResult = ReserveGoalFunds().execute(
            goal(linkedAccountId = null),
            command(backingAccountId = null),
        )

        assertTrue(linkedGoalResult is GoalPlanResult.Valid)
        assertEquals("account-1", (linkedGoalResult as GoalPlanResult.Valid).value.event.backingAccountId)
        assertEquals("BACKING_ACCOUNT_REQUIRED", (virtualGoalResult as GoalPlanResult.Invalid).code)
    }

    @Test
    fun reserveOnlyDoesNotRequestLedgerMovementAndInsufficientBackingIsRejectedWhole() {
        val success = ReserveGoalFunds().execute(
            goal(),
            command(backingAccountId = "account-1"),
        )
        val insufficient = ReserveGoalFunds().execute(
            goal(),
            command(backingAccountId = "account-1", freeMinor = 5_000),
        )

        assertTrue(success is GoalPlanResult.Valid)
        assertNull((success as GoalPlanResult.Valid).value.event.transactionId)
        assertNull(success.value.transfer)
        assertEquals("INSUFFICIENT_BACKING", (insufficient as GoalPlanResult.Invalid).code)
    }

    @Test
    fun sameOriginAndDestinationIsNormalizedToReserveOnly() {
        val result = ReserveGoalFunds().execute(
            goal(),
            command(
                backingAccountId = "account-2",
                transfer = GoalTransferIntent("account-2", "account-2", Currency.PEN),
            ),
        )

        assertTrue(result is GoalPlanResult.Valid)
        assertNull((result as GoalPlanResult.Valid).value.transfer)
    }

    @Test
    fun explicitDifferentAccountTransferIsRetainedAsIntent() {
        val intent = GoalTransferIntent("account-1", "account-2", Currency.PEN)
        val result = ReserveGoalFunds().execute(
            goal(),
            command(backingAccountId = "account-2", transfer = intent),
        )

        assertTrue(result is GoalPlanResult.Valid)
        assertEquals(intent, (result as GoalPlanResult.Valid).value.transfer)
        assertEquals("account-2", result.value.event.backingAccountId)
    }

    private fun command(
        backingAccountId: String?,
        freeMinor: Long = 50_000,
        transfer: GoalTransferIntent? = null,
    ) = ReserveGoalFundsCommand(
        eventId = "event-1",
        operationId = "operation-1",
        backingAccountId = backingAccountId,
        amount = Money(10_000, Currency.PEN),
        freeBacking = Money(freeMinor, Currency.PEN),
        accountCurrency = Currency.PEN,
        transfer = transfer,
        now = Instant.parse("2026-10-17T12:00:00Z"),
    )

    private fun goal(linkedAccountId: String? = null) = Goal(
        id = "goal-1",
        userId = "user-1",
        name = "Meta",
        target = Money(100_000, Currency.PEN),
        linkedAccountId = linkedAccountId,
        status = GoalStatus.ACTIVE,
        createdAt = Instant.parse("2026-10-17T12:00:00Z"),
        updatedAt = Instant.parse("2026-10-17T12:00:00Z"),
    )
}
