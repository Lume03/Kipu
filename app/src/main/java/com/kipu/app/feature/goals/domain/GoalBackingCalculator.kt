package com.kipu.app.feature.goals.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.GoalEvent

data class GoalBackingPosition(
    val accountId: String,
    val confirmedBalance: Money,
    val reserved: Money,
    val available: Money,
    val shortfall: Money,
)

object GoalBackingCalculator {
    sealed interface ReservationValidation {
        data object Valid : ReservationValidation
        data object InvalidAmount : ReservationValidation
        data object CurrencyMismatch : ReservationValidation
        data object InsufficientBacking : ReservationValidation
    }

    fun position(
        accountId: String,
        confirmedBalance: Money,
        events: Iterable<GoalEvent>,
    ): GoalBackingPosition {
        require(accountId.isNotBlank()) { "Backing account id is required" }
        val reservedMinor = events.asSequence()
            .filter { it.backingAccountId == accountId && !it.transactionVoided }
            .fold(0L) { total, event ->
                require(event.delta.currency == confirmedBalance.currency) { "Backing event currency mismatch" }
                Math.addExact(total, event.delta.minorUnits)
            }
        val reserved = Money(reservedMinor, confirmedBalance.currency)
        val available = confirmedBalance - reserved
        val shortfall = if (reserved > confirmedBalance) reserved - confirmedBalance else Money.zero(confirmedBalance.currency)
        return GoalBackingPosition(accountId, confirmedBalance, reserved, available, shortfall)
    }

    fun validateReservation(
        amount: Money,
        freeBacking: Money,
        accountCurrency: Currency,
    ): ReservationValidation {
        if (amount.minorUnits <= 0L) return ReservationValidation.InvalidAmount
        if (amount.currency != accountCurrency || freeBacking.currency != accountCurrency) {
            return ReservationValidation.CurrencyMismatch
        }
        if (amount > freeBacking) return ReservationValidation.InsufficientBacking
        return ReservationValidation.Valid
    }
}
