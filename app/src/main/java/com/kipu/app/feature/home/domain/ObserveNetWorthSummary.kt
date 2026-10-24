package com.kipu.app.feature.home.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Compone el patrimonio reutilizando los casos de uso/repositorios existentes:
 * saldos de cuentas y deuda de tarjetas vienen de [ObserveFinancialDashboard];
 * cuentas por cobrar y por pagar, de [DebtRepository].
 */
class ObserveNetWorthSummary @Inject constructor(
    private val observeFinancialDashboard: ObserveFinancialDashboard,
    private val debtRepository: DebtRepository,
) {
    operator fun invoke(userId: String): Flow<NetWorthSummary> =
        combine(observeFinancialDashboard(), debtRepository.observeDebts(userId)) { dashboard, debts ->
            // Una moneda sin cuentas (ni saldo) es «sin datos», no un cero confirmado.
            fun accountTotal(total: Money) = total.takeIf { money ->
                money.minorUnits != 0L || dashboard.liquidAccounts.any { it.account.currency == money.currency }
            }
            val activeDebts = debts.filter { it.status == DebtLifecycleStatus.ACTIVE }
            fun remaining(type: DebtObligationType) = activeDebts
                .filter { it.obligationType == type }
                .map { Money(it.remainingPrincipalMinor, Currency.fromCode(it.currencyCode)) }

            NetWorthCalculator.calculate(
                NetWorthInputs(
                    accountBalances = listOfNotNull(accountTotal(dashboard.totalPen), accountTotal(dashboard.totalUsd)),
                    receivables = remaining(DebtObligationType.RECEIVABLE),
                    payables = remaining(DebtObligationType.PAYABLE),
                    cardDebts = dashboard.creditCards.map { it.debt },
                ),
            )
        }
}
