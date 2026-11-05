package com.kipu.app.feature.notifications.domain

import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.budgets.data.local.BudgetDao
import com.kipu.app.feature.budgets.data.local.BudgetEntity
import com.kipu.app.feature.notifications.data.RegisteredNotificationDestinationResolver
import java.time.Instant
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDestinationResolverTest {
    @Test
    fun `debt and goal resolve only through owner scoped lookups and perform no writes`() = runTest {
        val debts = mockk<com.kipu.app.feature.debts.data.local.DebtDao>()
        val goals = mockk<com.kipu.app.feature.goals.data.local.GoalDao>()
        coEvery { debts.getDebt(USER_ID, "debt") } returns mockk()
        coEvery { goals.getGoal(USER_ID, "goal") } returns mockk()
        coEvery { debts.getDebt(USER_ID, "foreign-or-missing") } returns null
        coEvery { goals.getGoal(USER_ID, "foreign-or-missing") } returns null
        val resolver = RegisteredNotificationDestinationResolver(mockk(), mockk(), mockk(), debts, goals)

        assertEquals(NotificationDestination.DebtDetail("debt"), resolver.resolve(USER_ID, notification("DEBT", "debt")))
        assertEquals(NotificationDestination.GoalDetail("goal"), resolver.resolve(USER_ID, notification("GOAL", "goal")))
        listOf("DEBT", "GOAL").forEach { type ->
            assertEquals(NotificationDestination.Unavailable(), resolver.resolve(USER_ID, notification(type, "foreign-or-missing")))
            assertEquals(NotificationDestination.Unavailable(), resolver.resolve("other-owner", notification(type, "goal")))
            assertEquals(NotificationDestination.Unavailable(), resolver.resolve(USER_ID, notification(type, " ")))
        }
        coVerify(exactly = 1) { debts.getDebt(USER_ID, "debt") }
        coVerify(exactly = 1) { debts.getDebt(USER_ID, "foreign-or-missing") }
        coVerify(exactly = 1) { goals.getGoal(USER_ID, "goal") }
        coVerify(exactly = 1) { goals.getGoal(USER_ID, "foreign-or-missing") }
        io.mockk.confirmVerified(debts, goals)
    }

    @Test
    fun `existing card owned by active account resolves to card detail`() = runTest {
        val cards = mockk<CardDao>()
        val budgets = mockk<BudgetDao>()
        coEvery { cards.getById(USER_ID, CARD_ID) } returns mockk<CardEntity>()
        val resolver = RegisteredNotificationDestinationResolver(cards, budgets, mockk(), mockk(), mockk())

        assertEquals(NotificationDestination.CardDetail(CARD_ID), resolver.resolve(USER_ID, notification("CARD", CARD_ID)))
        coVerify { cards.getById(USER_ID, CARD_ID) }
    }

    @Test
    fun `missing or invalid card remains in center with friendly unavailable result`() = runTest {
        val cards = mockk<CardDao>()
        val budgets = mockk<BudgetDao>()
        coEvery { cards.getById(USER_ID, CARD_ID) } returns null
        val resolver = RegisteredNotificationDestinationResolver(cards, budgets, mockk(), mockk(), mockk())

        assertEquals(NotificationDestination.Unavailable(), resolver.resolve(USER_ID, notification("card", CARD_ID)))
        assertEquals(NotificationDestination.Unavailable(), resolver.resolve(USER_ID, notification("card", null)))
    }

    @Test
    fun `missing references and unknown types are unavailable`() = runTest {
        val cards = mockk<CardDao>()
        val budgets = mockk<BudgetDao>()
        coEvery { budgets.getBudget(USER_ID, any()) } returns null
        val debts = mockk<com.kipu.app.feature.debts.data.local.DebtDao>()
        val goals = mockk<com.kipu.app.feature.goals.data.local.GoalDao>()
        coEvery { debts.getDebt(USER_ID, any()) } returns null
        coEvery { goals.getGoal(USER_ID, any()) } returns null
        val resolver = RegisteredNotificationDestinationResolver(cards, budgets, mockk(), debts, goals)

        listOf("budget", "DEBT", "GOAL", "future-type").forEach { type ->
            assertEquals(NotificationDestination.Unavailable(), resolver.resolve(USER_ID, notification(type, "target")))
        }
        coVerify(exactly = 0) { cards.getById(any(), any()) }
    }

    @Test
    fun `existing budget resolves to budget detail`() = runTest {
        val cards = mockk<CardDao>()
        val budgets = mockk<BudgetDao>()
        coEvery { budgets.getBudget(USER_ID, BUDGET_ID) } returns mockk<BudgetEntity>()
        val resolver = RegisteredNotificationDestinationResolver(cards, budgets, mockk(), mockk(), mockk())

        assertEquals(
            NotificationDestination.BudgetDetail(BUDGET_ID),
            resolver.resolve(USER_ID, notification("BUDGET", BUDGET_ID)),
        )
        coVerify { budgets.getBudget(USER_ID, BUDGET_ID) }
    }

    @Test
    fun `capture reference resolves only a pending candidate owned by active user`() = runTest {
        val candidates = mockk<com.kipu.app.feature.capture.domain.CaptureCandidateStore>()
        val candidate = mockk<com.kipu.app.feature.capture.domain.CaptureCandidateRecord>()
        io.mockk.every { candidate.status } returns com.kipu.app.feature.capture.domain.CaptureCandidateStatus.PENDING
        coEvery { candidates.get(USER_ID, "candidate") } returns candidate
        val resolver = RegisteredNotificationDestinationResolver(mockk(), mockk(), candidates, mockk(), mockk())
        assertEquals(NotificationDestination.CaptureCandidate("candidate"), resolver.resolve(USER_ID, notification("CAPTURE_CANDIDATE", "candidate")))
        assertEquals(NotificationDestination.Unavailable(), resolver.resolve("other", notification("CAPTURE_CANDIDATE", "candidate")))
    }

    private fun notification(referenceType: String?, referenceId: String?) = AppNotification(
        id = "notice-id",
        userId = USER_ID,
        title = "Aviso",
        body = "Detalle",
        notificationType = "SYSTEM",
        referenceEntityType = referenceType,
        referenceEntityId = referenceId,
        isRead = false,
        createdAt = Instant.parse("2026-09-26T10:00:00Z"),
        deletedAt = null,
    )

    private companion object {
        const val USER_ID = "61000000-0000-4000-8000-000000000001"
        const val CARD_ID = "63000000-0000-4000-8000-000000000003"
        const val BUDGET_ID = "64000000-0000-4000-8000-000000000004"
    }
}
