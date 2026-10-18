package com.kipu.app.feature.goals.domain.usecase

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.GoalBackingCalculator
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import java.time.Instant

data class GoalTransferIntent(
    val sourceAccountId: String,
    val destinationAccountId: String,
    val currency: Currency,
)

data class ReserveGoalFundsCommand(
    val eventId: String,
    val operationId: String,
    val backingAccountId: String?,
    val amount: Money,
    val freeBacking: Money,
    val accountCurrency: Currency,
    val transfer: GoalTransferIntent? = null,
    val now: Instant = Instant.now(),
)

data class GoalReservePlan(
    val event: GoalEvent,
    val transfer: GoalTransferIntent?,
)

class ReserveGoalFunds {
    fun execute(goal: Goal, command: ReserveGoalFundsCommand): GoalPlanResult<GoalReservePlan> {
        if (goal.status != GoalStatus.ACTIVE) return GoalPlanResult.Invalid("GOAL_NOT_ACTIVE")
        val accountId = command.backingAccountId ?: goal.linkedAccountId
            ?: return GoalPlanResult.Invalid("BACKING_ACCOUNT_REQUIRED")
        if (accountId.isBlank()) return GoalPlanResult.Invalid("BACKING_ACCOUNT_REQUIRED")
        if (command.amount.currency != goal.currency) return GoalPlanResult.Invalid("CURRENCY_MISMATCH")
        when (
            GoalBackingCalculator.validateReservation(
                amount = command.amount,
                freeBacking = command.freeBacking,
                accountCurrency = command.accountCurrency,
            )
        ) {
            GoalBackingCalculator.ReservationValidation.Valid -> Unit
            GoalBackingCalculator.ReservationValidation.InvalidAmount -> return GoalPlanResult.Invalid("INVALID_AMOUNT")
            GoalBackingCalculator.ReservationValidation.CurrencyMismatch -> return GoalPlanResult.Invalid("CURRENCY_MISMATCH")
            GoalBackingCalculator.ReservationValidation.InsufficientBacking -> return GoalPlanResult.Invalid("INSUFFICIENT_BACKING")
        }
        if (command.accountCurrency != goal.currency) return GoalPlanResult.Invalid("CURRENCY_MISMATCH")

        val transfer = command.transfer?.let {
            if (it.destinationAccountId != accountId || it.sourceAccountId.isBlank() || it.destinationAccountId.isBlank()) {
                return GoalPlanResult.Invalid("INVALID_TRANSFER")
            }
            if (it.currency != goal.currency) return GoalPlanResult.Invalid("CURRENCY_MISMATCH")
            it.takeUnless { intent -> intent.sourceAccountId == intent.destinationAccountId }
        }
        return GoalPlanResult.Valid(
            GoalReservePlan(
                event = GoalEvent(
                    id = command.eventId,
                    userId = goal.userId,
                    goalId = goal.id,
                    type = GoalEventType.ALLOCATION,
                    delta = command.amount,
                    backingAccountId = accountId,
                    operationId = command.operationId,
                    occurredAt = command.now,
                ),
                transfer = transfer,
            ),
        )
    }
}
