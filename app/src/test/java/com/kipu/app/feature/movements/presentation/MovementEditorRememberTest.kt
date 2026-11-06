package com.kipu.app.feature.movements.presentation

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import com.kipu.app.feature.categories.domain.usecase.SetMerchantCategoryPreference
import com.kipu.app.feature.movements.domain.*
import com.kipu.app.feature.movements.domain.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovementEditorRememberTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = UserId("61000000-0000-4000-8000-000000000001")
    private val merchant = MerchantCatalogEntry(MerchantId("merchant"), "Tambo", "tambo")
    private val category = Category(CategoryId("new"), null, null, CategoryOrigin.SYSTEM, true, categoryType = CategoryType.EXPENSE)
    private val oldCategory = Category(CategoryId("old"), null, null, CategoryOrigin.SYSTEM, true, categoryType = CategoryType.EXPENSE)
    private val repo = mockk<CategoriesRepository>()
    private val maintenance = mockk<MovementMaintenanceRepository>()
    private val movements = mockk<MovementRepository>()
    private val instruments = mockk<ObserveInstruments>()
    private val session = mockk<SessionCoordinator>()
    private val payload = MovementRevisionPayload(MovementType.EXPENSE, null, 100, "PEN", "account", categoryId = "old", merchantId = "merchant", occurredAt = 100)
    private val head = MovementRevisionHead("tx", owner.value, payload, MovementFinancialState.CONFIRMED, 1)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { session.currentOwner } returns LocalOwner(owner.value)
        every { instruments.observeAccounts(true) } returns flowOf(emptyList())
        every { repo.observePremiumVerified(owner) } returns flowOf(true)
        every { repo.observeCategories(owner) } returns flowOf(listOf(category, oldCategory))
        every { repo.observeCategoryPresentations(owner) } returns flowOf(listOf(CategoryPresentation(category.id, owner, "Grasa", "food", "#ffffff"),
            CategoryPresentation(oldCategory.id, owner, "Comida", "food", "#ffffff")))
        every { repo.observeMerchantCategoryPreferences(owner) } returns flowOf(emptyList())
        coEvery { repo.getMerchantById(merchant.id) } returns merchant
        coEvery { maintenance.getRevisionHead(owner.value, "tx") } returns head
        coEvery { maintenance.getConflictProposals(owner.value, "tx") } returns emptyList()
        coEvery { maintenance.getRevisionAudit(owner.value, "tx") } returns emptyList()
        coEvery { movements.getTransactionById(owner.value, "tx") } returns Transaction(
            id = "tx", userId = owner.value, type = MovementType.EXPENSE, amountMinor = 100,
            currency = "PEN", sourceAccountId = "account", categoryId = "old", occurredAt = 100)
        coEvery { maintenance.revise(owner.value, any()) } returns MovementMutationResult.Success(head, false)
        coEvery { repo.saveMerchantCategoryPreference(any(), any()) } returns Result.success(Unit)
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun create() = MovementEditorViewModel(SavedStateHandle(mapOf("transactionId" to "tx")), session,
        ReviseTransaction(maintenance), maintenance, movements, instruments, ObserveCategories(repo), repo, SetMerchantCategoryPreference(repo))

    @Test fun `rejected correction restores editable draft against current official revision`() = runTest(dispatcher) {
        coEvery { maintenance.getRevisionAudit(owner.value, "tx") } returns listOf(
            MovementRevisionAudit(3, "CORRECTION_REJECTED", 200, "REQUEST_HASH_MISMATCH",
                """{"id":"tx","type":"EXPENSE","amount_minor":141,"currency_code":"PEN","source_account_id":"account","category_id":"old","occurred_at":"1970-01-01T00:00:00.100Z","note":"retained","status":"REVISED","revision":2}"""))
        val vm = create(); advanceUntilIdle()
        assertEquals(100L, vm.uiState.value.initialAmountMinor)
        assertEquals("1.41", vm.uiState.value.amountText)
        assertEquals("retained", vm.uiState.value.note)
        assertEquals(1L, vm.uiState.value.expectedRevision)
        vm.onSave(); advanceUntilIdle()
        coVerify { maintenance.revise(owner.value, match { it.payload.amountMinor == 141L && it.expectedRevision == 1L }) }
    }

    @Test fun `changed category offers and saves preference after revision`() = runTest(dispatcher) {
        val vm = create(); advanceUntilIdle()
        assertNull(vm.uiState.value.rememberCategory)
        vm.onCategorySelected(vm.uiState.value.availableCategories.first { it.id == "new" }); advanceUntilIdle()
        assertEquals("Recordar Grasa para Tambo", vm.uiState.value.rememberCategory?.label)
        assertEquals(true, vm.uiState.value.rememberCategory?.checked)
        vm.onSave(); advanceUntilIdle()
        assertTrue(vm.uiState.value.isSuccess)
        coVerify(exactly = 1) { repo.saveMerchantCategoryPreference(match { it.merchantId == merchant.id && it.categoryId == category.id }, null) }
    }

    @Test fun `changing only merchant offers existing category for the new merchant`() = runTest(dispatcher) {
        val vm = create(); advanceUntilIdle()
        vm.onMerchantSelected(MerchantCatalogEntry(MerchantId("other"), "Otro", "otro")); advanceUntilIdle()
        assertEquals("Recordar Comida para Otro", vm.uiState.value.rememberCategory?.label)
    }

    @Test fun `rejected revision never saves preference`() = runTest(dispatcher) {
        coEvery { maintenance.revise(owner.value, any()) } returns MovementMutationResult.Rejected("FAIL")
        val vm = create(); advanceUntilIdle()
        vm.onCategorySelected(vm.uiState.value.availableCategories.first { it.id == "new" }); advanceUntilIdle()
        vm.onSave(); advanceUntilIdle()
        assertFalse(vm.uiState.value.isSuccess)
        coVerify(exactly = 0) { repo.saveMerchantCategoryPreference(any(), any()) }
    }

    @Test fun `unchecked preference is not learned after revision`() = runTest(dispatcher) {
        val vm = create(); advanceUntilIdle()
        vm.onCategorySelected(vm.uiState.value.availableCategories.first { it.id == "new" }); advanceUntilIdle()
        vm.onRememberCategoryChanged(false)
        vm.onSave(); advanceUntilIdle()
        assertTrue(vm.uiState.value.isSuccess)
        coVerify(exactly = 0) { repo.saveMerchantCategoryPreference(any(), any()) }
    }
    @Test fun `income editor excludes expense choices but preserves its historical expense association`() = runTest(dispatcher) {
        val incomeCategory = category.copy(id = CategoryId("salary"), categoryType = CategoryType.INCOME)
        val shared = category.copy(id = CategoryId("shared"), categoryType = CategoryType.GENERAL)
        every { repo.observeCategories(owner) } returns flowOf(listOf(category, oldCategory, incomeCategory, shared))
        coEvery { maintenance.getRevisionHead(owner.value, "tx") } returns head.copy(payload = payload.copy(type = MovementType.INCOME))
        coEvery { movements.getTransactionById(owner.value, "tx") } returns Transaction(
            id = "tx", userId = owner.value, type = MovementType.INCOME, amountMinor = 100,
            currency = "PEN", sourceAccountId = "account", categoryId = "old", occurredAt = 100)
        val vm = create(); advanceUntilIdle()
        assertEquals(setOf("salary", "shared"), vm.uiState.value.selectableCategories.map { it.id }.toSet())
        assertEquals("old", vm.uiState.value.selectedCategoryId)
        vm.onCategorySelected(vm.uiState.value.availableCategories.first { it.id == "new" })
        assertEquals("old", vm.uiState.value.selectedCategoryId)
        vm.onCategorySelected(vm.uiState.value.selectableCategories.first { it.id == "salary" })
        assertEquals("salary", vm.uiState.value.selectedCategoryId)
    }

}
