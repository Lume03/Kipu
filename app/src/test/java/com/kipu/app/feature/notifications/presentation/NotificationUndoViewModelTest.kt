package com.kipu.app.feature.notifications.presentation

import com.kipu.app.core.session.*
import com.kipu.app.feature.notifications.domain.*
import io.mockk.*
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationUndoViewModelTest {
    @After fun tearDown() = Dispatchers.resetMain()
    private fun notice(id: String, read: Boolean = false) = AppNotification(id, "owner", "Aviso $id", "Texto",
        "BUDGET_ALERT", "BUDGET", "budget", read, Instant.parse("2026-10-15T12:00:00Z"), null)

    private fun TestScope.fixture(): Triple<NotificationsViewModel, MutableStateFlow<List<AppNotification>>, NotificationsRepository> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val rows = MutableStateFlow(listOf(notice("a"), notice("b"), notice("read", true)))
        val repo = mockk<NotificationsRepository>(relaxed = true)
        every { repo.observeActive("owner") } returns rows
        coEvery { repo.dismiss("owner", any()) } coAnswers {
            val id = secondArg<String>()
            rows.value = rows.value.filterNot { it.id == id }
            Result.success(Unit)
        }
        val session = mockk<SessionCoordinator> {
            every { localAccess } returns MutableStateFlow(LocalAccess.Available("owner", RemoteSession.Absent))
        }
        val vm = NotificationsViewModel(repo, session, { _, _ -> NotificationDestination.Unavailable() },
            NotificationDismissalQueue(repo, backgroundScope))
        runCurrent()
        return Triple(vm, rows, repo)
    }
    @Test fun `dismissal hides one notice and decrements unread before persistence`() = runTest {
        val (vm, _, repo) = fixture()
        assertEquals(2, vm.unreadCount.value)
        vm.dismiss("a"); runCurrent()
        assertEquals(listOf("b", "read"), vm.state.value.notifications.map { it.id })
        assertEquals(NotificationDismissalPhase.PENDING, vm.state.value.pendingDismissal?.phase)
        assertEquals(1, vm.unreadCount.value)
        coVerify(exactly = 0) { repo.dismiss(any(), any()) }
    }
    @Test fun `undo restores notice in place and unread without writing`() = runTest {
        val (vm, _, repo) = fixture()
        vm.dismiss("a"); runCurrent()
        val token = requireNotNull(vm.state.value.pendingDismissal).token
        vm.undoDismissal(token); runCurrent()
        assertEquals(listOf("a", "b", "read"), vm.state.value.notifications.map { it.id })
        assertEquals(2, vm.unreadCount.value)
        advanceTimeBy(5001); runCurrent()
        coVerify(exactly = 0) { repo.dismiss(any(), any()) }
    }
    @Test fun `expiry commits exactly once and removes overlay after source confirms`() = runTest {
        val (vm, rows, repo) = fixture()
        vm.dismiss("a"); runCurrent()
        advanceTimeBy(4999); runCurrent()
        coVerify(exactly = 0) { repo.dismiss(any(), any()) }
        advanceTimeBy(1); runCurrent()
        coVerify(exactly = 1) { repo.dismiss("owner", "a") }
        assertEquals(listOf("b", "read"), rows.value.map { it.id })
        assertTrue(vm.pendingDismissals.value.isEmpty())
        assertEquals(1, vm.unreadCount.value)
    }
    @Test fun `second dismissal commits first but can undo second independently`() = runTest {
        val (vm, rows, repo) = fixture()
        vm.dismiss("a"); runCurrent()
        vm.dismiss("b"); runCurrent()
        coVerify(exactly = 1) { repo.dismiss("owner", "a") }
        coVerify(exactly = 0) { repo.dismiss("owner", "b") }
        vm.undoDismissal(requireNotNull(vm.state.value.pendingDismissal).token); runCurrent()
        assertEquals(listOf("b", "read"), rows.value.map { it.id })
        assertEquals(listOf("b", "read"), vm.state.value.notifications.map { it.id })
        assertEquals(1, vm.unreadCount.value)
    }
    @Test fun `leaving screen commits pending deletion without affecting other rows`() = runTest {
        val (vm, rows, repo) = fixture()
        vm.dismiss("a"); runCurrent()
        vm.commitPendingDismissals(); runCurrent()
        coVerify(exactly = 1) { repo.dismiss("owner", "a") }
        coVerify(exactly = 0) { repo.dismiss("owner", "b") }
        assertEquals(listOf("b", "read"), rows.value.map { it.id })
    }
    @Test fun `failed commit restores visibility and emits recoverable message`() = runTest {
        val (vm, _, repo) = fixture()
        coEvery { repo.dismiss("owner", "a") } returns Result.failure(IllegalStateException("private detail"))
        vm.dismiss("a"); runCurrent()
        vm.commitPendingDismissals(); runCurrent()
        assertEquals(3, vm.state.value.notifications.size)
        assertEquals(2, vm.unreadCount.value)
        assertNull(vm.state.value.pendingDismissal)
        val event = vm.events.first() as NotificationsUiEvent.ShowMessage
        assertEquals("No se pudo eliminar el aviso. Inténtalo nuevamente.", event.message)
        assertFalse(event.message.contains("private detail"))
    }
    @Test fun `committing overlay prevents resurrection until Room deletion arrives`() = runTest {
        val (vm, rows, repo) = fixture()
        coEvery { repo.dismiss("owner", "a") } returns Result.success(Unit)
        vm.dismiss("a"); runCurrent()
        vm.commitPendingDismissals(); runCurrent()
        val pending = vm.pendingDismissals.value.values.single()
        assertEquals(NotificationDismissalPhase.COMMITTING, pending.phase)
        vm.undoDismissal(pending.token); runCurrent()
        assertEquals(2, vm.state.value.notifications.size)
        assertEquals(1, vm.unreadCount.value)
        rows.value = rows.value.filterNot { it.id == "a" }; runCurrent()
        assertTrue(vm.pendingDismissals.value.isEmpty())
        assertEquals(1, vm.unreadCount.value)
    }
}
