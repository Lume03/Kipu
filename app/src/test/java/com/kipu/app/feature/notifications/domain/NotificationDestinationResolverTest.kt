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
    fun `existing card owned by active account resolves to card detail`() = runTest {
        val cards = mockk<CardDao>()
        val budgets = mockk<BudgetDao>()
        coEvery { cards.getById(USER_ID, CARD_ID) } returns mockk<CardEntity>()
        val resolver = RegisteredNotificationDestinationResolver(cards, budgets, mockk())

        assertEquals(NotificationDestination.CardDetail(CARD_ID), resolver.resolve(USER_ID, notification("CARD", CARD_ID)))
        coVerify { cards.getById(USER_ID, CARD_ID) }
    }

    @Test
    fun `missing or invalid card remains in center with friendly unavailable result`() = runTest {
        val cards = mockk<CardDao>()
        val budgets = mockk<BudgetDao>()
        coEvery { cards.getById(USER_ID, CARD_ID) } returns null
        val resolver = RegisteredNotificationDestinationResolver(cards, budgets, mockk())

        assertEquals(NotificationDestination.Unavailable(), resolver.resolve(USER_ID, notification("card", CARD_ID)))
        assertEquals(NotificationDestination.Unavailable(), resolver.resolve(USER_ID, notification("card", null)))
    }

    @Test
    fun `future routes are never fabricated for budget debt or goal`() = runTest {
        val cards = mockk<CardDao>()
        val budgets = mockk<BudgetDao>()
        coEvery { budgets.getBudget(USER_ID, any()) } returns null
        val resolver = RegisteredNotificationDestinationResolver(cards, budgets, mockk())

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
        val resolver = RegisteredNotificationDestinationResolver(cards, budgets, mockk())

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
        val resolver = RegisteredNotificationDestinationResolver(mockk(), mockk(), candidates)
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
