package com.kipu.app.feature.goals.domain.usecase

import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant
import java.time.LocalDate

data class UpdateGoalCommand(
    val expectedRevision: Long,
    val name: String,
    val target: Money,
    val targetDate: LocalDate?,
    val linkedAccountId: String?,
    val iconKey: String? = null,
    val colorArgb: Int? = null,
    val replacementGoalId: String? = null,
    val replacementExpectedRevision: Long? = null,
    val hasPremiumAccess: Boolean = false,
    val now: Instant = Instant.now(),
) {
    init {
        require((replacementGoalId == null) == (replacementExpectedRevision == null)) {
            "Replacement goal and expected revision must be provided together"
        }
        require(replacementGoalId == null || replacementGoalId.isNotBlank())
        require(replacementExpectedRevision == null || replacementExpectedRevision >= 0L)
    }
}

class UpdateGoal {
    fun execute(
        current: Goal,
        command: UpdateGoalCommand,
        currentReserved: Money,
    ): GoalPlanResult<Goal> {
        if (current.revision != command.expectedRevision) return GoalPlanResult.Invalid("REVISION_MISMATCH")
        if (current.status == GoalStatus.ARCHIVED) return GoalPlanResult.Invalid("GOAL_ARCHIVED")
        if (command.target.minorUnits <= 0L) return GoalPlanResult.Invalid("TARGET_MUST_BE_POSITIVE")
        if (command.target.currency != current.currency || currentReserved.currency != current.currency) {
            return GoalPlanResult.Invalid("CURRENCY_MISMATCH")
        }
        val normalizedName = command.name.trim().replace(WHITESPACE, " ")
        if (normalizedName.isBlank()) return GoalPlanResult.Invalid("NAME_REQUIRED")

        val updated = current.copy(
            name = normalizedName,
            target = command.target,
            targetDate = command.targetDate,
            linkedAccountId = command.linkedAccountId,
            iconKey = command.iconKey,
            colorArgb = command.colorArgb,
            revision = Math.addExact(current.revision, 1L),
            updatedAt = command.now,
        )
        val status = GoalProgressCalculator.statusFor(updated.status, updated.target, currentReserved)
        return GoalPlanResult.Valid(updated.copy(status = status))
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
