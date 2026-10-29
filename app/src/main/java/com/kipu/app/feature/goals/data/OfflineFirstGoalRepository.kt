package com.kipu.app.feature.goals.data

import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.data.local.GoalLocalDataSource
import com.kipu.app.feature.goals.data.local.toDomain
import com.kipu.app.feature.goals.data.sync.GoalsSyncScheduler
import com.kipu.app.feature.goals.domain.GoalCommandHasher
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalCommandEnvelope
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.model.GoalCommandType
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.usecase.ArchiveGoal
import com.kipu.app.feature.goals.domain.usecase.CreateGoal
import com.kipu.app.feature.goals.domain.usecase.CreateGoalCommand
import com.kipu.app.feature.goals.domain.usecase.DeleteEmptyGoal
import com.kipu.app.feature.goals.domain.usecase.GoalPlanResult
import com.kipu.app.feature.goals.domain.usecase.GoalReassignmentCommand
import com.kipu.app.feature.goals.domain.usecase.ReassignGoalReserve
import com.kipu.app.feature.goals.domain.usecase.ReleaseGoalReserve
import com.kipu.app.feature.goals.domain.usecase.ReleaseGoalReserveCommand
import com.kipu.app.feature.goals.domain.usecase.ReserveGoalFunds
import com.kipu.app.feature.goals.domain.usecase.ReserveGoalFundsCommand
import com.kipu.app.feature.goals.domain.usecase.UpdateGoal
import com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Singleton
class OfflineFirstGoalRepository @Inject constructor(
    private val local: GoalLocalDataSource,
    private val syncScheduler: GoalsSyncScheduler,
    private val clock: Clock = Clock.systemUTC(),
) : GoalRepository {
    override fun observeGoals(userId: String): Flow<List<Goal>> =
        local.observeGoals(userId).map { goals -> goals.map { it.toDomain() } }

    override fun observeGoal(userId: String, goalId: String): Flow<Goal?> =
        local.observeGoal(userId, goalId).map { it?.toDomain() }

    override fun observeEvents(userId: String, goalId: String): Flow<List<GoalEvent>> =
        local.observeEvents(userId, goalId).map { events -> events.map { it.toDomain() } }

    override fun observeBackingAccounts(userId: String): Flow<List<GoalBackingAccount>> =
        local.observeBackingAccounts(userId).map { accounts -> accounts.map { it.toDomain() } }

    override suspend fun createGoal(
        userId: String,
        name: String,
        target: Money,
        linkedAccountId: String?,
        targetDate: java.time.LocalDate?,
        hasPremiumAccess: Boolean,
        iconKey: String?,
    ): GoalCommandResult {
        if (!hasPremiumAccess && local.countOperationalGoals(userId) >= FREE_ACTIVE_GOAL_LIMIT) {
            return GoalCommandResult.Rejected("GOAL_LIMIT")
        }
        val command = CreateGoalCommand(
            goalId = UUID.randomUUID().toString(),
            userId = userId,
            name = name,
            target = target,
            linkedAccountId = linkedAccountId,
            targetDate = targetDate,
            iconKey = iconKey,
            now = clock.instant(),
        )
        val goal = when (val result = CreateGoal().execute(command)) {
            is GoalPlanResult.Valid -> result.value
            is GoalPlanResult.Invalid -> return GoalCommandResult.Rejected(result.code)
        }
        val payload = buildJsonObject {
            put("name", goal.name)
            put("target_minor", goal.target.minorUnits)
            put("currency_code", goal.currency.name)
            goal.targetDate?.let { put("target_date", it.toString()) }
            goal.linkedAccountId?.let { put("linked_account_id", it) }
            goal.iconKey?.let { put("icon_key", it) }
            goal.colorArgb?.let { put("color_argb", it) }
        }
        return stage(
            userId = userId,
            goal = goal,
            expectedRevisions = mapOf(goal.id to null),
            type = GoalCommandType.CREATE_GOAL,
            expectedRevision = null,
            payload = payload,
        )
    }

    override suspend fun updateGoal(
        userId: String,
        goalId: String,
        command: UpdateGoalCommand,
    ): GoalCommandResult {
        val currentRow = local.getGoal(userId, goalId)
            ?: return GoalCommandResult.Rejected("GOAL_NOT_FOUND")
        if (currentRow.syncState == DELETE_PENDING) return GoalCommandResult.Rejected("GOAL_DELETE_PENDING")
        val current = currentRow.toDomain()
        val events = local.getEvents(userId, goalId).map { it.toDomain() }
        val currentReserved = GoalProgressCalculator.calculate(current, events).reserved
        val updated = when (val result = UpdateGoal().execute(current, command, currentReserved)) {
            is GoalPlanResult.Valid -> result.value
            is GoalPlanResult.Invalid -> return GoalCommandResult.Rejected(result.code)
        }
        val requiresReplacement = current.status == GoalStatus.COMPLETED &&
            updated.status == GoalStatus.ACTIVE && !command.hasPremiumAccess &&
            local.countOperationalGoals(userId) >= FREE_ACTIVE_GOAL_LIMIT
        var replacementGoal: Goal? = null
        if (requiresReplacement) {
            val replacementId = command.replacementGoalId
                ?: return GoalCommandResult.Rejected("REPLACEMENT_REQUIRED")
            val replacementRow = local.getGoal(userId, replacementId)
                ?: return GoalCommandResult.Rejected("REPLACEMENT_NOT_ACTIVE")
            val selected = replacementRow.toDomain()
            if (selected.id == current.id) return GoalCommandResult.Rejected("INVALID_REPLACEMENT_GOAL")
            if (selected.status != GoalStatus.ACTIVE) return GoalCommandResult.Rejected("REPLACEMENT_NOT_ACTIVE")
            if (selected.revision != command.replacementExpectedRevision) {
                return GoalCommandResult.Conflict("REVISION_MISMATCH", selected.revision)
            }
            replacementGoal = selected.copy(
                status = GoalStatus.LOCKED_BY_PLAN,
                revision = Math.addExact(selected.revision, 1L),
                updatedAt = command.now,
            )
        }
        val payload = buildJsonObject {
            put("name", updated.name)
            put("target_minor", updated.target.minorUnits)
            put("currency_code", updated.currency.name)
            put("target_date", updated.targetDate?.toString())
            put("linked_account_id", updated.linkedAccountId)
            put("icon_key", updated.iconKey)
            put("color_argb", updated.colorArgb)
            if (requiresReplacement) {
                put("replacement_goal_id", replacementGoal!!.id)
                put("replacement_expected_revision", command.replacementExpectedRevision!!)
            }
        }
        return stage(
            userId,
            updated,
            buildMap {
                put(goalId, current.revision)
                replacementGoal?.let { put(it.id, command.replacementExpectedRevision) }
            },
            GoalCommandType.UPDATE_GOAL,
            command.expectedRevision,
            payload,
            additionalGoals = listOfNotNull(replacementGoal),
        )
    }

    override suspend fun deleteEmptyGoal(
        userId: String,
        goalId: String,
        expectedRevision: Long,
    ): GoalCommandResult {
        val currentRow = local.getGoal(userId, goalId)
            ?: return GoalCommandResult.Rejected("GOAL_NOT_FOUND")
        if (currentRow.syncState == DELETE_PENDING) return GoalCommandResult.Rejected("GOAL_DELETE_PENDING")
        val current = currentRow.toDomain()
        val eventCount = local.countEvents(userId, goalId)
        val transferCount = local.countLinkedTransfers(userId, goalId)
        val pendingCount = local.pendingCommandCount(userId, goalId)
        when (val validation = DeleteEmptyGoal().execute(current, eventCount, transferCount, pendingCount)) {
            is GoalPlanResult.Invalid -> return GoalCommandResult.Rejected(validation.code)
            is GoalPlanResult.Valid -> Unit
        }
        if (current.revision != expectedRevision) return GoalCommandResult.Conflict("REVISION_MISMATCH", current.revision)
        return stage(
            userId,
            current,
            mapOf(goalId to current.revision),
            GoalCommandType.DELETE_EMPTY_GOAL,
            expectedRevision,
            buildJsonObject { put("goal_id", goalId) },
        )
    }

    override suspend fun reserveGoalFunds(
        userId: String,
        goalId: String,
        amount: Money,
        backingAccountId: String?,
        transfer: com.kipu.app.feature.goals.domain.usecase.GoalTransferIntent?,
    ): GoalCommandResult {
        val goalRow = local.getGoal(userId, goalId)
            ?: return GoalCommandResult.Rejected("GOAL_NOT_FOUND")
        if (goalRow.syncState == DELETE_PENDING) return GoalCommandResult.Rejected("GOAL_DELETE_PENDING")
        val goal = goalRow.toDomain()
        val resolvedAccountId = backingAccountId ?: goal.linkedAccountId
            ?: return GoalCommandResult.Rejected("BACKING_ACCOUNT_REQUIRED")
        val account = local.getBackingAccountPosition(userId, resolvedAccountId)
            ?: return GoalCommandResult.Rejected("OWNER_OR_ACCOUNT_INVALID")
        val currency = runCatching { Currency.fromCode(account.currencyCode) }.getOrNull()
            ?: return GoalCommandResult.Rejected("UNSUPPORTED_CURRENCY")
        val transferDecision = GoalMovementIntegration.prepareTransfer(transfer, resolvedAccountId, currency)
        val normalizedTransfer = when (transferDecision) {
            GoalMovementIntegration.TransferDecision.ReservationOnly -> null
            is GoalMovementIntegration.TransferDecision.InternalTransfer -> transferDecision.intent
            is GoalMovementIntegration.TransferDecision.Rejected -> return GoalCommandResult.Rejected(transferDecision.code)
        }
        if (normalizedTransfer != null) {
            val source = local.getBackingAccountPosition(userId, normalizedTransfer.sourceAccountId)
                ?: return GoalCommandResult.Rejected("OWNER_OR_ACCOUNT_INVALID")
            if (source.currencyCode != currency.name) return GoalCommandResult.Rejected("CURRENCY_MISMATCH")
            if (source.availableMinor < amount.minorUnits) {
                return GoalCommandResult.Rejected("INSUFFICIENT_TRANSFER_SOURCE")
            }
        }
        val freeBacking = try {
            Money(Math.addExact(account.availableMinor, if (normalizedTransfer == null) 0L else amount.minorUnits), currency)
        } catch (_: ArithmeticException) {
            return GoalCommandResult.Rejected("AMOUNT_OUT_OF_RANGE")
        } catch (_: IllegalArgumentException) {
            return GoalCommandResult.Rejected("AMOUNT_OUT_OF_RANGE")
        }
        val operationId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val plan = when (
            val result = ReserveGoalFunds().execute(
                goal,
                ReserveGoalFundsCommand(
                    eventId = eventId,
                    operationId = operationId,
                    backingAccountId = resolvedAccountId,
                    amount = amount,
                    freeBacking = freeBacking,
                    accountCurrency = currency,
                    transfer = normalizedTransfer,
                    now = clock.instant(),
                ),
            )
        ) {
            is GoalPlanResult.Valid -> result.value
            is GoalPlanResult.Invalid -> return GoalCommandResult.Rejected(result.code)
        }
        val oldEvents = local.getEvents(userId, goalId).map { it.toDomain() }
        val newStatus = GoalProgressCalculator.calculate(goal, oldEvents + plan.event).status
        val updatedGoal = goal.copy(
            status = newStatus,
            revision = Math.addExact(goal.revision, 1L),
            updatedAt = clock.instant(),
        )
        val payload = buildJsonObject {
            put("event_id", eventId)
            put("goal_id", goalId)
            put("amount_minor", amount.minorUnits)
            put("currency_code", amount.currency.name)
            put("backing_account_id", plan.event.backingAccountId)
            normalizedTransfer?.let { intent ->
                putJsonObject("transfer") {
                    put("source_account_id", intent.sourceAccountId)
                    put("destination_account_id", intent.destinationAccountId)
                    put("currency_code", intent.currency.name)
                }
            }
        }
        return stage(
            userId,
            updatedGoal,
            mapOf(goalId to goal.revision),
            GoalCommandType.RESERVE_GOAL,
            goal.revision,
            payload,
            listOf(plan.event),
            operationId,
        )
    }

    override suspend fun releaseGoalReserve(
        userId: String,
        goalId: String,
        backingAccountId: String,
        amount: Money,
        replacementGoalId: String?,
        replacementExpectedRevision: Long?,
        hasPremiumAccess: Boolean,
    ): GoalCommandResult {
        val goalRow = local.getGoal(userId, goalId)
            ?: return GoalCommandResult.Rejected("GOAL_NOT_FOUND")
        if (goalRow.syncState == DELETE_PENDING) return GoalCommandResult.Rejected("GOAL_DELETE_PENDING")
        val goal = goalRow.toDomain()
        val account = local.getBackingAccountPosition(userId, backingAccountId)
            ?: return GoalCommandResult.Rejected("OWNER_OR_ACCOUNT_INVALID")
        val currentEvents = local.getEvents(userId, goalId).map { it.toDomain() }
        val accountReserved = currentEvents.asSequence()
            .filter { it.backingAccountId == backingAccountId && !it.transactionVoided }
            .fold(0L) { total, event -> Math.addExact(total, event.delta.minorUnits) }
        val goalReserved = GoalProgressCalculator.calculate(goal, currentEvents).reserved
        val operationId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val event = when (
            val result = ReleaseGoalReserve().execute(
                goal,
                ReleaseGoalReserveCommand(
                    eventId = eventId,
                    operationId = operationId,
                    backingAccountId = backingAccountId,
                    amount = amount,
                    accountReserved = Money(accountReserved, goal.currency),
                    goalReserved = goalReserved,
                    now = clock.instant(),
                ),
            )
        ) {
            is GoalPlanResult.Valid -> result.value
            is GoalPlanResult.Invalid -> return GoalCommandResult.Rejected(result.code)
        }
        val updatedGoal = goal.copy(
            status = GoalProgressCalculator.calculate(goal, currentEvents + event).status,
            revision = Math.addExact(goal.revision, 1L),
            updatedAt = clock.instant(),
        )
        val requiresReplacement = goal.status == GoalStatus.COMPLETED &&
            updatedGoal.status == GoalStatus.ACTIVE && !hasPremiumAccess &&
            local.countOperationalGoals(userId) >= FREE_ACTIVE_GOAL_LIMIT
        var replacementGoal: Goal? = null
        if (requiresReplacement) {
            val replacementId = replacementGoalId
                ?: return GoalCommandResult.Rejected("REPLACEMENT_REQUIRED")
            val replacementRow = local.getGoal(userId, replacementId)
                ?: return GoalCommandResult.Rejected("REPLACEMENT_NOT_ACTIVE")
            val selected = replacementRow.toDomain()
            if (selected.id == goal.id) return GoalCommandResult.Rejected("INVALID_REPLACEMENT_GOAL")
            if (selected.status != GoalStatus.ACTIVE) return GoalCommandResult.Rejected("REPLACEMENT_NOT_ACTIVE")
            if (selected.revision != replacementExpectedRevision) {
                return GoalCommandResult.Conflict("REVISION_MISMATCH", selected.revision)
            }
            replacementGoal = selected.copy(
                status = GoalStatus.LOCKED_BY_PLAN,
                revision = Math.addExact(selected.revision, 1L),
                updatedAt = clock.instant(),
            )
        } else if (replacementGoalId != null || replacementExpectedRevision != null) {
            return GoalCommandResult.Rejected("UNEXPECTED_REPLACEMENT_GOAL")
        }
        val payload = buildJsonObject {
            put("event_id", eventId)
            put("goal_id", goalId)
            put("backing_account_id", backingAccountId)
            put("amount_minor", amount.minorUnits)
            put("currency_code", amount.currency.name)
            if (requiresReplacement) {
                put("replacement_goal_id", replacementGoal!!.id)
                put("replacement_expected_revision", replacementExpectedRevision!!)
            }
        }
        return stage(
            userId,
            updatedGoal,
            buildMap {
                put(goalId, goal.revision)
                replacementGoal?.let { put(it.id, replacementExpectedRevision) }
            },
            GoalCommandType.RELEASE_GOAL,
            goal.revision,
            payload,
            listOf(event),
            operationId,
            additionalGoals = listOfNotNull(replacementGoal),
        )
    }

    override suspend fun reassignGoalReserve(
        userId: String,
        sourceGoalId: String,
        destinationGoalId: String,
        backingAccountId: String,
        amount: Money,
    ): GoalCommandResult {
        val sourceRow = local.getGoal(userId, sourceGoalId)
            ?: return GoalCommandResult.Rejected("SOURCE_GOAL_NOT_FOUND")
        val destinationRow = local.getGoal(userId, destinationGoalId)
            ?: return GoalCommandResult.Rejected("DESTINATION_GOAL_NOT_FOUND")
        if (sourceRow.syncState == DELETE_PENDING || destinationRow.syncState == DELETE_PENDING) {
            return GoalCommandResult.Rejected("GOAL_DELETE_PENDING")
        }
        val source = sourceRow.toDomain()
        val destination = destinationRow.toDomain()
        val sourceEvents = local.getEvents(userId, sourceGoalId).map { it.toDomain() }
        val destinationEvents = local.getEvents(userId, destinationGoalId).map { it.toDomain() }
        val sourceReservedForAccount = sourceEvents.asSequence()
            .filter { it.backingAccountId == backingAccountId && !it.transactionVoided }
            .fold(0L) { total, event -> Math.addExact(total, event.delta.minorUnits) }
        val operationId = UUID.randomUUID().toString()
        val plan = when (
            val result = ReassignGoalReserve().execute(
                source,
                destination,
                GoalReassignmentCommand(
                    operationId = operationId,
                    sourceEventId = UUID.randomUUID().toString(),
                    destinationEventId = UUID.randomUUID().toString(),
                    sourceExpectedRevision = source.revision,
                    destinationExpectedRevision = destination.revision,
                    sourceBackingAccountId = backingAccountId,
                    destinationBackingAccountId = backingAccountId,
                    amount = amount,
                    sourceReservedForAccount = Money(sourceReservedForAccount, source.currency),
                    now = clock.instant(),
                ),
            )
        ) {
            is GoalPlanResult.Valid -> result.value
            is GoalPlanResult.Invalid -> return GoalCommandResult.Rejected(result.code)
        }
        val updatedSource = source.copy(
            status = GoalProgressCalculator.calculate(source, sourceEvents + plan.sourceEvent).status,
            revision = Math.addExact(source.revision, 1L),
            updatedAt = clock.instant(),
        )
        val updatedDestination = destination.copy(
            status = GoalProgressCalculator.calculate(destination, destinationEvents + plan.destinationEvent).status,
            revision = Math.addExact(destination.revision, 1L),
            updatedAt = clock.instant(),
        )
        val payload = buildJsonObject {
            put("source_goal_id", sourceGoalId)
            put("destination_goal_id", destinationGoalId)
            put("source_expected_revision", source.revision)
            put("destination_expected_revision", destination.revision)
            put("backing_account_id", backingAccountId)
            put("amount_minor", amount.minorUnits)
            put("currency_code", amount.currency.name)
            put("source_event_id", plan.sourceEvent.id)
            put("destination_event_id", plan.destinationEvent.id)
        }
        return stage(
            userId,
            updatedSource,
            mapOf(sourceGoalId to source.revision, destinationGoalId to destination.revision),
            GoalCommandType.REASSIGN_GOAL_RESERVE,
            source.revision,
            payload,
            listOf(plan.sourceEvent, plan.destinationEvent),
            operationId,
            additionalGoals = listOf(updatedDestination),
        )
    }

    override suspend fun archiveGoal(
        userId: String,
        goalId: String,
        expectedRevision: Long,
    ): GoalCommandResult {
        val goalRow = local.getGoal(userId, goalId)
            ?: return GoalCommandResult.Rejected("GOAL_NOT_FOUND")
        if (goalRow.syncState == DELETE_PENDING) return GoalCommandResult.Rejected("GOAL_DELETE_PENDING")
        val goal = goalRow.toDomain()
        val events = local.getEvents(userId, goalId).map { it.toDomain() }
        val reserved = GoalProgressCalculator.calculate(goal, events).reserved
        val archived = when (val result = ArchiveGoal().execute(goal, expectedRevision, reserved, clock.instant())) {
            is GoalPlanResult.Valid -> result.value
            is GoalPlanResult.Invalid -> return GoalCommandResult.Rejected(result.code)
        }
        return stage(
            userId,
            archived,
            mapOf(goalId to goal.revision),
            GoalCommandType.ARCHIVE_GOAL,
            expectedRevision,
            buildJsonObject { put("goal_id", goalId) },
        )
    }

    private suspend fun stage(
        userId: String,
        goal: Goal,
        expectedRevisions: Map<String, Long?>,
        type: GoalCommandType,
        expectedRevision: Long?,
        payload: JsonObject,
        events: List<GoalEvent> = emptyList(),
        operationId: String = UUID.randomUUID().toString(),
        additionalGoals: List<Goal> = emptyList(),
    ): GoalCommandResult {
        val goals = listOf(goal) + additionalGoals
        val hashInput = buildJsonObject {
            put("contract_version", com.kipu.app.feature.goals.domain.model.GoalCommandIdentity.CURRENT_CONTRACT_VERSION)
            put("command_type", type.name)
            put("goal_id", goal.id)
            expectedRevision?.let { put("expected_revision", it) }
            putJsonObject("payload") { payload.forEach { (key, value) -> put(key, value) } }
        }
        val envelope = GoalCommandEnvelope(
            identity = GoalCommandHasher.identity(operationId, hashInput),
            type = type,
            goalId = goal.id,
            expectedRevision = expectedRevision,
            payload = payload,
        )
        return local.stageCommand(userId, envelope, goals, expectedRevisions, events).also { result ->
            if (result is GoalCommandResult.Applied || result is GoalCommandResult.Duplicate) {
                syncScheduler.schedule(userId)
            }
        }
    }

    private companion object {
        const val DELETE_PENDING = "DELETE_PENDING"
        const val FREE_ACTIVE_GOAL_LIMIT = FreePlanLimits.DEFAULT_GOALS
    }
}
