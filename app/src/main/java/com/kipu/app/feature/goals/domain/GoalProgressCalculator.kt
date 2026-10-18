package com.kipu.app.feature.goals.domain

import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.math.BigDecimal
import java.math.RoundingMode

data class GoalProgress(
    val goalId: String,
    val reserved: Money,
    val target: Money,
    val progressPercent: BigDecimal,
    val status: GoalStatus,
) {
    val isOverfunded: Boolean get() = reserved > target
}

object GoalProgressCalculator {
    fun calculate(goal: Goal, events: Iterable<GoalEvent>): GoalProgress {
        val reservedMinor = events.asSequence()
            .filter { it.goalId == goal.id && !it.transactionVoided }
            .fold(0L) { total, event ->
                require(event.delta.currency == goal.currency) { "Goal event currency mismatch" }
                Math.addExact(total, event.delta.minorUnits)
            }
        val reserved = Money(reservedMinor, goal.currency)
        val percentage = BigDecimal.valueOf(reservedMinor)
            .multiply(HUNDRED)
            .divide(BigDecimal.valueOf(goal.target.minorUnits), 2, RoundingMode.HALF_UP)
        return GoalProgress(
            goalId = goal.id,
            reserved = reserved,
            target = goal.target,
            progressPercent = percentage,
            status = statusFor(goal.status, goal.target, reserved),
        )
    }

    fun statusFor(currentStatus: GoalStatus, target: Money, reserved: Money): GoalStatus {
        require(target.currency == reserved.currency) { "Goal amount currency mismatch" }
        if (currentStatus == GoalStatus.ARCHIVED) return GoalStatus.ARCHIVED
        if (currentStatus == GoalStatus.LOCKED_BY_PLAN) return GoalStatus.LOCKED_BY_PLAN
        return if (reserved >= target) GoalStatus.COMPLETED else GoalStatus.ACTIVE
    }

    private val HUNDRED = BigDecimal.valueOf(100)
}
