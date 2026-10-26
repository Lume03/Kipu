package com.kipu.app.feature.goals.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.usecase.CreateGoal
import com.kipu.app.feature.goals.domain.usecase.CreateGoalCommand
import com.kipu.app.feature.goals.domain.usecase.GoalPlanResult
import com.kipu.app.feature.goals.domain.usecase.UpdateGoal
import com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalManagementTest {
    @Test
    fun createAllowsVirtualGoalWithoutLinkedAccountAndDoesNotCreateFinancialEvents() {
        val result = CreateGoal().execute(
            CreateGoalCommand(
                goalId = "goal-1",
                userId = "user-1",
                name = "  Viaje  ",
                target = Money(100_000, Currency.PEN),
                linkedAccountId = null,
                now = Instant.parse("2026-10-17T12:00:00Z"),
            ),
        )

        assertTrue(result is GoalPlanResult.Valid)
        val goal = (result as GoalPlanResult.Valid).value
        assertEquals("Viaje", goal.name)
        assertNull(goal.linkedAccountId)
        assertEquals(GoalStatus.ACTIVE, goal.status)
        assertEquals(1L, goal.revision)
        assertEquals(0L, GoalProgressCalculator.calculate(goal, emptyList()).reserved.minorUnits)
    }

    @Test
    fun createRejectsBlankNameAndNonPositiveTarget() {
        val blankName = CreateGoal().execute(
            CreateGoalCommand("goal-1", "user-1", " ", Money(100, Currency.PEN)),
        )
        val invalidTarget = CreateGoal().execute(
            CreateGoalCommand("goal-2", "user-1", "Viaje", Money(0, Currency.PEN)),
        )

        assertEquals("NAME_REQUIRED", (blankName as GoalPlanResult.Invalid).code)
        assertEquals("TARGET_MUST_BE_POSITIVE", (invalidTarget as GoalPlanResult.Invalid).code)
    }

    @Test
    fun raisingCompletedGoalAboveItsReserveReactivatesItWithoutChangingReserve() {
        val completed = goal(targetMinor = 10_000, status = GoalStatus.COMPLETED)
        val result = UpdateGoal().execute(
            completed,
            UpdateGoalCommand(
                expectedRevision = 4,
                name = completed.name,
                target = Money(20_000, Currency.PEN),
                targetDate = completed.targetDate,
                linkedAccountId = completed.linkedAccountId,
                now = Instant.parse("2026-10-17T12:00:00Z"),
            ),
            currentReserved = Money(12_000, Currency.PEN),
        )

        assertTrue(result is GoalPlanResult.Valid)
        assertEquals(GoalStatus.ACTIVE, (result as GoalPlanResult.Valid).value.status)
        assertEquals(20_000L, result.value.target.minorUnits)
        val existingContribution = GoalEvent(
            id = "event-1",
            userId = "user-1",
            goalId = completed.id,
            type = GoalEventType.ALLOCATION,
            delta = Money(12_000, Currency.PEN),
            backingAccountId = "account-1",
            operationId = "operation-1",
            occurredAt = Instant.parse("2026-10-16T12:00:00Z"),
        )
        assertEquals(
            12_000L,
            GoalProgressCalculator.calculate((result as GoalPlanResult.Valid).value, listOf(existingContribution))
                .reserved.minorUnits,
        )
    }

    @Test
    fun reducingTargetToOrBelowReserveCompletesGoalAndPreservesAccountLink() {
        val linked = goal(targetMinor = 20_000, linkedAccountId = "account-1")
        val result = UpdateGoal().execute(
            linked,
            UpdateGoalCommand(
                expectedRevision = linked.revision,
                name = linked.name,
                target = Money(8_000, Currency.PEN),
                targetDate = linked.targetDate,
                linkedAccountId = linked.linkedAccountId,
                now = Instant.parse("2026-10-17T12:00:00Z"),
            ),
            currentReserved = Money(8_000, Currency.PEN),
        )

        assertTrue(result is GoalPlanResult.Valid)
        assertEquals(GoalStatus.COMPLETED, (result as GoalPlanResult.Valid).value.status)
        assertEquals("account-1", result.value.linkedAccountId)
    }

    private fun goal(
        targetMinor: Long,
        status: GoalStatus = GoalStatus.ACTIVE,
        linkedAccountId: String? = null,
    ) = Goal(
        id = "goal-1",
        userId = "user-1",
        name = "Viaje",
        target = Money(targetMinor, Currency.PEN),
        linkedAccountId = linkedAccountId,
        status = status,
        revision = 4,
        createdAt = Instant.parse("2026-10-16T12:00:00Z"),
        updatedAt = Instant.parse("2026-10-16T12:00:00Z"),
    )
}
