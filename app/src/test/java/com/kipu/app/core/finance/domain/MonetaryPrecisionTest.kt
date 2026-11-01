package com.kipu.app.core.finance.domain

import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MonetaryPrecisionTest {
    private fun movement(amount: Long) = FinancialMovement(MovementId.generate(), OperationId.generate(), 0,
        UserId("61000000-0000-4000-8000-000000000001"), MovementKind.ADJUSTMENT, amount, Currency.PEN,
        accountId = AccountId.generate(), effectiveAt = Instant.EPOCH)

    @Test fun `monetary bounds reject MIN_VALUE and accept both endpoints`() {
        for (amount in listOf(Long.MIN_VALUE, -Money.MAX_MONEY_MINOR - 1, Money.MAX_MONEY_MINOR + 1)) {
            assertThrows(IllegalArgumentException::class.java) { Money(amount, Currency.PEN) }
            assertThrows(IllegalArgumentException::class.java) { movement(amount) }
        }
        for (amount in listOf(-Money.MAX_MONEY_MINOR, Money.MAX_MONEY_MINOR)) {
            assertEquals(amount, Money(amount, Currency.PEN).minorUnits)
            assertEquals(amount, movement(amount).amountMinorUnits)
        }
    }

    @Test fun `French schedule matches independent high precision decimal oracle`() {
        val mc = MathContext(60, RoundingMode.HALF_UP)
        fun cents(value: BigDecimal) = value.setScale(0, RoundingMode.HALF_UP).longValueExact()
        for (rate in listOf(1, 4500, 100000)) for (count in listOf(2, 6, 36)) {
            val amount = 9_876_543_210_987L
            val factor = BigDecimal.ONE.add(BigDecimal(rate).movePointLeft(4))
            var low = BigDecimal.ONE
            var high = BigDecimal("2")
            repeat(220) {
                val middle = low.add(high).divide(BigDecimal(2), mc)
                if (middle.pow(12, mc) < factor) low = middle else high = middle
            }
            val growth = low.add(high).divide(BigDecimal(2), mc)
            val monthly = growth.subtract(BigDecimal.ONE)
            val payment = cents(BigDecimal(amount).multiply(monthly, mc).divide(
                BigDecimal.ONE.subtract(BigDecimal.ONE.divide(growth.pow(count, mc), mc)), mc))
            val result = InstallmentCalculator.simulate(CardId.generate(), Money(amount, Currency.PEN), count,
                LocalDate.of(2026, 7, 19), 19, terms = PurchaseTerms(PurchaseType.INSTALLMENTS,
                    Financing.WITH_INTEREST, count, rate, PurchaseRateSource.USER_ENTERED, RoundingPolicy.LAST_INSTALLMENT_RESIDUAL))
            var outstanding = amount
            result.schedule.forEachIndexed { index, row ->
                val interest = cents(BigDecimal(outstanding).multiply(monthly, mc))
                val principal = if (index == count - 1) outstanding else (payment - interest).coerceIn(0L, outstanding)
                assertEquals("$rate/$count/$index interest", interest, row.interestPortion.minorUnits)
                assertEquals("$rate/$count/$index principal", principal, row.principalPortion.minorUnits)
                outstanding -= principal
            }
            assertEquals(0L, outstanding)
        }
    }
}
