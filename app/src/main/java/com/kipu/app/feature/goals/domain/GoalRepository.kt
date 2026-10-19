package com.kipu.app.feature.goals.domain

import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.usecase.GoalTransferIntent
import com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeGoals(userId: String): Flow<List<Goal>>
    fun observeGoal(userId: String, goalId: String): Flow<Goal?>
    fun observeEvents(userId: String, goalId: String): Flow<List<GoalEvent>>
    fun observeBackingAccounts(userId: String): Flow<List<GoalBackingAccount>>

    suspend fun createGoal(
        userId: String,
        name: String,
        target: Money,
        linkedAccountId: String?,
        targetDate: java.time.LocalDate?,
        hasPremiumAccess: Boolean,
    ): GoalCommandResult

    suspend fun updateGoal(userId: String, goalId: String, command: UpdateGoalCommand): GoalCommandResult
    suspend fun deleteEmptyGoal(userId: String, goalId: String, expectedRevision: Long): GoalCommandResult
    suspend fun reserveGoalFunds(
        userId: String,
        goalId: String,
        amount: Money,
        backingAccountId: String?,
        transfer: GoalTransferIntent? = null,
    ): GoalCommandResult

    suspend fun releaseGoalReserve(
        userId: String,
        goalId: String,
        backingAccountId: String,
        amount: Money,
        replacementGoalId: String? = null,
        replacementExpectedRevision: Long? = null,
        hasPremiumAccess: Boolean = false,
    ): GoalCommandResult

    suspend fun reassignGoalReserve(
        userId: String,
        sourceGoalId: String,
        destinationGoalId: String,
        backingAccountId: String,
        amount: Money,
    ): GoalCommandResult

    suspend fun archiveGoal(userId: String, goalId: String, expectedRevision: Long): GoalCommandResult
}
