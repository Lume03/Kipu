package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.CardInstallment
import com.kipu.app.feature.accounts.domain.model.CardInstallmentsSummary
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class ObserveCardInstallments @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
    private val clock: Clock,
) {
    operator fun invoke(cardId: CardId): Flow<CardInstallmentsSummary> = combine(
        repository.observeCardInstallments(cardId), repository.observeCardById(cardId), cardCivilDate(clock),
    ) { installments, card, today ->
        CardInstallmentsSummary.from(installments.map { it.atDate(today) }, installments.firstOrNull()?.principal?.currency ?: card?.currency ?: Currency.PEN)
    }
}

/** Owner-scoped read model for KIP-19; includes overdue obligations and dues within seven days. */
class ObserveUpcomingCardInstallments @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
    private val clock: Clock,
) {
    operator fun invoke(userId: String): Flow<List<CardInstallment>> = combine(
        repository.observeUpcomingCardInstallments(userId), cardCivilDate(clock),
    ) { installments, today ->
        installments.filter { it.outstanding.minorUnits > 0 && it.dueDate <= today.plusDays(7) }.map { it.atDate(today) }
            .sortedWith(compareBy({ it.dueDate }, { it.cardId.value }, { it.purchaseId }, { it.installmentNumber }))
    }
}

/** Date-based labels also refresh while an unchanged Room result stays on screen overnight. */
private fun cardCivilDate(clock: Clock): Flow<LocalDate> = flow {
    val zone = ZoneId.of("America/Lima")
    while (true) {
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        emit(today)
        delay(Duration.between(now, today.plusDays(1).atStartOfDay(zone).toInstant()).toMillis().coerceAtLeast(1))
    }
}
