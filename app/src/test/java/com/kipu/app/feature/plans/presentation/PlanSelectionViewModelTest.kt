package com.kipu.app.feature.plans.presentation

import com.kipu.app.R
import com.kipu.app.feature.plans.domain.ConfirmPlanSelection
import com.kipu.app.feature.plans.domain.OperationIdProvider
import com.kipu.app.feature.plans.domain.PlanPreferencesRepository
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.PlanSelection
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySource
import com.kipu.app.feature.plans.domain.model.TrialEligibilityStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanSelectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val operationId = UUID.fromString("5d92af34-c725-4a1a-a863-2c93fa214c86")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `switching to Free retains the last premium period without persisting`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)

        viewModel.onOptionSelected(CommercialOption.LIFETIME)
        viewModel.onOptionSelected(CommercialOption.FREE)

        assertEquals(CommercialOption.FREE, viewModel.uiState.value.selectedOption)
        assertEquals(CommercialOption.LIFETIME, viewModel.uiState.value.premiumOption)
        assertEquals(0, repository.confirmCalls)

        viewModel.onOptionSelected(viewModel.uiState.value.premiumOption)
        assertEquals(CommercialOption.LIFETIME, viewModel.uiState.value.selectedOption)
    }

    @Test
    fun `confirming the Free tab persists only Free`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)
        viewModel.onOptionSelected(CommercialOption.FREE)

        viewModel.confirmSelection()
        advanceUntilIdle()

        assertEquals(PlanSelection.FREE, repository.lastSelection)
        assertEquals(1, repository.confirmCalls)
    }

    @Test
    fun `selection cannot change while confirmation is pending`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePlanPreferencesRepository(confirmGate = gate)
        val viewModel = viewModel(repository)
        viewModel.onOptionSelected(CommercialOption.MONTHLY)
        viewModel.confirmSelection()
        runCurrent()

        viewModel.onOptionSelected(CommercialOption.FREE)
        assertEquals(CommercialOption.MONTHLY, viewModel.uiState.value.selectedOption)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(PlanSelection.PREMIUM_INTENT, repository.lastSelection)
    }

    @Test
    fun `initial premium selection is Annual and opening the screen does not persist it`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)

        assertEquals(CommercialOption.ANNUAL, viewModel.uiState.value.selectedOption)
        assertEquals(0, repository.confirmCalls)
    }

    @Test
    fun `selecting an option changes only UI state`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)

        viewModel.onOptionSelected(CommercialOption.MONTHLY)

        assertEquals(CommercialOption.MONTHLY, viewModel.uiState.value.selectedOption)
        assertEquals(0, repository.confirmCalls)
    }

    @Test
    fun `eligibility loads without changing the Annual visual selection`() = runTest(dispatcher) {
        listOf(eligible, ineligible, TrialEligibilitySnapshot.UNKNOWN).forEach { snapshot ->
            val viewModel = viewModel(FakePlanPreferencesRepository(eligibilityValue = snapshot))
            advanceUntilIdle()
            assertEquals(snapshot, viewModel.uiState.value.eligibility)
            assertEquals(CommercialOption.ANNUAL, viewModel.uiState.value.selectedOption)
        }
    }

    @Test
    fun `continue Free commits Free without changing the premium selection`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.continueWithFree()
        advanceUntilIdle()

        assertEquals(PlanSelection.FREE, repository.lastSelection)
        assertEquals(CommercialOption.ANNUAL, viewModel.uiState.value.selectedOption)
        assertEquals(1, repository.confirmCalls)
    }

    @Test
    fun `confirming default Annual maps verified eligibility to Trial intent`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository(eligibilityValue = eligible)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.confirmSelection()
        advanceUntilIdle()

        assertEquals(PlanSelection.TRIAL_INTENT, repository.lastSelection)
        assertEquals(1, repository.confirmCalls)
    }

    @Test
    fun `abandoning without confirmation performs zero repository writes`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository()
        val viewModel = viewModel(repository)
        viewModel.onOptionSelected(CommercialOption.MONTHLY)

        advanceUntilIdle()

        assertEquals(0, repository.confirmCalls)
    }

    @Test
    fun `double submit while local commit is pending is coalesced`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePlanPreferencesRepository(confirmGate = gate)
        val viewModel = viewModel(repository)
        viewModel.onOptionSelected(CommercialOption.MONTHLY)
        advanceUntilIdle()

        viewModel.confirmSelection()
        viewModel.confirmSelection()
        runCurrent()

        assertEquals(1, repository.confirmCalls)
        assertTrue(viewModel.uiState.value.isSubmitting)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `repository failures expose only a safe presentation error`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository(confirmFailure = IllegalStateException("token=secret"))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.confirmSelection()
        advanceUntilIdle()

        assertEquals(PlanSelectionError.CONFIRMATION_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isConfirming)
        assertTrue(viewModel.uiState.value.canConfirm)
        assertFalse(viewModel.uiState.value.toString().contains("token=secret"))

        repository.confirmFailure = null
        viewModel.confirmSelection()
        advanceUntilIdle()
        assertEquals(2, repository.confirmCalls)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `confirmation event is emitted once and only after repository success`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePlanPreferencesRepository(confirmGate = gate)
        val viewModel = viewModel(repository)
        val events = mutableListOf<PlanSelectionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect(events::add)
        }
        advanceUntilIdle()

        viewModel.confirmSelection()
        runCurrent()
        assertTrue(events.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(PlanSelectionEvent.Confirmed), events)
        assertEquals(1, repository.confirmCalls)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `saved Free preference is preselected without writes`() = runTest(dispatcher) {
        val repository = FakePlanPreferencesRepository(preference = PlanSelection.FREE)
        val viewModel = viewModel(repository)

        assertEquals(CommercialOption.FREE, viewModel.uiState.value.selectedOption)
        assertEquals(CommercialOption.ANNUAL, viewModel.uiState.value.premiumOption)
        assertFalse(viewModel.uiState.value.isLoadingPreference)
        assertEquals(0, repository.confirmCalls)
    }

    @Test
    fun `saved Premium and Trial intents open Premium with Annual as the unstored period`() = runTest(dispatcher) {
        listOf(PlanSelection.PREMIUM_INTENT, PlanSelection.TRIAL_INTENT).forEach { preference ->
            val repository = FakePlanPreferencesRepository(preference = preference)
            val viewModel = viewModel(repository)

            assertEquals(CommercialOption.ANNUAL, viewModel.uiState.value.selectedOption)
            assertEquals(0, repository.confirmCalls)
        }
    }

    @Test
    fun `confirmation waits for the stored preference`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePlanPreferencesRepository(preference = PlanSelection.FREE, preferenceGate = gate)
        val viewModel = viewModel(repository)

        viewModel.confirmSelection()
        viewModel.continueWithFree()
        runCurrent()
        assertFalse(viewModel.uiState.value.canConfirm)
        assertEquals(0, repository.confirmCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canConfirm)
        assertEquals(CommercialOption.FREE, viewModel.uiState.value.selectedOption)
    }

    @Test
    fun `both confirmation actions are ignored while eligibility is loading`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePlanPreferencesRepository(eligibilityValue = eligible, eligibilityGate = gate)
        val viewModel = viewModel(repository)

        viewModel.confirmSelection()
        viewModel.continueWithFree()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoadingEligibility)
        assertFalse(viewModel.uiState.value.canConfirm)
        assertEquals(0, repository.confirmCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canConfirm)
        viewModel.confirmSelection()
        advanceUntilIdle()
        assertEquals(PlanSelection.TRIAL_INTENT, repository.lastSelection)
        assertEquals(1, repository.confirmCalls)
    }

    @Test
    fun `trial copy is shown only for eligible subscriptions`() = runTest(dispatcher) {
        val expired = eligible.copy(validUntil = Instant.parse("2020-01-01T00:00:00Z"))
        listOf(eligible, ineligible, TrialEligibilitySnapshot.UNKNOWN, expired).forEach { snapshot ->
            val viewModel = viewModel(FakePlanPreferencesRepository(eligibilityValue = snapshot))
            assertEquals(
                if (snapshot == eligible) R.string.plan_display_annual_trial else R.string.plan_display_annual_note,
                viewModel.uiState.value.premiumNote,
            )
            viewModel.onOptionSelected(CommercialOption.MONTHLY)
            assertEquals(
                if (snapshot == eligible) R.string.plan_display_monthly_trial else R.string.plan_display_monthly_note,
                viewModel.uiState.value.premiumNote,
            )
            viewModel.onOptionSelected(CommercialOption.LIFETIME)
            assertEquals(R.string.plan_display_lifetime_note, viewModel.uiState.value.premiumNote)
        }
    }

    @Test
    fun `trial copy is hidden until eligibility finishes loading`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(FakePlanPreferencesRepository(eligibilityValue = eligible, eligibilityGate = gate))
        assertEquals(R.string.plan_display_annual_note, viewModel.uiState.value.premiumNote)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(R.string.plan_display_annual_trial, viewModel.uiState.value.premiumNote)
    }

    private fun viewModel(repository: FakePlanPreferencesRepository): PlanSelectionViewModel =
        PlanSelectionViewModel(
            repository = repository,
            confirmPlanSelection = ConfirmPlanSelection(repository, OperationIdProvider { operationId }),
        ).also { dispatcher.scheduler.runCurrent() }

    private class FakePlanPreferencesRepository(
        private val eligibilityValue: TrialEligibilitySnapshot = TrialEligibilitySnapshot.UNKNOWN,
        private val confirmGate: CompletableDeferred<Unit>? = null,
        var confirmFailure: Throwable? = null,
        private val preference: PlanSelection? = null,
        private val preferenceGate: CompletableDeferred<Unit>? = null,
        private val eligibilityGate: CompletableDeferred<Unit>? = null,
    ) : PlanPreferencesRepository {
        override val currentPreference = flow {
            preferenceGate?.await()
            emit(preference)
        }
        var confirmCalls: Int = 0
            private set
        var lastSelection: PlanSelection? = null
            private set

        override suspend fun getTrialEligibility(): TrialEligibilitySnapshot {
            eligibilityGate?.await()
            return eligibilityValue
        }

        override suspend fun confirmSelection(selection: PlanSelection, operationId: UUID) {
            confirmCalls += 1
            lastSelection = selection
            confirmGate?.await()
            confirmFailure?.let { throw it }
        }
    }

    private companion object {
        val eligible = TrialEligibilitySnapshot(
            status = TrialEligibilityStatus.ELIGIBLE,
            source = TrialEligibilitySource.VERIFIED_ACCOUNT_HISTORY,
            verifiedAt = Instant.parse("2026-09-15T10:00:00Z"),
            validUntil = Instant.parse("2030-01-01T00:00:00Z"),
        )
        val ineligible = TrialEligibilitySnapshot(
            status = TrialEligibilityStatus.INELIGIBLE,
            source = TrialEligibilitySource.VERIFIED_ACCOUNT_HISTORY,
            verifiedAt = Instant.parse("2026-09-15T10:00:00Z"),
            validUntil = null,
        )
    }
}
