package com.kipu.app.feature.calendar.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.FakeFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Card
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.movements.domain.MovementHistoryQueryRepository
import com.kipu.app.feature.movements.domain.QueryMovementHistory
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryPage
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveCalendarMonthTest {
    private val user = UserId.generate()
    private val october = YearMonth.of(2026, 10)
    private val zone = ZoneOffset.UTC

    private class RecordingMovements(private val items: List<TransactionItem>) : MovementHistoryQueryRepository {
        val queries = mutableListOf<MovementHistoryQuery>()
        override suspend fun queryHistory(userId: String, query: MovementHistoryQuery): MovementHistoryPage {
            queries += query
            return MovementHistoryPage(items, null, false, MovementHistoryAccessDecision.Allowed)
        }
    }

    private fun movementOn(day: Int) = TransactionItem(
        Transaction(
            id = "m-$day", userId = user.value, type = MovementType.INCOME, amountMinor = 5_000L, currency = "PEN",
            sourceAccountId = "acc", occurredAt = LocalDate.of(2026, 10, day).atTime(10, 0).toInstant(zone).toEpochMilli(),
        ),
    )

    private fun <T : Any> readOnly(type: Class<T>, vararg readers: Pair<String, () -> Any?>): T {
        val byName = readers.toMap()
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            byName[method.name]?.invoke() ?: error("Escritura o lectura no esperada en el calendario: ${method.name}")
        } as T
    }

    private fun debt(id: String) = DebtSummary(
        debtId = id, userId = user.value, obligationType = DebtObligationType.PAYABLE, counterpartyName = "Banco",
        principalMinor = 90_000L, remainingPrincipalMinor = 90_000L, currencyCode = "PEN",
        openedOn = LocalDate.of(2026, 9, 1), dueDate = null, reminderLeadDays = null, notes = null,
        status = DebtLifecycleStatus.ACTIVE, syncState = "SYNCED", revision = 1L,
    )

    private fun useCase(movements: RecordingMovements): ObserveCalendarMonth {
        val card = CreditCard(
            id = CardId.generate(), userId = user, issuer = "BCP", network = CardNetwork.VISA, lastFourDigits = "1111",
            currency = Currency.PEN, creditLimitMinorUnits = 100_000L, billingDay = 15, dueDay = 5,
        )
        val instruments = object : FakeFinancialInstrumentsRepository() {
            override fun observeCards(activeOnly: Boolean): Flow<List<Card>> = flowOf(listOf(card))
        }
        val debts = readOnly<DebtRepository>(
            DebtRepository::class.java,
            "observeDebts" to { flowOf(listOf(debt("d1"))) },
            "observeInstallments" to {
                flowOf(listOf(DebtScheduleItem("i1", 1, LocalDate.of(2026, 10, 15), 30_000L)))
            },
        )
        val recurrences = readOnly<RecurrenceRepository>(RecurrenceRepository::class.java, "observeAll" to { flowOf(emptyList<Any>()) })
        return ObserveCalendarMonth(QueryMovementHistory(movements), instruments, debts, recurrences)
    }

    @Test
    fun `combina las cuatro fuentes y deja eventos del dia 15 por separado`() = runTest {
        val month = useCase(RecordingMovements(listOf(movementOn(15), movementOn(20))))(user.value, october, zone).first()

        val day15 = month.days.single { it.date == LocalDate.of(2026, 10, 15) }
        assertEquals(
            setOf(CalendarEventKind.MOVEMENT, CalendarEventKind.CARD_CLOSING, CalendarEventKind.DEBT_INSTALLMENT),
            day15.events.map { it.kind }.toSet(),
        )
        assertEquals(3, day15.events.size)
        assertTrue(month.days.any { it.date == LocalDate.of(2026, 10, 5) && it.events.single().kind == CalendarEventKind.CARD_DUE })
    }

    @Test
    fun `consulta el historial basico acotado al mes sin filtros avanzados`() = runTest {
        val movements = RecordingMovements(emptyList())

        useCase(movements)(user.value, october, zone).first()

        val query = movements.queries.single()
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli(), query.fromInclusive)
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli(), query.toExclusive)
        assertTrue(!query.requiresAdvancedAccess)
    }

    @Test
    fun `meses pasados se consultan igual que los actuales`() = runTest {
        val movements = RecordingMovements(emptyList())
        val case = useCase(movements)

        val past = case(user.value, YearMonth.of(2024, 1), zone).first()

        assertTrue(past.days.all { YearMonth.from(it.date) == YearMonth.of(2024, 1) })
        assertEquals(1, movements.queries.size)
    }
}
