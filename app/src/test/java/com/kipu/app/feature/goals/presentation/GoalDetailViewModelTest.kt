package com.kipu.app.feature.goals.presentation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.session.*
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.goals.data.GoalDisplayPreferences
import com.kipu.app.feature.goals.data.GoalDisplayState
import com.kipu.app.feature.goals.domain.model.*
import com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GoalDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<GoalRepository>()
    private val session = mockk<SessionCoordinator>()
    private val cache = mockk<FeatureAccessCacheDao>()
    private val evaluator = mockk<EffectiveEntitlementEvaluator>()
    private val owner = "a2a1bd4d-cb16-4ee5-9aa9-79e19fd51dce"
    private val goal = Goal("goal", owner, "Laptop", Money(500_000, Currency.PEN),
        linkedAccountId = "account", iconKey = "laptop", colorArgb = 123, revision = 4,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun create(reserved: Long, preferences: GoalDisplayPreferences? = null): GoalDetailViewModel {
        every { session.localAccess } returns MutableStateFlow(LocalAccess.Available(owner, RemoteSession.Absent))
        every { repository.observeGoal(owner, goal.id) } returns flowOf(goal)
        every { repository.observeGoals(owner) } returns flowOf(listOf(goal))
        every { repository.observeBackingAccounts(owner) } returns flowOf(emptyList())
        every { repository.observeEvents(owner, goal.id) } returns flowOf(if (reserved == 0L) emptyList() else listOf(
            GoalEvent("event", owner, goal.id, GoalEventType.ALLOCATION, Money(reserved, Currency.PEN), "account", "operation", occurredAt = Instant.EPOCH)))
        coEvery { cache.get(UUID.fromString(owner)) } returns null
        return GoalDetailViewModel(SavedStateHandle(mapOf("goalId" to goal.id)), repository, session, cache, evaluator, preferences)
    }

    @Test fun `marking achieved adjusts only target and preserves metadata`() = runTest(dispatcher) {
        val command = slot<UpdateGoalCommand>()
        coEvery { repository.updateGoal(owner, goal.id, capture(command)) } returns GoalCommandResult.Applied(goal.id, 5)
        val model = create(125_000)
        advanceUntilIdle()
        model.complete()
        advanceUntilIdle()
        assertEquals(Money(125_000, Currency.PEN), command.captured.target)
        assertEquals(goal.revision, command.captured.expectedRevision)
        assertEquals(goal.name, command.captured.name)
        assertEquals(goal.linkedAccountId, command.captured.linkedAccountId)
        assertEquals(goal.iconKey, command.captured.iconKey)
        assertEquals(goal.colorArgb, command.captured.colorArgb)
        coVerify(exactly = 1) { repository.updateGoal(owner, goal.id, any()) }
        assertNull(model.state.value.errorMessage)
    }

    @Test fun `zero reserves cannot change the target`() = runTest(dispatcher) {
        val model = create(0)
        advanceUntilIdle()
        model.complete()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.updateGoal(any(), any(), any()) }
        assertNotNull(model.state.value.errorMessage)
    }

    @Test fun `deletion rejected for history keeps the detail open`() = runTest(dispatcher) {
        coEvery { repository.deleteEmptyGoal(owner, goal.id, goal.revision) } returns GoalCommandResult.Rejected("HISTORY_PRESERVED")
        val model = create(0)
        advanceUntilIdle()
        model.delete()
        advanceUntilIdle()
        assertFalse(model.state.value.isDeleted)
        assertTrue(model.state.value.errorMessage!!.contains("Mejor archivarla"))
    }

    @Test fun `confirmed removal releases reserve and archives before hiding without physical deletion`() = runTest(dispatcher) {
        val preferences = mockk<GoalDisplayPreferences>()
        every { preferences.snapshot(owner) } returns GoalDisplayState()
        coEvery { preferences.remove(owner, goal.id) } returns Unit
        coEvery { repository.releaseGoalReserve(owner, goal.id, "account", Money(125_000, Currency.PEN), null, null, false) } returns GoalCommandResult.Applied(goal.id, 5)
        coEvery { repository.archiveGoal(owner, goal.id, any()) } returns GoalCommandResult.Applied(goal.id, 6)
        val model = create(125_000, preferences)
        advanceUntilIdle()
        model.delete()
        advanceUntilIdle()
        coVerifyOrder {
            repository.releaseGoalReserve(owner, goal.id, "account", Money(125_000, Currency.PEN), null, null, false)
            repository.archiveGoal(owner, goal.id, any())
            preferences.remove(owner, goal.id)
        }
        coVerify(exactly = 0) { repository.deleteEmptyGoal(any(), any(), any()) }
        assertTrue(model.state.value.isDeleted)
    }

    @Test fun `failed release keeps goal visible and does not archive or hide`() = runTest(dispatcher) {
        val preferences = mockk<GoalDisplayPreferences>()
        every { preferences.snapshot(owner) } returns GoalDisplayState()
        coEvery { repository.releaseGoalReserve(owner, goal.id, "account", Money(125_000, Currency.PEN), null, null, false) } returns GoalCommandResult.Rejected("RESERVE_EXCEEDED")
        val model = create(125_000, preferences)
        advanceUntilIdle()
        model.delete()
        advanceUntilIdle()
        coVerify(exactly = 0) { preferences.remove(any(), any()) }
        coVerify(exactly = 0) { repository.archiveGoal(any(), any(), any()) }
        assertFalse(model.state.value.isDeleted)
        assertNotNull(model.state.value.errorMessage)
    }
}
