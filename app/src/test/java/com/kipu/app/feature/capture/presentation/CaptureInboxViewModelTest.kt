package com.kipu.app.feature.capture.presentation

import com.kipu.app.feature.capture.domain.CaptureAccessDecision
import com.kipu.app.feature.capture.domain.CaptureCandidateStatus
import com.kipu.app.feature.capture.domain.DiscardCaptureCandidate
import com.kipu.app.feature.capture.domain.FakeCaptureAccessGate
import com.kipu.app.feature.capture.domain.FakeCaptureCandidateStore
import com.kipu.app.feature.capture.domain.captureCandidate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureInboxViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-28T17:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(store: FakeCaptureCandidateStore, gate: FakeCaptureAccessGate) =
        CaptureInboxViewModel(store, gate, com.kipu.app.feature.capture.domain.testDiscardCaptureCandidate(store, clock))

    @Test fun `lists only the session owner's pending candidates`() = runTest(dispatcher) {
        val mine = captureCandidate(ownerId = "owner-1", id = "mine")
        val foreign = captureCandidate(ownerId = "owner-2", id = "foreign")
        val resolved = captureCandidate(ownerId = "owner-1", id = "done").copy(status = CaptureCandidateStatus.CONFIRMED)
        val vm = viewModel(FakeCaptureCandidateStore(mine, foreign, resolved), FakeCaptureAccessGate("owner-1"))
        advanceUntilIdle()
        assertEquals(listOf("mine"), vm.state.value.candidates.map { it.id })
        assertFalse(vm.state.value.isLoading)
        assertEquals(CaptureAutomationStatus.ACTIVE, vm.state.value.automation)
    }

    @Test fun `automation status mirrors the verified gate and never a flag`() = runTest(dispatcher) {
        val gate = FakeCaptureAccessGate("owner-1", CaptureAccessDecision.PremiumRequired)
        val vm = viewModel(FakeCaptureCandidateStore(), gate)
        advanceUntilIdle()
        assertEquals(CaptureAutomationStatus.PREMIUM_REQUIRED, vm.state.value.automation)

        gate.decision = CaptureAccessDecision.ConsentMissing
        vm.refreshAutomation()
        advanceUntilIdle()
        assertEquals(CaptureAutomationStatus.SETUP_REQUIRED, vm.state.value.automation)
    }

    @Test fun `discard removes the candidate from the list without registering anything`() = runTest(dispatcher) {
        val candidate = captureCandidate(ownerId = "owner-1", id = "c1")
        val store = FakeCaptureCandidateStore(candidate)
        val vm = viewModel(store, FakeCaptureAccessGate("owner-1"))
        advanceUntilIdle()
        vm.discard("c1")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), vm.state.value.candidates.map { it.id })
        assertEquals(CaptureCandidateStatus.DISCARDED, store.row("owner-1", "c1")!!.status)
        assertNull(store.row("owner-1", "c1")!!.resolvedTransactionId)
        assertNotNull(vm.state.value.message)
        vm.dismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test fun `association inbox restores resolved captures and clears after None`() = runTest(dispatcher) {
        val pending = captureCandidate(id = "association").copy(status = CaptureCandidateStatus.AUTO_REGISTERED,
            expectedMatchState = com.kipu.app.feature.capture.domain.ExpectedMatchState.PENDING_CHOICE)
        val foreign = pending.copy(ownerId = "owner-2", id = "foreign")
        val store = FakeCaptureCandidateStore(pending, foreign)
        val vm = viewModel(store, FakeCaptureAccessGate())
        advanceUntilIdle()
        assertEquals(listOf("association"), vm.state.value.pendingAssociations.map { it.id })
        assertEquals(0, vm.state.value.candidates.size)
        store.setExpectedMatchState(pending.ownerId, pending.id, com.kipu.app.feature.capture.domain.ExpectedMatchState.NONE, clock.millis())
        advanceUntilIdle()
        assertEquals(0, vm.state.value.pendingAssociations.size)
    }
}
