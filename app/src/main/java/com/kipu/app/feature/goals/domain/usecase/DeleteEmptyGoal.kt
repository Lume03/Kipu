package com.kipu.app.feature.goals.domain.usecase

import com.kipu.app.feature.goals.domain.model.Goal

class DeleteEmptyGoal {
    fun execute(
        goal: Goal,
        eventCount: Int,
        linkedTransferCount: Int,
        pendingOutboxCount: Int,
    ): GoalPlanResult<Boolean> {
        if (eventCount < 0 || linkedTransferCount < 0 || pendingOutboxCount < 0) {
            return GoalPlanResult.Invalid("INVALID_HISTORY_COUNT")
        }
        if (eventCount != 0 || linkedTransferCount != 0 || pendingOutboxCount != 0) {
            return GoalPlanResult.Invalid("HISTORY_PRESERVED")
        }
        return GoalPlanResult.Valid(true)
    }
}
