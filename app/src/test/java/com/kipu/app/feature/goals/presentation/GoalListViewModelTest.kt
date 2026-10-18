package com.kipu.app.feature.goals.presentation

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.usecase.UpdateGoalCommand
import com.kipu.app.feature.plans.data.entitlement.EffectiveEntitlementEvaluator
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.domain.model.OfflineEntitlementLeaseDecision
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GoalListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saving completed goal as verified premium passes entitlement without replacement`() = runTest(dispatcher) {
        val userId = UUID.fromString("a2a1bd4d-cb16-4ee5-9aa9-79e19fd51dce").toString()
        val goal = Goal(
            id = "completed-goal",
            userId = userId,
            name = "Emergency fund",
            target = Money(10_000, Currency.PEN),
            status = GoalStatus.COMPLETED,
            revision = 4,
            createdAt = Instant.parse("2026-10-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-10-10T00:00:00Z"),
        )
        val activeGoals = listOf("active-one", "active-two").map { id ->
            Goal(
                id = id,
                userId = userId,
                name = id,
                target = Money(10_000, Currency.PEN),
                status = GoalStatus.ACTIVE,
                revision = 2,
                createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-10-10T00:00:00Z"),
            )
        }
        val repository = mockk<GoalRepository>()
        val session = mockk<SessionCoordinator>()
        val cacheDao = mockk<FeatureAccessCacheDao>()
        val entitlementEvaluator = mockk<EffectiveEntitlementEvaluator>()
        val cachedPremium = FeatureAccessCacheEntity(
            userId = UUID.fromString(userId),
            policyVersion = 1,
            effectiveTier = "PREMIUM",
        )
        every { session.localAccess } returns MutableStateFlow(
            LocalAccess.Available(userId, RemoteSession.Absent),
        )
        every { repository.observeGoals(userId) } returns flowOf(listOf(goal) + activeGoals)
        every { repository.observeBackingAccounts(userId) } returns flowOf(emptyList())
        every { repository.observeEvents(userId, any()) } returns flowOf(emptyList())
        coEvery { cacheDao.get(UUID.fromString(userId)) } returns cachedPremium
        every { entitlementEvaluator.evaluate(userId, cachedPremium) } returns
            OfflineEntitlementLeaseDecision.Allowed(trustedNowMillis = 1_800_000_000_000)
        val commandResult = GoalCommandResult.Applied(goal.id, revision = 5)
        coEvery { repository.updateGoal(userId, goal.id, any()) } returns commandResult

        val viewModel = GoalListViewModel(repository, session, cacheDao, entitlementEvaluator)
        advanceUntilIdle()
        assertTrue("Premium entitlement must be verified into UI state", viewModel.state.value.hasPremiumAccess)
        assertEquals("Two ACTIVE goals put the account at the Free quota", 2, viewModel.state.value.activeGoalCount)

        viewModel.saveGoal(
            goalId = goal.id,
            name = goal.name,
            target = goal.target,
            targetDate = goal.targetDate,
            linkedAccountId = goal.linkedAccountId,
        )
        advanceUntilIdle()

        val command = slot<UpdateGoalCommand>()
        coVerify(exactly = 1) { repository.updateGoal(userId, goal.id, capture(command)) }
        assertTrue("Premium entitlement must reach the update command", command.captured.hasPremiumAccess)
        assertNull("Premium save at quota must not request a replacement", command.captured.replacementGoalId)
        assertNull(command.captured.replacementExpectedRevision)
        assertFalse(viewModel.state.value.isSaving)
        assertNull(viewModel.state.value.errorMessage)
    }
}
