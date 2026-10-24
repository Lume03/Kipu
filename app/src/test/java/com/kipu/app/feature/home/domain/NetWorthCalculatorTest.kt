package com.kipu.app.feature.home.domain

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthCalculatorTest {
    private fun pen(units: Long) = Money(units * 100, Currency.PEN)
    private fun usd(units: Long) = Money(units * 100, Currency.USD)

    private fun available(inputs: NetWorthInputs) =
        (NetWorthCalculator.calculate(inputs) as NetWorthSummary.Available).byCurrency

    @Test
    fun `S1000 de activos mas S200 por cobrar menos S300 de pasivos da S900`() {
        val result = available(
            NetWorthInputs(
                accountBalances = listOf(pen(1000)),
                receivables = listOf(pen(200)),
                payables = listOf(pen(300)),
                cardDebts = emptyList(),
            ),
        ).single()

        assertEquals(Currency.PEN, result.currency)
        assertEquals(pen(900), result.netWorth)
    }

    @Test
    fun `la deuda de tarjeta se resta una vez como pasivo`() {
        val result = available(
            NetWorthInputs(listOf(pen(1000)), listOf(pen(200)), listOf(pen(100)), listOf(pen(200))),
        ).single()

        assertEquals(pen(300), result.liabilities)
        assertEquals(pen(900), result.netWorth)
    }

    @Test
    fun `PEN y USD se muestran por separado sin conversion`() {
        val result = available(
            NetWorthInputs(listOf(pen(500), usd(50)), listOf(usd(10)), listOf(pen(100)), emptyList()),
        )

        assertEquals(listOf(Currency.PEN, Currency.USD), result.map { it.currency })
        assertEquals(pen(400), result[0].netWorth)
        assertEquals(usd(60), result[1].netWorth)
    }

    @Test
    fun `sin ninguna fuente es sin datos y no cero`() {
        val result = NetWorthCalculator.calculate(NetWorthInputs(emptyList(), emptyList(), emptyList(), emptyList()))

        assertTrue(result is NetWorthSummary.NoData)
    }

    @Test
    fun `cuenta con saldo cero confirma cero`() {
        val result = available(NetWorthInputs(listOf(pen(0)), emptyList(), emptyList(), emptyList())).single()

        assertEquals(pen(0), result.netWorth)
    }

    @Test
    fun `el patrimonio puede ser negativo`() {
        val result = available(NetWorthInputs(listOf(pen(100)), emptyList(), listOf(pen(300)), emptyList())).single()

        assertEquals(pen(-200), result.netWorth)
    }
}
