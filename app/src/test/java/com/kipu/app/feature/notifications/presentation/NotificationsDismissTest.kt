package com.kipu.app.feature.notifications.presentation

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationDestination
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsDismissTest {
    private val userId = "61000000-0000-4000-8000-000000000001"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun dismissReadOnlyRemovesReadNoticesThroughSyncedDismiss() = runTest {
        val read = notice("read-1", isRead = true)
        val unread = notice("unread-1", isRead = false)
        val repository = mockk<NotificationsRepository>(relaxed = true)
        every { repository.observeActive(userId) } returns flowOf(listOf(read, unread))
        every { repository.observeUnreadCount(userId) } returns flowOf(1)
        coEvery { repository.dismiss(any(), any()) } returns Result.success(Unit)
        val session = mockk<SessionCoordinator>()
        every { session.localAccess } returns MutableStateFlow(LocalAccess.Available(userId, RemoteSession.Absent))
        val viewModel = NotificationsViewModel(repository, session) { _, _ -> NotificationDestination.Unavailable() }

        viewModel.dismissRead()

        coVerify(exactly = 1) { repository.dismiss(userId, "read-1") }
        coVerify(exactly = 0) { repository.dismiss(userId, "unread-1") }
    }

    @Test
    fun relativeReceivedLabelIsCompact() {
        val zone = ZoneOffset.ofHours(-5)
        val now = Instant.parse("2026-10-26T20:00:00Z")
        assertEquals("Ahora", relativeReceivedLabel(now.minusSeconds(20), now, zone))
        assertEquals("Hace 5 min", relativeReceivedLabel(now.minusSeconds(300), now, zone))
        assertEquals("Hace 2 h", relativeReceivedLabel(now.minusSeconds(7_200), now, zone))
        assertEquals("Ayer", relativeReceivedLabel(Instant.parse("2026-10-25T20:00:00Z"), now, zone))
        val older = relativeReceivedLabel(Instant.parse("2026-10-20T15:00:00Z"), now, zone)
        assertEquals(true, older.startsWith("20 "))
    }

    private fun notice(id: String, isRead: Boolean) = AppNotification(
        id = id,
        userId = userId,
        title = "Aviso $id",
        body = "Cuerpo",
        notificationType = "BUDGET_ALERT",
        referenceEntityType = "BUDGET",
        referenceEntityId = "budget-1",
        isRead = isRead,
        createdAt = Instant.parse("2026-10-26T10:00:00Z"),
        deletedAt = null,
    )
}
