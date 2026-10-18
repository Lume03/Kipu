package com.kipu.app.feature.goals.domain.usecase

import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant

data class GoalReassignmentCommand(
    val operationId: String,
    val sourceEventId: String,
    val destinationEventId: String,
    val sourceExpectedRevision: Long,
    val destinationExpectedRevision: Long,
    val sourceBackingAccountId: String,
    val destinationBackingAccountId: String,
    val amount: Money,
    val sourceReservedForAccount: Money,
    val now: Instant = Instant.now(),
)

data class GoalReassignmentPlan(
    val sourceEvent: GoalEvent,
    val destinationEvent: GoalEvent,
)

class ReassignGoalReserve {
    fun execute(
        source: Goal,
        destination: Goal,
        command: GoalReassignmentCommand,
    ): GoalPlanResult<GoalReassignmentPlan> {
        if (source.id == destination.id) return GoalPlanResult.Invalid("SAME_GOAL")
        if (source.userId != destination.userId) return GoalPlanResult.Invalid("OWNER_OR_ACCOUNT_INVALID")
        if (source.status == GoalStatus.ARCHIVED) return GoalPlanResult.Invalid("GOAL_ARCHIVED")
        if (destination.status != GoalStatus.ACTIVE) return GoalPlanResult.Invalid("DESTINATION_NOT_ACTIVE")
        if (source.revision != command.sourceExpectedRevision || destination.revision != command.destinationExpectedRevision) {
            return GoalPlanResult.Invalid("REVISION_MISMATCH")
        }
        if (source.currency != destination.currency || command.amount.currency != source.currency) {
            return GoalPlanResult.Invalid("CURRENCY_MISMATCH")
        }
        if (command.amount.minorUnits <= 0L) return GoalPlanResult.Invalid("INVALID_AMOUNT")
        if (command.amount > command.sourceReservedForAccount) return GoalPlanResult.Invalid("RESERVE_EXCEEDED")
        if (command.sourceBackingAccountId.isBlank() || command.destinationBackingAccountId.isBlank()) {
            return GoalPlanResult.Invalid("BACKING_ACCOUNT_REQUIRED")
        }
        if (command.sourceBackingAccountId != command.destinationBackingAccountId) {
            return GoalPlanResult.Invalid("BACKING_CHANGE_REQUIRES_TRANSFER")
        }

        return GoalPlanResult.Valid(
            GoalReassignmentPlan(
                sourceEvent = GoalEvent(
                    id = command.sourceEventId,
                    userId = source.userId,
                    goalId = source.id,
                    type = GoalEventType.REASSIGNMENT_OUT,
                    delta = -command.amount,
                    backingAccountId = command.sourceBackingAccountId,
                    operationId = command.operationId,
                    occurredAt = command.now,
                ),
                destinationEvent = GoalEvent(
                    id = command.destinationEventId,
                    userId = destination.userId,
                    goalId = destination.id,
                    type = GoalEventType.REASSIGNMENT_IN,
                    delta = command.amount,
                    backingAccountId = command.destinationBackingAccountId,
                    operationId = command.operationId,
                    occurredAt = command.now,
                ),
            ),
        )
    }
}
