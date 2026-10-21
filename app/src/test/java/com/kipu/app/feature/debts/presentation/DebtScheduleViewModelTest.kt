package com.kipu.app.feature.debts.presentation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DebtScheduleViewModelTest {
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
    fun overlappingSaveCallbacksEmitOneMutationAndLaterRetryCanRun() = runTest(dispatcher) {
        val userId = "88000000-0000-4000-8000-000000000001"
        val repository = mockk<DebtRepository>(relaxed = true)
        val session = mockk<SessionCoordinator>()
        every { session.localAccess } returns MutableStateFlow(LocalAccess.Available(userId, RemoteSession.Absent))
        every { repository.observeDebt(userId, DEBT_ID) } returns flowOf(debt(userId))
        every { repository.observeInstallments(userId, DEBT_ID) } returns flowOf(emptyList())

        val firstCall = CompletableDeferred<Unit>()
        val firstResult = CompletableDeferred<DebtCommandResult>()
        coEvery { repository.setDebtSchedule(userId, any()) } coAnswers {
            firstCall.complete(Unit)
            firstResult.await()
        } andThen DebtCommandResult.Retryable("offline") andThen DebtCommandResult.Applied(DEBT_ID, 2, 10_000L)

        val viewModel = DebtScheduleViewModel(SavedStateHandle(mapOf("debtId" to DEBT_ID)), repository, session)
        advanceUntilIdle()

        viewModel.saveSchedule()
        viewModel.saveSchedule()
        runCurrent()
        assertTrue(firstCall.isCompleted)
        coVerify(exactly = 1) { repository.setDebtSchedule(userId, any()) }
        assertTrue(viewModel.state.value.isSaving)

        firstResult.complete(DebtCommandResult.Applied(DEBT_ID, 2, 10_000L))
        runCurrent()
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(viewModel.state.value.successMessage.orEmpty().contains("este dispositivo"))

        viewModel.saveSchedule()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(viewModel.state.value.successMessage.orEmpty().contains("sincronizará"))

        viewModel.saveSchedule()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(viewModel.state.value.successMessage.orEmpty().contains("este dispositivo"))
        coVerify(exactly = 3) { repository.setDebtSchedule(userId, any()) }
    }

    @Test
    fun overlappingSaveIsRejectedWhileRepositoryThrowsAndRetrySucceeds() = runTest(dispatcher) {
        val userId = "88000000-0000-4000-8000-000000000001"
        val repository = mockk<DebtRepository>(relaxed = true)
        val session = mockk<SessionCoordinator>()
        every { session.localAccess } returns MutableStateFlow(LocalAccess.Available(userId, RemoteSession.Absent))
        every { repository.observeDebt(userId, DEBT_ID) } returns flowOf(debt(userId))
        every { repository.observeInstallments(userId, DEBT_ID) } returns flowOf(emptyList())

        val firstCall = CompletableDeferred<Unit>()
        val allowFailure = CompletableDeferred<Unit>()
        coEvery { repository.setDebtSchedule(userId, any()) } coAnswers {
            firstCall.complete(Unit)
            allowFailure.await()
            throw IllegalStateException("recoverable repository failure")
        } andThen DebtCommandResult.Applied(DEBT_ID, 2, 10_000L)

        val viewModel = DebtScheduleViewModel(SavedStateHandle(mapOf("debtId" to DEBT_ID)), repository, session)
        advanceUntilIdle()

        viewModel.saveSchedule()
        runCurrent()
        assertTrue(firstCall.isCompleted)
        viewModel.saveSchedule()
        runCurrent()
        coVerify(exactly = 1) { repository.setDebtSchedule(userId, any()) }
        assertTrue(viewModel.state.value.isSaving)

        allowFailure.complete(Unit)
        runCurrent()
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(viewModel.state.value.errorMessage.orEmpty().contains("No se pudo confirmar"))
        coVerify(exactly = 1) { repository.setDebtSchedule(userId, any()) }

        viewModel.saveSchedule()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSaving)
        assertTrue(viewModel.state.value.successMessage.orEmpty().contains("este dispositivo"))
        coVerify(exactly = 2) { repository.setDebtSchedule(userId, any()) }
    }

    private fun debt(userId: String) = DebtSummary(
        debtId = DEBT_ID,
        userId = userId,
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Proveedor",
        principalMinor = 10_000L,
        remainingPrincipalMinor = 10_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-01"),
        dueDate = null,
        reminderLeadDays = 2,
        notes = null,
        status = DebtLifecycleStatus.ACTIVE,
        syncState = "PENDING",
        revision = 1L,
        openingMode = DebtOpeningMode.HISTORICAL,
    )

    private companion object {
        const val DEBT_ID = "88000000-0000-4000-8000-000000000002"
    }
}
