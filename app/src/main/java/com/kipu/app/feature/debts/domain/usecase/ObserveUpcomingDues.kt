package com.kipu.app.feature.debts.domain.usecase

import com.kipu.app.feature.accounts.domain.usecase.ObserveUpcomingCardInstallments
import com.kipu.app.feature.accounts.domain.usecase.observeLimaCivilDate
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.UpcomingDuesSnapshot
import com.kipu.app.feature.debts.domain.model.buildUpcomingDues
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A read-only calendar; no reminder scheduling or financial write occurs here. */
class ObserveUpcomingDues @Inject constructor(
    private val debts: DebtRepository,
    private val cards: ObserveUpcomingCardInstallments,
    private val clock: Clock,
) {
    operator fun invoke(userId: String): Flow<UpcomingDuesSnapshot> = combine(
        debts.observeDebts(userId), debts.observeDueBalances(userId), cards(userId, includeAllDues = true),
        observeLimaCivilDate(clock),
    ) { summaries, balances, installments, today -> buildUpcomingDues(
        summaries.filter { it.userId == userId }, balances, installments, today,
    ) }
}
