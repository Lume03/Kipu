package com.kipu.app.feature.home.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money

/**
 * Patrimonio de una moneda: activos reales + cuentas por cobrar - pasivos pendientes.
 * La línea de crédito disponible y las reservas de metas no forman parte de ninguno de
 * los componentes (la reserva es una asignación del activo de respaldo).
 */
data class CurrencyNetWorth(
    val currency: Currency,
    val realAssets: Money,
    val receivables: Money,
    val liabilities: Money,
) {
    init {
        require(realAssets.currency == currency && receivables.currency == currency && liabilities.currency == currency) {
            "Todos los componentes deben estar en $currency"
        }
    }

    val netWorth: Money get() = realAssets + receivables - liabilities
}

/** Resultado del resumen patrimonial; «sin datos» es distinto de un cero confirmado. */
sealed interface NetWorthSummary {
    /** No existe ninguna fuente (cuentas, deudas o tarjetas): el patrimonio es desconocido, no cero. */
    data object NoData : NetWorthSummary

    /** Un patrimonio por moneda; no se convierte entre monedas. */
    data class Available(val byCurrency: List<CurrencyNetWorth>) : NetWorthSummary
}

data class NetWorthInputs(
    /** Saldos de cuentas líquidas (cada cuenta contada una sola vez). */
    val accountBalances: List<Money>,
    /** Principal pendiente de cuentas por cobrar activas. */
    val receivables: List<Money>,
    /** Principal pendiente de deudas por pagar activas. */
    val payables: List<Money>,
    /** Deuda vigente de tarjetas de crédito. */
    val cardDebts: List<Money>,
)

object NetWorthCalculator {
    fun calculate(inputs: NetWorthInputs): NetWorthSummary {
        val hasSources = inputs.accountBalances.isNotEmpty() || inputs.receivables.isNotEmpty() ||
            inputs.payables.isNotEmpty() || inputs.cardDebts.isNotEmpty()
        if (!hasSources) return NetWorthSummary.NoData

        val currencies = (
            inputs.accountBalances + inputs.receivables + inputs.payables + inputs.cardDebts
            ).map { it.currency }.toSortedSet()

        return NetWorthSummary.Available(
            currencies.map { currency ->
                CurrencyNetWorth(
                    currency = currency,
                    realAssets = inputs.accountBalances.sum(currency),
                    receivables = inputs.receivables.sum(currency),
                    liabilities = (inputs.payables + inputs.cardDebts).sum(currency),
                )
            },
        )
    }

    private fun List<Money>.sum(currency: Currency): Money =
        filter { it.currency == currency }.fold(Money.zero(currency)) { acc, money -> acc + money }
}
