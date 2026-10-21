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
        coEvery { store.get("owner", "candidate") } returns candidate
    }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun viewModel() = ReceiptFlowViewModel(store, importer, confirmer, session, SavedStateHandle())

    @Test fun `scan creates editable selection but never confirms money automatically`() = runTest {
        coEvery { importer("owner", any(), true) } returns ReceiptImportResult.CandidateCreated(candidate)
        val vm = viewModel()
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        assertEquals(candidate, vm.state.value.selected)
        assertFalse(vm.state.value.readyForMovement)
        assertFalse(vm.state.value.busy)
        coVerify(exactly = 0) { confirmer(any(), any(), any()) }
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        coVerify(exactly = 1) { importer(any(), any(), any()) }
    }

    @Test fun `registered receipt explains duplicate with operation and amount`() = runTest {
        coEvery { importer("owner", any(), true) } returns ReceiptImportResult.Duplicate(candidate.id)
        coEvery { store.get("owner", candidate.id) } returns candidate.copy(status = ReceiptCandidateStatus.CONFIRMED)
        val vm = viewModel()
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        val message = vm.state.value.message.orEmpty()
        assertTrue(message, message.contains("Ya registraste"))
        assertTrue(message, message.contains("01234567"))
        assertTrue(message, message.contains("S/ 12.50"))
        assertEquals("Posible movimiento duplicado", vm.state.value.warningTitle)
        assertNull(vm.state.value.selected)
        coVerify(exactly = 0) { confirmer(any(), any(), any()) }
        vm.dismissMessage()
        assertNull(vm.state.value.message)
        assertNull(vm.state.value.warningTitle)
    }

    @Test fun `discarded receipt is not described as a registered movement`() = runTest {
        coEvery { importer("owner", any(), true) } returns ReceiptImportResult.Duplicate(candidate.id)
        coEvery { store.get("owner", candidate.id) } returns candidate.copy(status = ReceiptCandidateStatus.DISCARDED)
        val vm = viewModel()
        vm.import("content://receipts/test.png")
        advanceUntilIdle()
        assertTrue(vm.state.value.message.orEmpty().contains("descartado"))
        assertFalse(vm.state.value.message.orEmpty().contains("registrado"))
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
