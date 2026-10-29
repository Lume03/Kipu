package com.kipu.app.feature.accounts.presentation

import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.FakeFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.MutableVerifiedPlanAccess
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsPlanQuotaViewModelTest {
    @Test
    fun `dashboard and form state follow verified premium and free quotas`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repo = FakeFinancialInstrumentsRepository().apply { activeComputableCount = 4 }
            val access = MutableVerifiedPlanAccess(false)
            val session = mockk<SessionCoordinator> { every { currentOwner } returns null }
            val vm = AccountsViewModel(
                observeFinancialDashboard = ObserveFinancialDashboard(repo, access),
                observeInstruments = ObserveInstruments(repo, access),
                createLiquidAccountUseCase = mockk(), recordOpeningAdjustmentUseCase = mockk(),
                updateInstrumentAppearanceUseCase = mockk(), archiveInstrumentUseCase = mockk(),
                reactivateInstrumentUseCase = mockk(), registerDebitCardUseCase = mockk(),
                registerCreditCardUseCase = mockk(), checkCardDuplicateUseCase = mockk(),
                payCreditCardUseCase = mockk(), confirmCreditPurchaseUseCase = mockk(),
                getReferentialRatesUseCase = mockk(), updatePersonalTeaUseCase = mockk(),
                financialInstrumentsRepository = repo, observeCategories = mockk(), sessionCoordinator = session,
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.instrumentsUiState.collect() }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.dashboardUiState.collect() }
            testScheduler.runCurrent()
            assertEquals(4, vm.instrumentsUiState.value.maxFreeQuota)
            assertEquals(4, vm.dashboardUiState.value.dashboardData?.maxFreeQuota)
            access.premium.value = true
            testScheduler.runCurrent()
            assertEquals(0, vm.instrumentsUiState.value.maxFreeQuota)
            assertEquals(0, vm.dashboardUiState.value.dashboardData?.maxFreeQuota)
            assertEquals(4, vm.instrumentsUiState.value.activeComputableCount)
            access.premium.value = false
            testScheduler.runCurrent()
            assertEquals(4, vm.instrumentsUiState.value.maxFreeQuota)
            assertEquals(4, vm.dashboardUiState.value.dashboardData?.maxFreeQuota)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
