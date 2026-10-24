package com.kipu.app.feature.calendar.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventProjectorTest {
    private val october = YearMonth.of(2026, 10)
    private val zone = ZoneOffset.UTC
    private val user = UserId.generate()

    private fun movement(id: String, day: Int, status: TransactionStatus = TransactionStatus.CONFIRMED) = TransactionItem(
        transaction = Transaction(
            id = id, userId = user.value, type = MovementType.INCOME, amountMinor = 10_000L, currency = "PEN",
            sourceAccountId = "acc", occurredAt = LocalDate.of(2026, 10, day).atTime(12, 0).toInstant(zone).toEpochMilli(),
            status = status,
        ),
        merchantName = "Sueldo",
    )

    private fun card(billingDay: Int = 15, dueDay: Int = 5, archived: Boolean = false) = CreditCard(
        id = CardId.generate(), userId = user, issuer = "BCP", network = CardNetwork.VISA, lastFourDigits = "1111",
        currency = Currency.PEN, creditLimitMinorUnits = 100_000L, billingDay = billingDay, dueDay = dueDay,
        isArchived = archived,
    )

    private fun debt(id: String, dueDate: LocalDate? = null, status: DebtLifecycleStatus = DebtLifecycleStatus.ACTIVE) = DebtSummary(
        debtId = id, userId = user.value, obligationType = DebtObligationType.PAYABLE, counterpartyName = "Banco",
        principalMinor = 90_000L, remainingPrincipalMinor = 90_000L, currencyCode = "PEN",
        openedOn = LocalDate.of(2026, 9, 1), dueDate = dueDate, reminderLeadDays = null, notes = null,
        status = status, syncState = "SYNCED", revision = 1L,
    )

    private fun installment(id: String, number: Int, due: LocalDate, status: String = "PENDING") =
        DebtScheduleItem(id = id, installmentNumber = number, dueDate = due, principalMinor = 30_000L, status = status)

    private fun rule(
        id: String = "r1",
        start: LocalDate = LocalDate.of(2026, 9, 10),
        next: LocalDate = LocalDate.of(2026, 10, 10),
        status: RecurrenceStatus = RecurrenceStatus.ACTIVE,
        end: LocalDate? = null,
    ) = RecurrenceRule(
        id = id, userId = user.value,
        templateTransaction = JsonObject(
            mapOf(
                "concept" to JsonPrimitive("Alquiler"), "amount_minor" to JsonPrimitive(120_000L),
                "currency_code" to JsonPrimitive("PEN"),
            ),
        ),
        amountMode = "FIXED", frequency = "MONTHLY", intervalCount = 1L, customIntervalUnit = null,
        startDate = start, endDate = end, nextDueDate = next, reminderLeadDays = 2, status = status, revision = 1L,
        syncState = RecurrenceSyncState.SYNCED, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun `dos eventos el mismo dia se listan por separado`() {
        val events = CalendarEventProjector.fromMovements(listOf(movement("m1", 15), movement("m2", 15)), october, zone) +
            CalendarEventProjector.fromCards(listOf(card(billingDay = 15)), october)

        val day = CalendarEventProjector.buildMonth(october, events).days.single { it.date == LocalDate.of(2026, 10, 15) }

        assertEquals(3, day.events.size)
        assertEquals(3, day.events.map { it.id }.toSet().size)
    }

    @Test
    fun `realizado y previsto se distinguen y el realizado va primero`() {
        val events = CalendarEventProjector.fromRecurrences(listOf(rule(next = LocalDate.of(2026, 10, 10))), october) +
            CalendarEventProjector.fromMovements(listOf(movement("m1", 10)), october, zone)

        val day = CalendarEventProjector.buildMonth(october, events).days.single()

        assertEquals(listOf(CalendarEventStatus.REALIZED, CalendarEventStatus.PLANNED), day.events.map { it.status })
        assertEquals(listOf(CalendarEventKind.MOVEMENT, CalendarEventKind.RECURRENCE), day.events.map { it.kind })
    }

    @Test
    fun `movimientos anulados o de otro mes no aparecen`() {
        val voided = movement("v", 3, TransactionStatus.VOIDED)
        val result = CalendarEventProjector.fromMovements(listOf(voided, movement("ok", 4)), october, zone)
        val other = CalendarEventProjector.fromMovements(listOf(movement("ok", 4)), YearMonth.of(2026, 9), zone)

        assertEquals(listOf("movement:ok"), result.map { it.id })
        assertTrue(other.isEmpty())
    }

    @Test
    fun `tarjeta emite cierre y vencimiento previstos con dia efectivo en mes corto`() {
        val feb = YearMonth.of(2027, 2)
        val events = CalendarEventProjector.fromCards(listOf(card(billingDay = 31, dueDay = 5)), feb)

        assertEquals(LocalDate.of(2027, 2, 28), events.single { it.kind == CalendarEventKind.CARD_CLOSING }.date)
        assertEquals(LocalDate.of(2027, 2, 5), events.single { it.kind == CalendarEventKind.CARD_DUE }.date)
        assertTrue(events.all { it.status == CalendarEventStatus.PLANNED })
    }

    @Test
    fun `tarjeta archivada no genera eventos`() {
        assertTrue(CalendarEventProjector.fromCards(listOf(card(archived = true)), october).isEmpty())
    }

    @Test
    fun `cuota pendiente de deuda aparece y la pagada no`() {
        val debts = listOf(debt("d1"))
        val installments = mapOf(
            "d1" to listOf(
                installment("i1", 1, LocalDate.of(2026, 10, 20)),
                installment("i2", 2, LocalDate.of(2026, 10, 25), status = "PAID"),
                installment("i3", 3, LocalDate.of(2026, 11, 20)),
            ),
        )

        val events = CalendarEventProjector.fromDebts(debts, installments, october)

        assertEquals(listOf("debt-installment:i1"), events.map { it.id })
        assertEquals(Money(30_000L, Currency.PEN), events.single().amount)
        assertTrue(events.single().isSourceAvailable)
    }

    @Test
    fun `cuota cuya deuda ya no existe se muestra como no disponible`() {
        val events = CalendarEventProjector.fromDebts(
            debts = emptyList(),
            installmentsByDebtId = mapOf("gone" to listOf(installment("i1", 1, LocalDate.of(2026, 10, 20)))),
            month = october,
        )

        val event = events.single()
        assertFalse(event.isSourceAvailable)
        assertEquals(CalendarEventSource.Debt("gone"), event.source)
        assertEquals(null, event.amount)
    }

    @Test
    fun `deuda activa sin cronograma aparece en su fecha de vencimiento`() {
        val events = CalendarEventProjector.fromDebts(
            debts = listOf(debt("d1", dueDate = LocalDate.of(2026, 10, 30)), debt("d2", dueDate = LocalDate.of(2026, 10, 30), status = DebtLifecycleStatus.SETTLED)),
            installmentsByDebtId = emptyMap(),
            month = october,
        )

        assertEquals(listOf("debt-due:d1"), events.map { it.id })
    }

    @Test
    fun `recurrencia activa proyecta solo desde su proxima fecha y respeta fin`() {
        val events = CalendarEventProjector.fromRecurrences(
            listOf(rule(next = LocalDate.of(2026, 10, 10)), rule(id = "ended", end = LocalDate.of(2026, 9, 30), next = LocalDate.of(2026, 9, 10))),
            october,
        )

        assertEquals(listOf(LocalDate.of(2026, 10, 10)), events.map { it.date })
        assertEquals("Alquiler", events.single().title)
        assertEquals(Money(120_000L, Currency.PEN), events.single().amount)
    }

    @Test
    fun `recurrencia pausada o cancelada no se proyecta`() {
        val events = CalendarEventProjector.fromRecurrences(
            listOf(rule(status = RecurrenceStatus.PAUSED), rule(id = "c", status = RecurrenceStatus.CANCELLED)),
            october,
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `mes sin eventos queda vacio`() {
        assertTrue(CalendarEventProjector.buildMonth(october, emptyList()).isEmpty)
    }
}
