package com.kipu.app.feature.goals.data

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.data.local.GoalEntity
import com.kipu.app.feature.goals.data.local.GoalEventEntity
import com.kipu.app.feature.goals.data.local.GoalEventWithTransactionStatusRow
import com.kipu.app.feature.goals.data.local.GoalLocalDataSource
import com.kipu.app.feature.goals.data.sync.GoalsSyncScheduler
import com.kipu.app.feature.goals.domain.model.GoalCommandEnvelope
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineFirstGoalRepositoryTest {
    private val local = mockk<GoalLocalDataSource>()
    private val scheduler = mockk<GoalsSyncScheduler>(relaxed = true)
    private val repository = OfflineFirstGoalRepository(
        local = local,
        syncScheduler = scheduler,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private val completed = goalRow("completed", GoalStatus.COMPLETED, revision = 4L)
    private val activeOne = goalRow("active-1", GoalStatus.ACTIVE, revision = 7L)
    private val activeTwo = goalRow("active-2", GoalStatus.ACTIVE, revision = 9L)

    @Before
    fun setUp() {
        coEvery { local.getGoal(USER_ID, "completed") } returns completed
        coEvery { local.getGoal(USER_ID, "active-1") } returns activeOne
        coEvery { local.getGoal(USER_ID, "active-2") } returns activeTwo
        coEvery { local.getEvents(USER_ID, any()) } answers {
            if (firstArg<String>() == USER_ID && secondArg<String>() == "completed") {
                listOf(
                    GoalEventWithTransactionStatusRow(
                        event = GoalEventEntity(
                            id = "event-completed",
                            userId = USER_ID,
                            goalId = "completed",
                            eventType = "ALLOCATION",
                            deltaMinor = 100L,
                            currencyCode = Currency.PEN.name,
                            operationId = "operation-event",
                            occurredAt = NOW.toEpochMilli(),
                            createdAt = NOW.toEpochMilli(),
                        ),
                        linkedTransactionStatus = null,
                    ),
                )
            } else {
                emptyList()
            }
        }
        coEvery {
            local.stageCommand(USER_ID, any(), any(), any(), any())
        } coAnswers {
            val command = secondArg<GoalCommandEnvelope>()
            val stagedGoals = thirdArg<List<com.kipu.app.feature.goals.domain.model.Goal>>()
            GoalCommandResult.Applied(command.goalId, stagedGoals.first { it.id == command.goalId }.revision)
        }
    }

    @Test
    fun premiumCanReactivateCompletedGoalAtFreeActiveGoalLimitWithoutReplacement() = runTest {
        coEvery { local.countOperationalGoals(USER_ID) } returns 2

        val result = repository.updateGoal(
            USER_ID,
            "completed",
            updateCommand(hasPremiumAccess = true),
        )

        assertTrue("Unexpected result: $result", result is GoalCommandResult.Applied)
        val stagedGoals = slot<List<com.kipu.app.feature.goals.domain.model.Goal>>()
        val command = slot<GoalCommandEnvelope>()
        coVerify(exactly = 1) {
            local.stageCommand(USER_ID, capture(command), capture(stagedGoals), any(), any())
        }
        assertEquals(listOf("completed"), stagedGoals.captured.map { it.id })
        assertEquals(GoalStatus.ACTIVE, stagedGoals.captured.single().status)
        assertFalse(command.captured.payload.containsKey("replacement_goal_id"))
        coVerify(exactly = 0) { local.countOperationalGoals(USER_ID) }
    }

    @Test
    fun freeUserAtLimitWithoutReplacementIsRejectedBeforeAnyMutation() = runTest {
        coEvery { local.countOperationalGoals(USER_ID) } returns 2

        val result = repository.updateGoal(
            USER_ID,
            "completed",
            updateCommand(hasPremiumAccess = false),
        )

        assertEquals("REPLACEMENT_REQUIRED", (result as GoalCommandResult.Rejected).code)
        coVerify(exactly = 0) { local.stageCommand(USER_ID, any(), any(), any(), any()) }
    }

    @Test
    fun freeUserAtLimitStagesReplacementAndReactivationTogether() = runTest {
        coEvery { local.countOperationalGoals(USER_ID) } returns 2

        val result = repository.updateGoal(
            USER_ID,
            "completed",
            updateCommand(
                hasPremiumAccess = false,
                replacementGoalId = "active-1",
                replacementExpectedRevision = 7L,
            ),
        )

        assertTrue(result is GoalCommandResult.Applied)
        val stagedGoals = slot<List<com.kipu.app.feature.goals.domain.model.Goal>>()
        val expectedRevisions = slot<Map<String, Long?>>()
        val command = slot<GoalCommandEnvelope>()
        coVerify(exactly = 1) {
            local.stageCommand(
                USER_ID,
                capture(command),
                capture(stagedGoals),
                capture(expectedRevisions),
                any(),
            )
        }
        assertEquals(setOf("completed", "active-1"), stagedGoals.captured.map { it.id }.toSet())
        assertEquals(GoalStatus.ACTIVE, stagedGoals.captured.single { it.id == "completed" }.status)
        assertEquals(GoalStatus.LOCKED_BY_PLAN, stagedGoals.captured.single { it.id == "active-1" }.status)
        assertEquals(mapOf("completed" to 4L, "active-1" to 7L), expectedRevisions.captured)
        assertEquals("active-1", command.captured.payload["replacement_goal_id"]?.jsonPrimitive?.content)
        assertEquals("7", command.captured.payload["replacement_expected_revision"]?.jsonPrimitive?.content)
        assertEquals(9, command.captured.payload.jsonObject.size)
    }

    @Test
    fun freeEditThatRemainsCompletedDoesNotRequireOrStageReplacement() = runTest {
        coEvery { local.countOperationalGoals(USER_ID) } returns 2

        val result = repository.updateGoal(
            USER_ID,
            "completed",
            updateCommand(
                hasPremiumAccess = false,
                targetMinor = 100L,
            ),
        )

        assertTrue(result is GoalCommandResult.Applied)
        val stagedGoals = slot<List<com.kipu.app.feature.goals.domain.model.Goal>>()
        val command = slot<GoalCommandEnvelope>()
        coVerify(exactly = 1) {
            local.stageCommand(USER_ID, capture(command), capture(stagedGoals), any(), any())
        }
        assertEquals(listOf("completed"), stagedGoals.captured.map { it.id })
        assertEquals(GoalStatus.COMPLETED, stagedGoals.captured.single().status)
        assertFalse(command.captured.payload.containsKey("replacement_goal_id"))
        assertEquals(4L, command.captured.expectedRevision)
    }

    private fun updateCommand(
        hasPremiumAccess: Boolean,
        targetMinor: Long = 200L,
        replacementGoalId: String? = null,
        replacementExpectedRevision: Long? = null,
    ) = UpdateGoalCommand(
        expectedRevision = completed.revision,
        name = completed.name,
        target = Money(targetMinor, Currency.PEN),
        targetDate = null,
        linkedAccountId = null,
        replacementGoalId = replacementGoalId,
        replacementExpectedRevision = replacementExpectedRevision,
        hasPremiumAccess = hasPremiumAccess,
        now = NOW,
    )

    private fun goalRow(id: String, status: GoalStatus, revision: Long) = GoalEntity(
        id = id,
        userId = USER_ID,
        name = id,
        targetMinor = 100L,
        currencyCode = Currency.PEN.name,
        status = status.name,
        revision = revision,
        syncState = "SYNCED",
        createdAt = NOW.toEpochMilli(),
        updatedAt = NOW.toEpochMilli(),
    )

    private companion object {
        const val USER_ID = "user-1"
        val NOW: Instant = Instant.parse("2026-10-18T12:00:00Z")
    }
}
