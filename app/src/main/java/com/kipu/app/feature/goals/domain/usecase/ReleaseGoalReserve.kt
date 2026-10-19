package com.kipu.app.feature.goals.domain.usecase

import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant

data class ReleaseGoalReserveCommand(
    val eventId: String,
    val operationId: String,
    val backingAccountId: String,
    val amount: Money,
    val accountReserved: Money,
    val goalReserved: Money,
    val now: Instant = Instant.now(),
)

class ReleaseGoalReserve {
    fun execute(goal: Goal, command: ReleaseGoalReserveCommand): GoalPlanResult<GoalEvent> {
        if (goal.status == GoalStatus.ARCHIVED) return GoalPlanResult.Invalid("GOAL_ARCHIVED")
        if (command.amount.minorUnits <= 0L) return GoalPlanResult.Invalid("INVALID_AMOUNT")
        if (
            command.amount.currency != goal.currency ||
            command.accountReserved.currency != goal.currency ||
            command.goalReserved.currency != goal.currency
        ) {
            return GoalPlanResult.Invalid("CURRENCY_MISMATCH")
        }
        if (command.amount > command.accountReserved || command.amount > command.goalReserved) {
            return GoalPlanResult.Invalid("RESERVE_EXCEEDED")
        }
        return GoalPlanResult.Valid(
            GoalEvent(
                id = command.eventId,
                userId = goal.userId,
                goalId = goal.id,
                type = GoalEventType.RELEASE,
                delta = -command.amount,
                backingAccountId = command.backingAccountId,
                operationId = command.operationId,
                occurredAt = command.now,
            ),
        )
    }
}
