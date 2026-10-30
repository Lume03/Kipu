package com.kipu.app.navigation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.receipts.domain.*
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReceiptFlowViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = mockk<ReceiptCandidateStore>()
    private val importer = mockk<ImportYapeReceipt>()
    private val confirmer = mockk<ConfirmReceiptCandidate>()
    private val session = mockk<SessionCoordinator>()
    private val access = MutableStateFlow<LocalAccess>(LocalAccess.Available("owner", RemoteSession.Absent))
    private val candidate = ReceiptCandidate(
        id = "candidate", ownerId = "owner", amountMinor = 1250, occurredAt = 1792594800000,
        recipient = "Persona de prueba", reference = "01234567", referenceFingerprint = "ref-hash",
        imageFingerprint = "image-hash", status = ReceiptCandidateStatus.PENDING, createdAt = 1, updatedAt = 1,
    )

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { session.localAccess } returns access
        every { store.observe("owner") } returns flowOf(emptyList())
        every { store.observeRegistered("owner") } returns flowOf(emptyList())
        coEvery { store.get("owner", "candidate") } returns candidate
    }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun viewModel() = ReceiptFlowViewModel(store, importer, confirmer, session, SavedStateHandle())

    @Test fun `scan returns to inbox with new marker but never confirms money automatically`() = runTest {
        coEvery { importer("owner", any(), true) } returns ReceiptImportResult.CandidateCreated(candidate)
        val vm = viewModel()
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        assertNull(vm.state.value.selected)
        assertEquals(candidate.id, vm.state.value.newCandidateId)
        assertEquals(candidate.id, vm.state.value.addedEventId)
        assertFalse(vm.state.value.readyForMovement)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { confirmer(any(), any(), any()) }
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        coVerify(exactly = 1) { importer(any(), any(), any()) }
    }

    @Test fun `registered receipt opens matching notice with operation amount and movement link`() = runTest {
        coEvery { importer("owner", any(), true) } returns ReceiptImportResult.Duplicate(candidate.id)
        coEvery { store.get("owner", candidate.id) } returns candidate.copy(status = ReceiptCandidateStatus.CONFIRMED)
        coEvery { store.registeredMovementId("owner", candidate.id) } returns "movement-id"
        val vm = viewModel()
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        assertEquals(ReceiptImportNotice.ALREADY_REGISTERED, vm.state.value.notice)
        assertEquals("01234567", vm.state.value.duplicateCandidate?.reference)
        assertEquals(1250L, vm.state.value.duplicateCandidate?.amountMinor)
        assertEquals("movement-id", vm.state.value.duplicateMovementId)
        assertNull(vm.state.value.selected)
        coVerify(exactly = 0) { confirmer(any(), any(), any()) }
        vm.dismissNotice()
        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.duplicateCandidate)
    }

    @Test fun `discarded receipt is not described as a registered movement`() = runTest {
        coEvery { importer("owner", any(), true) } returns ReceiptImportResult.Duplicate(candidate.id)
        coEvery { store.get("owner", candidate.id) } returns candidate.copy(status = ReceiptCandidateStatus.DISCARDED)
        val vm = viewModel()
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        assertEquals(ReceiptImportNotice.ALREADY_DISCARDED, vm.state.value.notice)
        assertNull(vm.state.value.message)
    }

    @Test fun `start review and next keep original queue positions until last confirmation`() = runTest {
        val older = candidate.copy(id = "older", createdAt = 0)
        every { store.observe("owner") } returns flowOf(listOf(older, candidate))
        coEvery { store.get("owner", older.id) } returns older
        val vm = viewModel()
        advanceUntilIdle()
        vm.startReview()
        advanceUntilIdle()
        assertEquals("candidate", vm.state.value.selected?.id)
        assertEquals("1 de 2", vm.state.value.queue?.position)
        vm.movementSaved()
        assertEquals(candidate, vm.state.value.savedCandidate)
        vm.reviewNext()
        advanceUntilIdle()
        assertEquals("older", vm.state.value.selected?.id)
        assertEquals("2 de 2", vm.state.value.queue?.position)
        vm.movementSaved()
        assertNull(vm.state.value.savedCandidate)
        assertNull(vm.state.value.selected)
        assertTrue(vm.state.value.candidates.isEmpty())
    }

    @Test fun `restore discarded receipt reuses identity without reading image again`() = runTest {
        coEvery { importer("owner", any(), true) } returns ReceiptImportResult.Duplicate(candidate.id)
        coEvery { store.get("owner", candidate.id) } returns candidate.copy(status = ReceiptCandidateStatus.DISCARDED)
        coEvery { store.restore("owner", candidate.id) } returns true
        val vm = viewModel()
        vm.import("content://receipts/old.png")
        advanceUntilIdle()
        vm.restoreDuplicate()
        advanceUntilIdle()
        assertNull(vm.state.value.notice)
        assertEquals(candidate.id, vm.state.value.newCandidateId)
        coVerify(exactly = 1) { importer(any(), any(), any()) }
        coVerify(exactly = 1) { store.restore("owner", candidate.id) }
        coVerify(exactly = 0) { confirmer(any(), any(), any()) }
    }

    @Test fun `picker opens fresh consent session and cancel never opens image`() = runTest {
        val vm = viewModel()
        vm.requestImport("content://picker/image", "captura.png", 2048)
        assertFalse(vm.state.value.importConsumed)
        assertEquals("content://picker/image", vm.state.value.pendingImportUri)
        vm.cancelImport()
        advanceUntilIdle()
        assertTrue(vm.state.value.importConsumed)
        assertNull(vm.state.value.pendingImportUri)
        coVerify(exactly = 0) { importer(any(), any(), any()) }
    }

    @Test fun `scan access denial gives manual fallback without confirming`() = runTest {
        coEvery { importer("owner", any(), true) } returns ReceiptImportResult.AccessDenied
        val vm = viewModel()
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        assertNull(vm.state.value.selected)
        assertTrue(vm.state.value.message.orEmpty().contains("manualmente"))
        assertFalse(vm.state.value.message.orEmpty().contains("Premium"))
        coVerify(exactly = 0) { confirmer(any(), any(), any()) }
    }

    @Test fun `changing owner removes private review data`() = runTest {
        val vm = viewModel()
        vm.select("candidate")
        advanceUntilIdle()
        assertNotNull(vm.state.value.selected)
        access.value = LocalAccess.Available("other-owner", RemoteSession.Absent)
        advanceUntilIdle()
        assertNull(vm.state.value.selected)
        assertTrue(vm.state.value.candidates.isEmpty())
        vm.select("candidate")
        advanceUntilIdle()
        coVerify(exactly = 1) { store.get("owner", "candidate") }
    }

    @Test fun `restored candidate waits for session restoration without repeating OCR`() = runTest {
        access.value = LocalAccess.NoOwner
        val savedState = SavedStateHandle(mapOf("selectedCandidate" to "candidate", "importConsumed" to true))
        val vm = ReceiptFlowViewModel(store, importer, confirmer, session, savedState)
        advanceUntilIdle()
        assertNull(vm.state.value.selected)
        coVerify(exactly = 0) { store.get(any(), any()) }
        access.value = LocalAccess.Available("owner", RemoteSession.Absent)
        advanceUntilIdle()
        assertEquals(candidate, vm.state.value.selected)
        assertTrue(vm.state.value.importConsumed)
        coVerify(exactly = 0) { importer(any(), any(), any()) }
    }
}
