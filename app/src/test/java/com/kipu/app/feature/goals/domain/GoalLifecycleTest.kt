package com.kipu.app.feature.goals.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.usecase.ArchiveGoal
import com.kipu.app.feature.goals.domain.usecase.GoalPlanResult
import com.kipu.app.feature.goals.domain.usecase.GoalReassignmentCommand
import com.kipu.app.feature.goals.domain.usecase.ReassignGoalReserve
import com.kipu.app.feature.goals.domain.usecase.ReleaseGoalReserve
import com.kipu.app.feature.goals.domain.usecase.ReleaseGoalReserveCommand
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalLifecycleTest {
    @Test
    fun partialReleaseHasNegativeDeltaAndDoesNotCreateTransaction() {
        val result = ReleaseGoalReserve().execute(
            goal = goal("goal-1", GoalStatus.COMPLETED),
            command = ReleaseGoalReserveCommand(
                eventId = "release-1",
                operationId = "operation-1",
                backingAccountId = "account-1",
                amount = Money(4_000, Currency.PEN),
                accountReserved = Money(10_000, Currency.PEN),
                goalReserved = Money(10_000, Currency.PEN),
                now = NOW,
            ),
        )

        assertTrue(result is GoalPlanResult.Valid)
        val event = (result as GoalPlanResult.Valid).value
        assertEquals(-4_000L, event.delta.minorUnits)
        assertEquals(GoalEventType.RELEASE, event.type)
        assertEquals(null, event.transactionId)
    }

    @Test
    fun excessiveReleaseIsRejectedWithoutPartialEvent() {
        val result = ReleaseGoalReserve().execute(
            goal = goal("goal-1"),
            command = ReleaseGoalReserveCommand(
                eventId = "release-1",
                operationId = "operation-1",
                backingAccountId = "account-1",
                amount = Money(11_000, Currency.PEN),
                accountReserved = Money(10_000, Currency.PEN),
                goalReserved = Money(10_000, Currency.PEN),
                now = NOW,
            ),
        )

        assertEquals("RESERVE_EXCEEDED", (result as GoalPlanResult.Invalid).code)
    }

    @Test
    fun reassignmentCreatesPairedAuditEventsOnSameBackingWithoutLedgerMovement() {
        val result = ReassignGoalReserve().execute(
            source = goal("goal-1"),
            destination = goal("goal-2"),
            command = GoalReassignmentCommand(
                operationId = "operation-1",
                sourceEventId = "event-out",
                destinationEventId = "event-in",
                sourceExpectedRevision = 2,
                destinationExpectedRevision = 2,
                sourceBackingAccountId = "account-1",
                destinationBackingAccountId = "account-1",
                amount = Money(3_000, Currency.PEN),
                sourceReservedForAccount = Money(5_000, Currency.PEN),
                now = NOW,
            ),
        )

        assertTrue(result is GoalPlanResult.Valid)
        val plan = (result as GoalPlanResult.Valid).value
        assertEquals(-3_000L, plan.sourceEvent.delta.minorUnits)
        assertEquals(3_000L, plan.destinationEvent.delta.minorUnits)
        assertEquals(null, plan.sourceEvent.transactionId)
        assertEquals(null, plan.destinationEvent.transactionId)
    }

    @Test
    fun reassignmentToDifferentBackingRequiresAnExplicitTransfer() {
        val result = ReassignGoalReserve().execute(
            source = goal("goal-1"),
            destination = goal("goal-2"),
            command = GoalReassignmentCommand(
                operationId = "operation-1",
                sourceEventId = "event-out",
                destinationEventId = "event-in",
                sourceExpectedRevision = 2,
                destinationExpectedRevision = 2,
                sourceBackingAccountId = "account-1",
                destinationBackingAccountId = "account-2",
                amount = Money(3_000, Currency.PEN),
                sourceReservedForAccount = Money(5_000, Currency.PEN),
                now = NOW,
            ),
        )

        assertEquals("BACKING_CHANGE_REQUIRES_TRANSFER", (result as GoalPlanResult.Invalid).code)
    }

    @Test
    fun goalCannotBeArchivedWhileAnyReserveRemains() {
        val result = ArchiveGoal().execute(
            goal = goal("goal-1"),
            expectedRevision = 2,
            currentReserved = Money(1, Currency.PEN),
            now = NOW,
        )

        assertEquals("RESERVE_UNRESOLVED", (result as GoalPlanResult.Invalid).code)
    }

    @Test
    fun emptyGoalCanBeArchivedAndItsHistoryModelRemainsAvailable() {
        val event = GoalEvent(
            id = "old-event",
            userId = "user-1",
            goalId = "goal-1",
            type = GoalEventType.ALLOCATION,
            delta = Money(1_000, Currency.PEN),
            backingAccountId = "account-1",
            operationId = "old-operation",
            occurredAt = NOW,
        )
        val result = ArchiveGoal().execute(
            goal = goal("goal-1"),
            expectedRevision = 2,
            currentReserved = Money.zero(Currency.PEN),
            now = NOW,
        )

        assertEquals(1_000L, event.delta.minorUnits)
        assertEquals(GoalStatus.ARCHIVED, (result as GoalPlanResult.Valid).value.status)
    }

    private fun goal(id: String, status: GoalStatus = GoalStatus.ACTIVE) = Goal(
        id = id,
        userId = "user-1",
        name = "Meta",
        target = Money(20_000, Currency.PEN),
        status = status,
        revision = 2,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-17T12:00:00Z")
    }
}
