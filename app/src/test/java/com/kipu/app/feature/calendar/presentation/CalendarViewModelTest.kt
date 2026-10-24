package com.kipu.app.feature.calendar.presentation

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.FakeFinancialInstrumentsRepository
import com.kipu.app.feature.calendar.domain.ObserveCalendarMonth
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.movements.domain.MovementHistoryQueryRepository
import com.kipu.app.feature.movements.domain.QueryMovementHistory
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryPage
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
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
class CalendarViewModelTest {
    private val user = UserId.generate()
    private val clock = Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ZoneId.systemDefault())

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

    private fun <T : Any> reader(type: Class<T>, vararg readers: Pair<String, () -> Any?>): T {
        val byName = readers.toMap()
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            byName[method.name]?.invoke() ?: error("No esperado: ${method.name}")
        } as T
    }

    private fun viewModel(session: SessionCoordinator, debts: () -> Flow<List<Any>> = { flowOf(emptyList()) }): CalendarViewModel {
        val movements = object : MovementHistoryQueryRepository {
            override suspend fun queryHistory(userId: String, query: MovementHistoryQuery) =
                MovementHistoryPage(emptyList(), null, false, MovementHistoryAccessDecision.Allowed)
        }
        val useCase = ObserveCalendarMonth(
            QueryMovementHistory(movements),
            FakeFinancialInstrumentsRepository(),
            reader(DebtRepository::class.java, "observeDebts" to debts),
            reader(RecurrenceRepository::class.java, "observeAll" to { flowOf(emptyList<Any>()) }),
        )
        return CalendarViewModel(useCase, session, clock)
    }

    @Test
    fun `inicia en el mes actual y un mes sin eventos es Empty`() = runTest {
        val vm = viewModel(FakeSession(available()))
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()

        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        assertEquals(CalendarContent.Empty, vm.state.value.content)
        job.cancel()
    }

    @Test
    fun `navega a meses pasados sin restriccion`() = runTest {
        val vm = viewModel(FakeSession(available()))
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()

        repeat(30) { vm.previousMonth() }
        runCurrent()
        assertEquals(YearMonth.of(2024, 4), vm.state.value.month)
        assertEquals(CalendarContent.Empty, vm.state.value.content)

        vm.goToToday()
        runCurrent()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        job.cancel()
    }

    @Test
    fun `error de fuente muestra Error y reintentar recupera`() = runTest {
        var calls = 0
        val vm = viewModel(FakeSession(available()), debts = {
            if (calls++ == 0) flow { throw IllegalStateException("boom") } else flowOf(emptyList())
        })
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()
        assertTrue(vm.state.value.content is CalendarContent.Error)

        vm.retry()
        runCurrent()

        assertTrue(vm.state.value.content !is CalendarContent.Error)
        job.cancel()
    }

    @Test
    fun `sin propietario muestra error de sesion`() = runTest {
        val vm = viewModel(FakeSession(LocalAccess.NoOwner))
        val job = backgroundScope.launch { vm.state.collect { } }
        runCurrent()

        assertTrue(vm.state.value.content is CalendarContent.Error)
        job.cancel()
    }
}
