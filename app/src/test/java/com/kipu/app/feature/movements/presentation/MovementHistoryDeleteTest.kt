package com.kipu.app.feature.movements.presentation

import com.kipu.app.core.session.*
import com.kipu.app.feature.movements.data.MovementDisplayPreferences
import com.kipu.app.feature.movements.domain.*
import com.kipu.app.feature.movements.domain.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MovementHistoryDeleteTest {
    private val dispatcher = StandardTestDispatcher()
    private val maintenance = mockk<MovementMaintenanceRepository>()
    private val display = mockk<MovementDisplayPreferences>()
    private val hidden = MutableStateFlow(emptySet<String>())
    private val access = MutableStateFlow<LocalAccess>(LocalAccess.Available("owner", RemoteSession.Absent))
    private val session = mockk<SessionCoordinator>()
    private val item = TransactionItem(Transaction(id = "movement", userId = "owner", type = MovementType.EXPENSE,
        amountMinor = 1500, currency = "PEN", sourceAccountId = "cash", categoryId = "food", occurredAt = 1,
        operationKind = "STANDARD"), merchantName = "MINISO")
    private var head = MovementRevisionHead("movement", "owner", MovementRevisionPayload(MovementType.EXPENSE,
        "STANDARD", 1500, "PEN", "cash", categoryId = "food", occurredAt = 1), MovementFinancialState.CONFIRMED, 1)
    private lateinit var vm: MovementHistoryViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { session.localAccess } returns access
        every { session.remoteSession } returns MutableStateFlow(RemoteSession.Absent)
        every { display.observe("owner") } returns hidden
        coEvery { display.hide("owner", "movement") } coAnswers { hidden.value += "movement" }
        coEvery { maintenance.getRevisionHead("owner", "movement") } answers { head }
        coEvery { maintenance.void("owner", any()) } coAnswers {
            head = head.copy(financialState = MovementFinancialState.VOIDED, officialRevision = 2)
            MovementMutationResult.Success(head, false)
        }
        vm = MovementHistoryViewModel(mockk(relaxed = true), session, VoidTransaction(maintenance), maintenance,
            displayPreferences = display)
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun activeVoidsExactlyOnceAndHidesAfterExplicitConfirmation() = runTest {
        vm.onSelectTransactionForDelete(item)
        coVerify(exactly = 0) { maintenance.void(any(), any()) }
        vm.onConfirmDelete(); vm.onConfirmDelete()
        advanceUntilIdle()
        coVerify(exactly = 1) { maintenance.void("owner", any()) }
        coVerify(exactly = 1) { display.hide("owner", "movement") }
        assertNull(vm.uiState.value.selectedTransactionForDelete)
    }
    @Test fun alreadyVoidedIncludingSpecializedOnlyHides() = runTest {
        head = head.copy(financialState = MovementFinancialState.VOIDED)
        vm.onSelectTransactionForDelete(item.copy(transaction = item.transaction.copy(status = TransactionStatus.VOIDED, operationKind = "REFUND")))
        vm.onConfirmDelete(); advanceUntilIdle()
        coVerify(exactly = 0) { maintenance.void(any(), any()) }
        coVerify(exactly = 1) { display.hide("owner", "movement") }
    }
    @Test fun cancelNeverChangesFinancialOrDisplayData() = runTest {
        vm.onSelectTransactionForDelete(item); vm.onDismissDelete(); vm.onConfirmDelete(); advanceUntilIdle()
        coVerify(exactly = 0) { maintenance.void(any(), any()) }
        coVerify(exactly = 0) { display.hide(any(), any()) }
    }
    @Test fun hideFailureAfterVoidRetriesWithoutSecondReversal() = runTest {
        coEvery { display.hide(any(), any()) } throws IllegalStateException("disk") andThen Unit
        vm.onSelectTransactionForDelete(item); vm.onConfirmDelete(); advanceUntilIdle()
        assertEquals("disk", vm.uiState.value.deleteError)
        vm.onConfirmDelete(); advanceUntilIdle()
        coVerify(exactly = 1) { maintenance.void(any(), any()) }
        coVerify(exactly = 2) { display.hide(any(), any()) }
        assertNull(vm.uiState.value.selectedTransactionForDelete)
    }
    @Test fun uncertainFailureReusesCommandIdentity() = runTest {
        val keys = mutableListOf<String>()
        coEvery { maintenance.void(any(), any()) } coAnswers {
            keys += secondArg<MovementRevisionCommand.Void>().idempotencyKey
            if (keys.size == 1) error("retry")
            head = head.copy(financialState = MovementFinancialState.VOIDED)
            MovementMutationResult.Success(head, false)
        }
        vm.onSelectTransactionForDelete(item); vm.onConfirmDelete(); advanceUntilIdle()
        coVerify(exactly = 0) { display.hide(any(), any()) }
        vm.onConfirmDelete(); advanceUntilIdle()
        assertEquals(2, keys.size); assertEquals(keys[0], keys[1])
    }
    @Test fun rejectedOrConflictedVoidNeverHides() = runTest {
        coEvery { maintenance.void(any(), any()) } returns MovementMutationResult.Rejected("LEDGER_MISMATCH")
        vm.onSelectTransactionForDelete(item); vm.onConfirmDelete(); advanceUntilIdle()
        assertNotNull(vm.uiState.value.deleteError)
        coEvery { maintenance.void(any(), any()) } returns MovementMutationResult.Conflict(head, "REVISION_CONFLICT")
        vm.onConfirmDelete(); advanceUntilIdle()
        coVerify(exactly = 0) { display.hide(any(), any()) }
        assertNotNull(vm.uiState.value.selectedTransactionForDelete)
    }
    @Test fun specializedActiveFailedAndForeignRowsCannotDelete() = runTest {
        for (tx in listOf(item.transaction.copy(operationKind = "DEBT_PAYMENT"),
            item.transaction.copy(legacyKind = "OPENING"), item.transaction.copy(status = TransactionStatus.FAILED),
            item.transaction.copy(userId = "other"))) {
            vm.onSelectTransactionForDelete(item.copy(transaction = tx)); vm.onConfirmDelete()
        }
        advanceUntilIdle()
        coVerify(exactly = 0) { maintenance.void(any(), any()) }
        coVerify(exactly = 0) { display.hide(any(), any()) }
    }
    @Test fun revisedAndLegacyActiveStandardRemainEligible() {
        assertTrue(canDeleteFromHistory(item))
        assertTrue(canDeleteFromHistory(item.copy(transaction = item.transaction.copy(status = TransactionStatus.REVISED))))
    }
}
