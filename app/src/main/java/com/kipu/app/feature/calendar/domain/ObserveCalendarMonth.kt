package com.kipu.app.feature.calendar.domain

import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.movements.domain.QueryMovementHistory
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Consulta mensual local del calendario: solo lectura. Combina fuentes existentes (historial de
 * movimientos, tarjetas, deudas y cronogramas, recurrencias) y no escribe, no crea movimientos
 * ni ejecuta pagos. El historial básico es accesible en Free, así que los meses pasados no
 * requieren paywall.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCalendarMonth @Inject constructor(
    private val queryMovementHistory: QueryMovementHistory,
    private val instruments: FinancialInstrumentsRepository,
    private val debtRepository: DebtRepository,
    private val recurrenceRepository: RecurrenceRepository,
) {
    operator fun invoke(
        userId: String,
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<CalendarMonth> = combine(
        movements(userId, month, zone),
        instruments.observeCards(activeOnly = false),
        debtsWithInstallments(userId),
        recurrenceRepository.observeAll(userId),
    ) { movements, cards, (debts, installments), rules ->
        CalendarEventProjector.buildMonth(
            month,
            CalendarEventProjector.fromMovements(movements, month, zone) +
                CalendarEventProjector.fromCards(cards, month) +
                CalendarEventProjector.fromDebts(debts, installments, month) +
                CalendarEventProjector.fromRecurrences(rules, month),
        )
    }

    private fun movements(userId: String, month: YearMonth, zone: ZoneId): Flow<List<TransactionItem>> =
        queryMovementHistory.observeHistoryInvalidations()
            .onStart { emit(Unit) }
            .map { loadMonthMovements(userId, month, zone) }

    private suspend fun loadMonthMovements(userId: String, month: YearMonth, zone: ZoneId): List<TransactionItem> {
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val items = mutableListOf<TransactionItem>()
        var cursor: com.kipu.app.feature.movements.domain.model.MovementHistoryCursor? = null
        repeat(MAX_PAGES) {
            val page = queryMovementHistory(
                userId,
                MovementHistoryQuery(fromInclusive = from, toExclusive = to, limit = PAGE_SIZE, cursor = cursor),
            )
            items += page.items
            cursor = page.nextCursor
            if (!page.hasMore || cursor == null) return items
        }
        return items
    }

    private fun debtsWithInstallments(userId: String): Flow<Pair<List<DebtSummary>, Map<String, List<DebtScheduleItem>>>> =
        debtRepository.observeDebts(userId).flatMapLatest { debts ->
            val active = debts.filter { it.status == DebtLifecycleStatus.ACTIVE }
            if (active.isEmpty()) {
                flowOf(debts to emptyMap())
            } else {
                combine(active.map { debt -> debtRepository.observeInstallments(userId, debt.debtId).map { debt.debtId to it } }) {
                    debts to it.toMap()
                }
            }
        }

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 25
    }
}
