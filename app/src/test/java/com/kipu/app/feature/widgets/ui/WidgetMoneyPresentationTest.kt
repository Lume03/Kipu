package com.kipu.app.feature.widgets.ui

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetMoneyPresentationTest {
    @Test fun compactLabelsKeepCentsInsteadOfTruncatingThem() {
        assertEquals("S/ 0.01", Money(1, Currency.PEN).text(false))
        assertEquals("$ 12.34", Money(1234, Currency.USD).text(false))
        assertEquals("S/ 12", Money(1200, Currency.PEN).text(false))
        assertEquals("S/ 12.00", Money(1200, Currency.PEN).text(false, decimals = true))
    }
    @Test fun signedAmountsShowOneSignAndMaskNeverRevealsSignOrCents() {
        assertEquals("−S/ 0.01", Money(-1, Currency.PEN).signed(false))
        assertEquals("+S/ 0.01", Money(1, Currency.PEN).signed(false))
        assertEquals("S/ ••••", Money(-1234, Currency.PEN).signed(true))
        assertEquals("$ ••••", Money(1234, Currency.USD).text(true))
    }
    @Test fun maximumSupportedAmountKeepsItsSignAndCents() {
        assertEquals("−S/ 999,999,999,999.99", Money(-Money.MAX_MONEY_MINOR, Currency.PEN).text(false))
    }
}
