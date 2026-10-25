package com.kipu.app.feature.budgets.presentation

import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetStatus
import com.kipu.app.feature.budgets.domain.PendingBudgetChange
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(kit: BudgetTestKit, owner: String? = TEST_OWNER) =
        BudgetListViewModel(
            fakeSession(owner), kit.repository, kit.renew, kit.getConsumption, FakeCategoryOptions(), kit.plan, TEST_ZONE,
        ).also { it.todayProvider = { TEST_TODAY } }

    @Test
    fun `first resume does not duplicate initial read and return refreshes without discarding content`() = runTest(dispatcher) {
        val reader = FakeReader(consumed = 12_000L)
        val kit = BudgetTestKit(reader = reader)
        kit.seed(sampleBudget())
        val vm = viewModel(kit)
        advanceUntilIdle()
        reader.consumed = 18_000L
        vm.onResume()
        advanceUntilIdle()
        assertEquals(12_000L, assertIs<BudgetListUiState.Content>(vm.state.value).items.single().consumption!!.consumedMinor)
        vm.onResume()
        assertIs<BudgetListUiState.Content>(vm.state.value)
        advanceUntilIdle()
        assertEquals(18_000L, assertIs<BudgetListUiState.Content>(vm.state.value).items.single().consumption!!.consumedMinor)
    }

    @Test
    fun `starts in loading and then shows empty with quota`() = runTest(dispatcher) {
        val vm = viewModel(BudgetTestKit())
        assertIs<BudgetListUiState.Loading>(vm.state.value)
        advanceUntilIdle()
        val empty = assertIs<BudgetListUiState.Empty>(vm.state.value)
        assertEquals(0, empty.quota.activeCount)
        assertEquals(2, empty.quota.limit)
    }

    @Test
    fun `content exposes limit consumed remaining and percent from domain`() = runTest(dispatcher) {
        val kit = BudgetTestKit(reader = FakeReader(consumed = 12_000L))
        kit.seed(sampleBudget(limit = 50_000L))
        val vm = viewModel(kit)
        advanceUntilIdle()
        val content = assertIs<BudgetListUiState.Content>(vm.state.value)
        val consumption = content.items.single().consumption!!
        assertEquals(50_000L, consumption.limitMinor)
        assertEquals(12_000L, consumption.consumedMinor)
        assertEquals(38_000L, consumption.remainingMinor)
        assertEquals(24L, consumption.percentWhole)
        assertEquals("Todos los gastos", content.items.single().scopeLabel)
    }

    @Test
    fun `exceeded budget reports negative remaining`() = runTest(dispatcher) {
        val kit = BudgetTestKit(reader = FakeReader(consumed = 60_000L))
        kit.seed(sampleBudget(limit = 50_000L))
        val vm = viewModel(kit)
        advanceUntilIdle()
        val consumption = (vm.state.value as BudgetListUiState.Content).items.single().consumption!!
        assertTrue(consumption.isExceeded)
        assertEquals(-10_000L, consumption.remainingMinor)
    }

    @Test
    fun `free quota reached at two active budgets and finished ones do not count`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget("a", "A"))
        kit.seed(sampleBudget("b", "B"))
        kit.seed(sampleBudget("c", "C", status = BudgetStatus.FINISHED))
        val vm = viewModel(kit)
        advanceUntilIdle()
        val content = assertIs<BudgetListUiState.Content>(vm.state.value)
        assertEquals(3, content.items.size)
        assertEquals(2, content.quota.activeCount)
        assertTrue(content.quota.isAtLimit)
        assertEquals(BudgetStatus.FINISHED, content.items.last().status)
    }

    @Test
    fun `premium is never at limit`() = runTest(dispatcher) {
        val kit = BudgetTestKit(plan = FakePlanAccess(premium = true))
        kit.seed(sampleBudget("a"))
        kit.seed(sampleBudget("b"))
        kit.seed(sampleBudget("c"))
        val vm = viewModel(kit)
        advanceUntilIdle()
        assertTrue(!(vm.state.value as BudgetListUiState.Content).quota.isAtLimit)
    }

    @Test
    fun `category scope label uses the user category tree`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget(scope = BudgetScope.Category("cat-food-out")))
        val vm = viewModel(kit)
        advanceUntilIdle()
        assertEquals("Comida > Restaurantes", (vm.state.value as BudgetListUiState.Content).items.single().scopeLabel)
    }

    @Test
    fun `error state then retry recovers`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.repository.failObserve = true
        val vm = viewModel(kit)
        advanceUntilIdle()
        assertIs<BudgetListUiState.Error>(vm.state.value)
        kit.repository.failObserve = false
        vm.reload()
        advanceUntilIdle()
        assertIs<BudgetListUiState.Empty>(vm.state.value)
    }

    @Test
    fun `without session shows error and no data`() = runTest(dispatcher) {
        val vm = viewModel(BudgetTestKit(), owner = null)
        advanceUntilIdle()
        val error = assertIs<BudgetListUiState.Error>(vm.state.value)
        assertEquals(SESSION_REQUIRED_MESSAGE, error.message)
    }

    @Test
    fun `pending scope or calendar change is flagged`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget().copy(pendingChange = PendingBudgetChange(null, null, LocalDate.of(2026, 11, 1))))
        val vm = viewModel(kit)
        advanceUntilIdle()
        assertTrue((vm.state.value as BudgetListUiState.Content).items.single().hasPendingChange)
    }
}
