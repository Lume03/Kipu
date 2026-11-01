package com.kipu.app.feature.setup

import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.usecase.CreateLiquidAccount
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.usecase.EnsureInitialCategoryCatalog
import com.kipu.app.feature.plans.domain.ConfirmPlanSelection
import com.kipu.app.feature.plans.domain.PlanPreferencesRepository
import com.kipu.app.feature.plans.domain.model.PlanSelection
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SetupFinalizerTest {
    private val accountFactory = registerInstanceFactory { AccountId("61000000-0000-4000-8000-000000000020") }
    private val operationFactory = registerInstanceFactory { OperationId("61000000-0000-4000-8000-000000000030") }
    private val ownerFactory = registerInstanceFactory { UserId("61000000-0000-4000-8000-000000000001") }
    @org.junit.After fun releaseFactories() {
        accountFactory.deregister()
        operationFactory.deregister()
        ownerFactory.deregister()
    }
    @Test fun `process restart after account creation does not duplicate account or opening balance`() = runTest {
        val owner = "00000000-0000-0000-0000-0000000000aa"
        val create = mockk<CreateLiquidAccount>()
        val ensure = mockk<EnsureInitialCategoryCatalog>()
        val categories = mockk<CategoriesRepository>()
        val confirm = mockk<ConfirmPlanSelection>()
        val preferences = mockk<PlanPreferencesRepository>(relaxed = true)
        val instruments = mockk<FinancialInstrumentsRepository>()
        val saved = slot<Account>()
        every { instruments.observeAccountById(any()) } answers { flowOf(if (saved.isCaptured) saved.captured else null) }
        coEvery { create(capture(saved), any()) } answers { Result.success(saved.captured) }
        coEvery { ensure(any()) } returns Result.success(Unit)
        coEvery { confirm(any(), any()) } throws IllegalStateException("Plan write interrupted")
        val draft = SetupDraft(account = SetupAccountDraft(kind = SetupAccountKind.CASH, preset = AccountPreset.CASH, alias = "Efectivo", balanceText = "150"))
        fun finalizer() = SetupFinalizer(create, ensure, categories, confirm, preferences, instruments)
        assertTrue(finalizer().apply(owner, draft, mutableSetOf()).isFailure)
        coEvery { confirm(any(), any()) } returns PlanSelection.FREE
        assertTrue(finalizer().apply(owner, draft, mutableSetOf()).isSuccess)
        coVerify(exactly = 1) { create(any(), any()) }
        assertEquals(15000L, saved.captured.initialBalance.minorUnits)
        assertEquals(owner, saved.captured.userId.value)
    }
}
