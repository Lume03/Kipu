package com.kipu.app.feature.budgets.presentation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetStatus
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(kit: BudgetTestKit, id: String = "b1") = BudgetDetailViewModel(
        SavedStateHandle(mapOf(BudgetDetailViewModel.ARG_BUDGET_ID to id)),
        fakeSession(), kit.repository, kit.renew, kit.getConsumption, kit.finish, kit.delete, FakeCategoryOptions(), TEST_ZONE,
    ).also { it.todayProvider = { TEST_TODAY } }

    private fun BudgetDetailViewModel.content() = (state.value as BudgetDetailUiState.Content).content

    @Test
    fun `shows current period and history with snapshot limit`() = runTest(dispatcher) {
        val kit = BudgetTestKit(reader = FakeReader(12_000L))
        val budget = sampleBudget(limit = 50_000L)
        val previous = BudgetPeriod(
            id = "b1:2026-09-01:2026-10-01", budgetId = "b1", ownerId = TEST_OWNER,
            interval = com.kipu.app.feature.budgets.domain.BudgetInterval(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)),
            limitMinor = 30_000L, scope = BudgetScope.Total,
        )
        kit.seed(budget, previous)
        val vm = viewModel(kit)
        advanceUntilIdle()
        val content = vm.content()
        assertEquals(50_000L, content.current!!.consumption.limitMinor)
        assertEquals(24L, content.current!!.consumption.percentWhole)
        assertEquals(38_000L, content.current!!.consumption.remainingMinor)
        assertEquals(1, content.history.size)
        assertEquals(30_000L, content.history.single().consumption.limitMinor)
        assertFalse(content.history.single().isCurrent)
    }

    @Test
    fun `missing budget is not found`() = runTest(dispatcher) {
        val vm = viewModel(BudgetTestKit(), "nope")
        advanceUntilIdle()
        assertIs<BudgetDetailUiState.NotFound>(vm.state.value)
    }

    @Test
    fun `repository failure shows error and reload recovers`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget())
        kit.repository.failObserve = true
        val vm = viewModel(kit)
        advanceUntilIdle()
        assertIs<BudgetDetailUiState.Error>(vm.state.value)
        kit.repository.failObserve = false
        vm.reload()
        advanceUntilIdle()
        assertIs<BudgetDetailUiState.Content>(vm.state.value)
    }

    @Test
    fun `finish keeps history and stops renewal`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget())
        val vm = viewModel(kit)
        advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()
        assertEquals(BudgetDetailEvent.Finished, vm.events.first())
        val content = vm.content()
        assertEquals(BudgetStatus.FINISHED, content.budget.status)
        assertFalse(content.isActive)
        assertNotNull(content.current)
        assertEquals(1, kit.repository.periods.value.size)
    }

    @Test
    fun `delete removes budget and periods and emits Deleted`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget())
        val vm = viewModel(kit)
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        assertEquals(BudgetDetailEvent.Deleted, vm.events.first())
        assertTrue(kit.repository.budgets.value.isEmpty())
        assertTrue(kit.repository.periods.value.isEmpty())
    }

    @Test
    fun `not calling delete leaves everything intact (cancel path)`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget())
        val vm = viewModel(kit)
        advanceUntilIdle()
        assertEquals(0, kit.repository.deleteCalls)
        assertEquals(1, kit.repository.budgets.value.size)
        assertNull(vm.content().actionError)
    }

    @Test
    fun `pending change is described with its effective date`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(
            sampleBudget().copy(
                pendingChange = com.kipu.app.feature.budgets.domain.PendingBudgetChange(
                    BudgetScope.Category("cat-food"), null, LocalDate.of(2026, 11, 1),
                ),
            ),
        )
        val vm = viewModel(kit)
        advanceUntilIdle()
        val label = vm.content().pendingChangeLabel!!
        assertTrue(label.contains("Comida") && label.contains("01/11/2026"))
    }
}
