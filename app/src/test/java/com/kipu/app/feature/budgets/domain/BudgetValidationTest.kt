package com.kipu.app.feature.budgets.domain

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetValidationTest {
    private val validator = BudgetValidator(FakeCategoryLookup(unavailable = setOf("transporte")))
    private val monthly = BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY)

    private fun validate(
        name: String = "Mensual",
        scope: BudgetScope = BudgetScope.Total,
        limit: Long = 50_000,
        currency: String = "PEN",
        schedule: BudgetSchedule = monthly,
        thresholds: List<Int> = Budget.DEFAULT_THRESHOLDS_BPS,
    ) = runBlocking { validator.validate("u1", name, scope, limit, currency, schedule, thresholds) }

    @Test fun validBudgetHasNoErrors() = assertTrue(validate().isEmpty())

    @Test fun limitMustBePositive() {
        assertEquals(listOf<BudgetError>(BudgetError.LimitNotPositive), validate(limit = 0))
        assertEquals(listOf<BudgetError>(BudgetError.LimitNotPositive), validate(limit = -1))
    }

    @Test fun currencyMustBeIsoAndSupported() {
        assertEquals(listOf<BudgetError>(BudgetError.InvalidCurrency("XX1")), validate(currency = "XX1"))
        assertEquals(listOf<BudgetError>(BudgetError.UnsupportedCurrency("EUR")), validate(currency = "EUR"))
        assertTrue(validate(currency = "USD").isEmpty())
    }

    @Test fun nameRequiredAndBounded() {
        assertEquals(listOf<BudgetError>(BudgetError.BlankName), validate(name = "  "))
        assertEquals(listOf<BudgetError>(BudgetError.NameTooLong(60)), validate(name = "x".repeat(61)))
    }

    @Test fun invertedCustomRangeRejectedSingleDayAccepted() {
        val d = LocalDate.parse("2026-10-10")
        assertEquals(
            listOf<BudgetError>(BudgetError.InvertedRange),
            validate(schedule = BudgetSchedule.CustomRange(d, d.minusDays(1))),
        )
        assertTrue(validate(schedule = BudgetSchedule.CustomRange(d, d)).isEmpty())
    }

    @Test fun thresholdsMustBeInRangeAndUnique() {
        assertEquals(listOf<BudgetError>(BudgetError.ThresholdOutOfRange(0)), validate(thresholds = listOf(0, 5000)))
        assertEquals(listOf<BudgetError>(BudgetError.ThresholdOutOfRange(10001)), validate(thresholds = listOf(10001)))
        assertEquals(
            listOf<BudgetError>(BudgetError.DuplicateThreshold(8000)),
            validate(thresholds = listOf(8000, 5000, 8000)),
        )
        assertTrue(validate(thresholds = listOf(1, 10000)).isEmpty())
    }

    @Test fun categoryMustBeOwnedAndAvailable() {
        assertTrue(validate(scope = BudgetScope.Category("restaurantes")).isEmpty())
        assertEquals(
            listOf<BudgetError>(BudgetError.CategoryNotAvailable("transporte")),
            validate(scope = BudgetScope.Category("transporte")),
        )
        assertEquals(
            listOf<BudgetError>(BudgetError.CategoryNotAvailable("ajena")),
            validate(scope = BudgetScope.Category("ajena")),
        )
    }

    @Test fun reportsAllErrorsTogether() {
        assertEquals(3, validate(name = "", limit = 0, currency = "ZZZ").size)
    }
}
