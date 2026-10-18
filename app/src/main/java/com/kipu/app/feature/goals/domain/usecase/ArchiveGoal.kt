package com.kipu.app.feature.goals.domain.usecase

import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant

class ArchiveGoal {
    fun execute(
        goal: Goal,
        expectedRevision: Long,
        currentReserved: Money,
        now: Instant = Instant.now(),
    ): GoalPlanResult<Goal> {
        if (goal.revision != expectedRevision) return GoalPlanResult.Invalid("REVISION_MISMATCH")
        if (goal.status == GoalStatus.ARCHIVED) return GoalPlanResult.Valid(goal)
        if (currentReserved.currency != goal.currency) return GoalPlanResult.Invalid("CURRENCY_MISMATCH")
        if (currentReserved.minorUnits != 0L) return GoalPlanResult.Invalid("RESERVE_UNRESOLVED")
        return GoalPlanResult.Valid(
            goal.copy(
                status = GoalStatus.ARCHIVED,
                revision = Math.addExact(goal.revision, 1L),
                updatedAt = now,
            ),
        )
    }
}
