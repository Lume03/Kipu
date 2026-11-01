package com.kipu.app.core.finance.domain

import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InstallmentContractTest {
    private fun simulate(amount: Long, n: Int, interest: Boolean = false) = InstallmentCalculator.simulateForPurchase(
        CardId.generate(), Money(amount, Currency.PEN), n, BillingCycleConfiguration(26, 19),
        LocalDate.parse("2026-06-25"), terms = if (interest) PurchaseTerms(PurchaseType.INSTALLMENTS,
            Financing.WITH_INTEREST, n, 4500, PurchaseRateSource.USER_ENTERED) else PurchaseTerms.interestFree(n))

    @Test fun `1200 soles in six interest free payments is exactly 200 each`() {
        val result = simulate(120000, 6)
        assertEquals(List(6) { 20000L }, result.schedule.map { it.amount.minorUnits })
        assertEquals(0L, result.totalInterest.minorUnits)
        assertEquals(120000L, result.totalFinanced.minorUnits)
    }
    @Test fun `1200 soles at 45 percent yields the accepted French schedule`() {
        val result = simulate(120000, 6, true)
        assertEquals(listOf(22259L) + List(5) { 22258L }, result.schedule.map { it.amount.minorUnits })
        assertEquals(13549L, result.totalInterest.minorUnits)
        assertEquals(133549L, result.totalFinanced.minorUnits)
        assertEquals(4500, result.terms.teaBps)
    }
    @Test fun `principal sums exactly for all supported counts and amounts`() {
        for (amount in listOf(1L, 2L, 25L, 50L, 99L, 10000L, 120000L, 9999999L)) for (n in 1..36) for (interest in listOf(false, true)) {
            val result = simulate(amount, n, interest)
            assertEquals("$amount/$n/$interest", amount, result.schedule.sumOf { it.principalPortion.minorUnits })
            assertEquals(result.totalFinanced.minorUnits, result.schedule.sumOf { it.amount.minorUnits })
            assertEquals(result.totalInterest.minorUnits, result.schedule.sumOf { it.interestPortion.minorUnits })
            result.schedule.forEach { assertEquals(it.amount.minorUnits, it.principalPortion.minorUnits + it.interestPortion.minorUnits) }
            assertTrue(result.schedule.all { it.amount.minorUnits >= 0 })
        }
        assertEquals(listOf(3334L, 3333L, 3333L), simulate(10000, 3).schedule.map { it.amount.minorUnits })
    }
    @Test fun `direct purchase is one interest free payment`() {
        val result = simulate(120000, 1)
        assertEquals(PurchaseType.DIRECT, result.terms.purchaseType)
        assertEquals(Financing.INTEREST_FREE, result.terms.financing)
        assertNull(result.appliedTeaBps)
        assertEquals(120000L, result.schedule.single().amount.minorUnits)
    }
    @Test fun `tiny financed principal absorbs negative residual without negative installments`() {
        val result = simulate(25, 11, true)
        assertEquals(0L, result.schedule.first().principalPortion.minorUnits)
        assertEquals(3L, result.schedule.last().principalPortion.minorUnits)
        assertEquals(25L, result.schedule.sumOf { it.principalPortion.minorUnits })
        assertTrue(result.schedule.all { it.principalPortion.minorUnits >= 0 && it.amount.minorUnits >= 0 })
    }
    @Test fun `legacy policy still reproduces last installment residual`() {
        val result = InstallmentCalculator.simulate(CardId.generate(), Money(10000, Currency.PEN), 3,
            LocalDate.of(2026, 7, 19), 19, terms = PurchaseTerms.interestFree(3).copy(
                roundingPolicy = RoundingPolicy.LAST_INSTALLMENT_RESIDUAL))
        assertEquals(listOf(3333L, 3333L, 3334L), result.schedule.map { it.amount.minorUnits })
    }
    @Test fun `invalid purchase terms amounts and inconsistent counts are rejected`() {
        for (n in listOf(0, 37)) assertThrows(IllegalArgumentException::class.java) { simulate(100, n) }
        assertThrows(IllegalArgumentException::class.java) { simulate(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { PurchaseTerms(PurchaseType.DIRECT, Financing.WITH_INTEREST, 1, 4500, PurchaseRateSource.PERSONAL) }
        for (rate in listOf(0, -1, 100001)) assertThrows(IllegalArgumentException::class.java) {
            PurchaseTerms(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, 6, rate, PurchaseRateSource.USER_ENTERED)
        }
        assertThrows(IllegalArgumentException::class.java) { PurchaseTerms(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, 6, 4500) }
        assertThrows(IllegalArgumentException::class.java) { PurchaseTerms(PurchaseType.INSTALLMENTS, Financing.INTEREST_FREE, 6, 4500) }
    }
}
