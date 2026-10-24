package com.kipu.app.feature.budgets.presentation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetScope
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
class BudgetFormViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(kit: BudgetTestKit, budgetId: String? = null) = BudgetFormViewModel(
        SavedStateHandle(if (budgetId == null) emptyMap() else mapOf(BudgetFormViewModel.ARG_BUDGET_ID to budgetId)),
        fakeSession(), kit.repository, kit.create, kit.edit, FakeCategoryOptions(), TEST_ZONE,
    ).also {
        it.todayProvider = { TEST_TODAY }
        it.idGenerator = { "new-id" }
    }

    private fun BudgetFormViewModel.form() = (state.value as BudgetFormUiState.Content).form

    @Test
    fun `create saves a valid budget and emits Saved`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        val vm = viewModel(kit)
        advanceUntilIdle()
        vm.update { it.copy(name = "Comida", limitText = "500") }
        vm.save()
        advanceUntilIdle()
        assertEquals(BudgetFormEvent.Saved("new-id"), vm.events.first())
        val saved = kit.repository.budgets.value.single()
        assertEquals(50_000L, saved.limitMinor)
        assertEquals(1, kit.repository.periods.value.size)
    }

    @Test
    fun `invalid input shows field errors and does not write`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        val vm = viewModel(kit)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        val form = vm.form()
        assertTrue(BudgetFormField.NAME in form.fieldErrors)
        assertTrue(BudgetFormField.LIMIT in form.fieldErrors)
        assertTrue(kit.repository.budgets.value.isEmpty())
        assertFalse(form.isSaving)
    }

    @Test
    fun `inverted custom range is rejected with clear message`() = runTest(dispatcher) {
        val vm = viewModel(BudgetTestKit())
        advanceUntilIdle()
        vm.update {
            it.copy(
                name = "Viaje", limitText = "100", periodType = BudgetPeriodType.CUSTOM_RANGE,
                rangeStartText = "2026-10-20", rangeEndText = "2026-10-10",
            )
        }
        vm.save()
        advanceUntilIdle()
        assertEquals(
            "La fecha de fin no puede ser anterior a la de inicio.",
            vm.form().fieldErrors[BudgetFormField.RANGE_END],
        )
    }

    @Test
    fun `third active budget on Free shows quota dialog and keeps existing ones`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget("a", "A"))
        kit.seed(sampleBudget("b", "B"))
        val vm = viewModel(kit)
        advanceUntilIdle()
        vm.update { it.copy(name = "Tercero", limitText = "100") }
        vm.save()
        advanceUntilIdle()
        assertEquals(2, vm.form().quotaLimitDialog)
        assertEquals(2, kit.repository.budgets.value.size)
        vm.dismissQuotaDialog()
        assertNull(vm.form().quotaLimitDialog)
    }

    @Test
    fun `unavailable category is a field error`() = runTest(dispatcher) {
        val vm = viewModel(BudgetTestKit())
        advanceUntilIdle()
        vm.update { it.copy(name = "X", limitText = "10", scopeKind = BudgetScopeKind.CATEGORY, categoryId = "gone") }
        vm.save()
        advanceUntilIdle()
        assertNotNull(vm.form().fieldErrors[BudgetFormField.CATEGORY])
    }

    @Test
    fun `edit prefills and limit change applies to current period`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget(limit = 50_000L))
        val vm = viewModel(kit, "b1")
        advanceUntilIdle()
        assertEquals("500.00", vm.form().input.limitText)
        assertFalse(vm.form().scopeOrScheduleChanged)
        vm.update { it.copy(limitText = "600") }
        vm.save()
        advanceUntilIdle()
        assertEquals(60_000L, kit.repository.budgets.value.single().limitMinor)
        assertEquals(60_000L, kit.repository.periods.value.single().limitMinor)
    }

    @Test
    fun `edit scope flags next period notice and stores pending change`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        kit.seed(sampleBudget())
        val vm = viewModel(kit, "b1")
        advanceUntilIdle()
        vm.update { it.copy(scopeKind = BudgetScopeKind.CATEGORY, categoryId = "cat-food") }
        assertTrue(vm.form().scopeOrScheduleChanged)
        vm.save()
        advanceUntilIdle()
        val budget = kit.repository.budgets.value.single()
        assertEquals(BudgetScope.Total, budget.scope)
        assertEquals(BudgetScope.Category("cat-food"), budget.pendingChange?.scope)
        assertEquals(LocalDate.of(2026, 11, 1), budget.pendingChange?.effectiveFrom)
    }

    @Test
    fun `edit of missing or finished budget does not show a form`() = runTest(dispatcher) {
        val kit = BudgetTestKit()
        val missing = viewModel(kit, "nope")
        advanceUntilIdle()
        assertIs<BudgetFormUiState.NotFound>(missing.state.value)

        kit.seed(sampleBudget(status = com.kipu.app.feature.budgets.domain.BudgetStatus.FINISHED))
        val finished = viewModel(kit, "b1")
        advanceUntilIdle()
        assertIs<BudgetFormUiState.Error>(finished.state.value)
    }
}
