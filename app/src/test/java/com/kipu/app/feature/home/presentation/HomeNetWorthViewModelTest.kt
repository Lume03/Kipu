package com.kipu.app.feature.home.presentation

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.FakeFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.home.domain.ObserveNetWorthSummary
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeNetWorthViewModelTest {
    private val user = UserId.generate()

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class FakeSession(initial: LocalAccess) : SessionCoordinator {
        override val remoteSession: StateFlow<RemoteSession> = MutableStateFlow(RemoteSession.Absent)
        override val localAccess: MutableStateFlow<LocalAccess> = MutableStateFlow(initial)
        override val currentOwner: LocalOwner? = null
        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }

    private fun available() = LocalAccess.Available(user.value, RemoteSession.Absent)

    private fun debtRepository(source: () -> Flow<List<com.kipu.app.feature.debts.domain.model.DebtSummary>>): DebtRepository =
        Proxy.newProxyInstance(DebtRepository::class.java.classLoader, arrayOf(DebtRepository::class.java)) { _, method, _ ->
            if (method.name == "observeDebts") source() else error("No usado: ${method.name}")
        } as DebtRepository

    private fun viewModel(
        session: SessionCoordinator,
        accounts: List<Account> = emptyList(),
        debts: () -> Flow<List<com.kipu.app.feature.debts.domain.model.DebtSummary>> = { flowOf(emptyList()) },
    ) = HomeNetWorthViewModel(
        ObserveNetWorthSummary(
            ObserveFinancialDashboard(
                object : FakeFinancialInstrumentsRepository() {
                    override fun observeAccounts(activeOnly: Boolean): Flow<List<Account>> = flowOf(accounts)
                    override fun observeAccountBalance(accountId: AccountId): Flow<Money> = flowOf(Money(0L, Currency.PEN))
                },
            ),
            debtRepository(debts),
        ),
        session,
    )

    private fun account() = Account(
        id = AccountId.generate(), userId = user, alias = "BCP", type = AccountType.SAVINGS, currency = Currency.PEN,
        initialBalance = Money(0L, Currency.PEN), openedAt = Instant.now(),
    )

    @Test
    fun `sin fuentes muestra Empty y no cero`() = runTest {
        val vm = viewModel(FakeSession(available()))
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()

        assertEquals(NetWorthUiState.Empty, vm.state.value)
        job.cancel()
    }

    @Test
    fun `con una cuenta en cero confirma Content con cero`() = runTest {
        val vm = viewModel(FakeSession(available()), accounts = listOf(account()))
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()

        val content = vm.state.value as NetWorthUiState.Content
        assertEquals(Money(0L, Currency.PEN), content.byCurrency.single().netWorth)
        job.cancel()
    }

    @Test
    fun `error de fuente muestra Error y reintentar recupera`() = runTest {
        val calls = AtomicInteger(0)
        val vm = viewModel(
            FakeSession(available()),
            accounts = listOf(account()),
            debts = {
                if (calls.getAndIncrement() == 0) flow { throw IllegalStateException("boom") } else flowOf(emptyList())
            },
        )
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()
        assertTrue(vm.state.value is NetWorthUiState.Error)

        vm.retry()
        runCurrent()

        assertTrue(vm.state.value is NetWorthUiState.Content)
        job.cancel()
    }

    @Test
    fun `sin propietario muestra Error de sesion`() = runTest {
        val vm = viewModel(FakeSession(LocalAccess.NoOwner))
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()

        assertTrue(vm.state.value is NetWorthUiState.Error)
        job.cancel()
    }

    @Test
    fun `acceso protegido mantiene Loading sin exponer datos`() = runTest {
        val vm = viewModel(FakeSession(LocalAccess.Protected(user.value, "LOCKED")), accounts = listOf(account()))
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()

        assertEquals(NetWorthUiState.Loading, vm.state.value)
        job.cancel()
    }
}
