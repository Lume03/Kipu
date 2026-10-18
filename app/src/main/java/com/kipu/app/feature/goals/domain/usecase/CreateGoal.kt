package com.kipu.app.feature.goals.domain.usecase

import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant
import java.time.LocalDate

data class CreateGoalCommand(
    val goalId: String,
    val userId: String,
    val name: String,
    val target: Money,
    val targetDate: LocalDate? = null,
    val linkedAccountId: String? = null,
    val iconKey: String? = null,
    val colorArgb: Int? = null,
    val now: Instant = Instant.now(),
)

sealed interface GoalPlanResult<out T> {
    data class Valid<T>(val value: T) : GoalPlanResult<T>
    data class Invalid(val code: String) : GoalPlanResult<Nothing>
}

class CreateGoal {
    fun execute(command: CreateGoalCommand): GoalPlanResult<Goal> {
        if (command.goalId.isBlank() || command.userId.isBlank()) return GoalPlanResult.Invalid("INVALID_OWNER_OR_ID")
        val name = command.name.trim().replace(WHITESPACE, " ")
        if (name.isBlank()) return GoalPlanResult.Invalid("NAME_REQUIRED")
        if (command.target.minorUnits <= 0L) return GoalPlanResult.Invalid("TARGET_MUST_BE_POSITIVE")
        return GoalPlanResult.Valid(
            Goal(
                id = command.goalId,
                userId = command.userId,
                name = name,
                target = command.target,
                targetDate = command.targetDate,
                linkedAccountId = command.linkedAccountId,
                iconKey = command.iconKey,
                colorArgb = command.colorArgb,
                status = GoalStatus.ACTIVE,
                revision = 0L,
                createdAt = command.now,
                updatedAt = command.now,
            ),
        )
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
