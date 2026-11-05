package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.movements.domain.MovementMaintenanceRepository
import com.kipu.app.feature.movements.domain.VoidTransaction
import com.kipu.app.feature.movements.domain.model.*
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebtPaymentVoidViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<DebtRepository>(relaxed = true)
    private val dao = mockk<DebtDao>()
    private val session = mockk<SessionCoordinator>()
    private val maintenance = mockk<MovementMaintenanceRepository>()
    private val payment = DebtSettlementActivity("event", 2000L, 100L, 1L, false, transactionId = "principal")
    private val head = MovementRevisionHead("principal", "owner",
        MovementRevisionPayload(MovementType.EXPENSE, "DEBT_PAYMENT", 2000L, "PEN", "account", occurredAt = 1L),
        MovementFinancialState.CONFIRMED, officialRevision = 3L)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { session.localAccess } returns MutableStateFlow(LocalAccess.Available("owner", RemoteSession.Absent))
        every { repository.observeDebt("owner", "debt") } returns flowOf(null)
        every { repository.observeInstallments("owner", "debt") } returns flowOf(emptyList())
        every { dao.observeHasFinancialHistory("owner", "debt") } returns flowOf(true)
        every { dao.observeSettlementActivities("owner", "debt") } returns flowOf(emptyList())
        coEvery { maintenance.getRevisionHead("owner", "principal") } returns head
    }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun model() = DebtDetailViewModel(SavedStateHandle(mapOf("debtId" to "debt")), repository,
        dao, session, maintenance, VoidTransaction(maintenance))

    @Test fun cancelSelectionDoesNotReadOrMutatePayment() = runTest(dispatcher) {
        val vm = model()
        advanceUntilIdle()
        vm.selectPaymentToVoid(payment)
        assertEquals(payment, vm.state.value.selectedPayment)
        vm.dismissPaymentVoid()
        vm.confirmPaymentVoid()
        advanceUntilIdle()
        assertNull(vm.state.value.selectedPayment)
        coVerify(exactly = 0) { maintenance.getRevisionHead(any(), any()) }
        coVerify(exactly = 0) { maintenance.void(any(), any()) }
    }

    @Test fun confirmationUsesCurrentHeadAndPreventsDoubleSubmitOrDismissWhileRunning() = runTest(dispatcher) {
        val completion = CompletableDeferred<MovementMutationResult>()
        val command = slot<MovementRevisionCommand.Void>()
        coEvery { maintenance.void("owner", capture(command)) } coAnswers { completion.await() }
        val vm = model()
        advanceUntilIdle()
        vm.selectPaymentToVoid(payment)
        vm.confirmPaymentVoid()
        vm.confirmPaymentVoid()
        runCurrent()
        vm.dismissPaymentVoid()
        assertTrue(vm.state.value.isVoidingPayment)
        assertEquals(payment, vm.state.value.selectedPayment)
        assertEquals("principal", command.captured.transactionId)
        assertEquals(3L, command.captured.expectedRevision)
        assertTrue(command.captured.idempotencyKey.isNotBlank())
        coVerify(exactly = 1) { maintenance.void("owner", any()) }
        completion.complete(MovementMutationResult.Success(head, isDuplicate = false))
        advanceUntilIdle()
        assertFalse(vm.state.value.isVoidingPayment)
        assertNull(vm.state.value.selectedPayment)
        assertNull(vm.state.value.voidError)
    }

    @Test fun voidedOrNonPaymentActivityCannotBeSelected() = runTest(dispatcher) {
        val vm = model()
        advanceUntilIdle()
        listOf(payment.copy(isVoided = true), payment.copy(eventType = "OPENING"), payment.copy(transactionId = null)).forEach {
            vm.selectPaymentToVoid(it)
            vm.confirmPaymentVoid()
            assertNull(vm.state.value.selectedPayment)
        }
        advanceUntilIdle()
        coVerify(exactly = 0) { maintenance.void(any(), any()) }
    }

    @Test fun rejectionKeepsSelectionAndShowsErrorForRetry() = runTest(dispatcher) {
        coEvery { maintenance.void("owner", any()) } returns MovementMutationResult.Rejected("SETTLEMENT_NOT_ACTIVE")
        val vm = model()
        advanceUntilIdle()
        vm.selectPaymentToVoid(payment)
        vm.confirmPaymentVoid()
        advanceUntilIdle()
        assertEquals(payment, vm.state.value.selectedPayment)
        assertFalse(vm.state.value.isVoidingPayment)
        assertTrue(vm.state.value.voidError.orEmpty().contains("SETTLEMENT_NOT_ACTIVE"))
        vm.dismissPaymentVoid()
        assertNull(vm.state.value.voidError)
    }
}
