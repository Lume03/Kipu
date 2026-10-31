package com.kipu.app.feature.accounts.presentation.instruments

import androidx.lifecycle.SavedStateHandle
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.domain.usecase.GetReferentialRates
import com.kipu.app.feature.accounts.presentation.components.*
import com.kipu.app.feature.categories.data.sync.CategorySyncScheduler
import com.kipu.app.feature.categories.domain.MerchantPreferenceResolution
import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.categories.domain.usecase.*
import io.mockk.*
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstallmentSimulatorViewModelTest {
    @After fun tearDown() = Dispatchers.resetMain()
    private val owner = UserId("61000000-0000-4000-8000-000000000001")
    private val category = CategoryId("food")
    private val candidate = PurchaseCandidate("candidate", CardId("61000000-0000-4000-8000-000000000010"), Money(120000, Currency.PEN),
        "Tienda", Instant.parse("2026-06-25T12:00:00Z"), suggestedInstallments = 6)
    private fun create(handle: SavedStateHandle = SavedStateHandle()): InstallmentSimulatorViewModel {
        val observe = mockk<ObserveCategories>()
        every { observe(owner) } returns flowOf(listOf(CategoryItem(Category(category, owner, null, CategoryOrigin.CUSTOM, true,
            categoryType = CategoryType.EXPENSE), CategoryPresentation(category, owner, "Comida", "restaurant", "#008000"))))
        val ensure = mockk<EnsureInitialCategoryCatalog>()
        coEvery { ensure(owner) } returns Result.success(Unit)
        val preference = mockk<ResolvePreferredCategory>()
        coEvery { preference(owner, any(), any(), any()) } returns MerchantPreferenceResolution.Preferred(category)
        val rates = mockk<GetReferentialRates>()
        coEvery { rates() } returns Result.success(emptyList())
        val session = mockk<SessionCoordinator> {
            every { currentOwner } returns LocalOwner(owner.value)
            every { localAccess } returns MutableStateFlow(LocalAccess.Available(owner.value, RemoteSession.Absent))
        }
        return InstallmentSimulatorViewModel(handle, session, observe, ensure, mockk(), preference,
            mockk<CategorySyncScheduler>(relaxed = true), rates)
    }
    @Test fun `shared category and merchant selections preserve confirmed financing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create(); vm.initialize(candidate); runCurrent()
        assertEquals(listOf("Comida"), vm.state.value.categories.map { it.name })
        val merchant = MerchantCatalogEntry(MerchantId("merchant"), "Comercio elegido", "comercio elegido", defaultCategoryId = category)
        vm.selectMerchant(merchant); runCurrent()
        vm.updateDraft(CreditPurchaseDraft(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, "6", "45", PurchaseRateSource.USER_ENTERED))
        val state = vm.state.value
        assertEquals("merchant", state.merchantId)
        assertEquals("Comercio elegido", state.merchantName)
        assertEquals("food", state.category?.id)
        assertEquals(4500, validateCreditPurchaseDraft(requireNotNull(state.draft)).terms?.teaBps)
        assertEquals(6, validateCreditPurchaseDraft(requireNotNull(state.draft)).terms?.installmentsCount)
    }
    @Test fun `explicit category remains selected when merchant changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create(); vm.initialize(candidate); runCurrent()
        vm.selectCategory(vm.state.value.categories.single())
        vm.selectMerchant(MerchantCatalogEntry(MerchantId("other"), "Otro comercio", "otro")); runCurrent()
        assertEquals("food", vm.state.value.categoryId)
        assertFalse(vm.state.value.resolvingMerchant)
        vm.setProvisionalMerchant("Bodega local")
        assertEquals("Bodega local", vm.state.value.merchantName)
        assertNull(vm.state.value.merchantId)
        assertEquals("food", vm.state.value.categoryId)
    }
    @Test fun `saved simulator state restores classification and accepted terms`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val vm = create(handle); vm.initialize(candidate); runCurrent()
        vm.selectCategory(vm.state.value.categories.single()); vm.setProvisionalMerchant("Bodega")
        val draft = CreditPurchaseDraft(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, "36", "45", PurchaseRateSource.USER_ENTERED)
        vm.updateDraft(draft)
        val restored = create(handle); restored.initialize(candidate); runCurrent()
        assertEquals("Bodega", restored.state.value.merchantName)
        assertEquals("food", restored.state.value.categoryId)
        assertEquals(draft, restored.state.value.draft)
    }
}
