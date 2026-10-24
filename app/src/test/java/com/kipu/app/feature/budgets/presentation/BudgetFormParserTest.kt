package com.kipu.app.feature.budgets.presentation

import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BudgetFormParserTest {
    private val valid = BudgetFormInput(name = " Comida ", limitText = "500")

    private fun invalid(input: BudgetFormInput) =
        assertIs<BudgetFormParseResult.Invalid>(BudgetFormParser.parse(input)).errors

    @Test
    fun `valid total monthly budget with default thresholds`() {
        val parsed = assertIs<BudgetFormParseResult.Valid>(BudgetFormParser.parse(valid)).value
        assertEquals("Comida", parsed.name)
        assertEquals(50_000L, parsed.limitMinor)
        assertEquals(BudgetScope.Total, parsed.scope)
        assertEquals(BudgetSchedule.Recurring(BudgetPeriodType.MONTHLY), parsed.schedule)
        assertEquals(listOf(5000, 8000, 10000), parsed.thresholdsBps)
    }

    @Test
    fun `accepts decimal comma and point`() {
        assertEquals(50_050L, BudgetFormParser.parseLimitMinor("500,50"))
        assertEquals(50_050L, BudgetFormParser.parseLimitMinor("500.50"))
        assertNull(BudgetFormParser.parseLimitMinor("500.555"))
        assertNull(BudgetFormParser.parseLimitMinor("abc"))
        assertNull(BudgetFormParser.parseLimitMinor(""))
    }

    @Test
    fun `blank name zero limit and bad limit are field errors`() {
        val errors = invalid(BudgetFormInput(name = "  ", limitText = "0"))
        assertTrue(BudgetFormField.NAME in errors)
        assertTrue(BudgetFormField.LIMIT in errors)
        assertTrue(BudgetFormField.LIMIT in invalid(valid.copy(limitText = "x")))
    }

    @Test
    fun `category scope requires a selected category`() {
        val errors = invalid(valid.copy(scopeKind = BudgetScopeKind.CATEGORY, categoryId = null))
        assertTrue(BudgetFormField.CATEGORY in errors)
        val ok = assertIs<BudgetFormParseResult.Valid>(
            BudgetFormParser.parse(valid.copy(scopeKind = BudgetScopeKind.CATEGORY, categoryId = "c1")),
        )
        assertEquals(BudgetScope.Category("c1"), ok.value.scope)
    }

    @Test
    fun `custom range rejects end before start with a clear message`() {
        val errors = invalid(
            valid.copy(
                periodType = BudgetPeriodType.CUSTOM_RANGE,
                rangeStartText = "2026-10-20",
                rangeEndText = "2026-10-10",
            ),
        )
        assertEquals("La fecha de fin no puede ser anterior a la de inicio.", errors[BudgetFormField.RANGE_END])
    }

    @Test
    fun `custom range requires both dates and accepts single day`() {
        val missing = invalid(valid.copy(periodType = BudgetPeriodType.CUSTOM_RANGE))
        assertTrue(BudgetFormField.RANGE_START in missing && BudgetFormField.RANGE_END in missing)
        val ok = assertIs<BudgetFormParseResult.Valid>(
            BudgetFormParser.parse(
                valid.copy(periodType = BudgetPeriodType.CUSTOM_RANGE, rangeStartText = "2026-10-10", rangeEndText = "2026-10-10"),
            ),
        )
        assertEquals(
            BudgetSchedule.CustomRange(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10)),
            ok.value.schedule,
        )
    }

    @Test
    fun `thresholds are editable and validated`() {
        val ok = assertIs<BudgetFormParseResult.Valid>(BudgetFormParser.parse(valid.copy(thresholdsText = "30, 90 %")))
        assertEquals(listOf(3000, 9000), ok.value.thresholdsBps)
        assertTrue(BudgetFormField.THRESHOLDS in invalid(valid.copy(thresholdsText = "0, 50")))
        assertTrue(BudgetFormField.THRESHOLDS in invalid(valid.copy(thresholdsText = "101")))
        assertTrue(BudgetFormField.THRESHOLDS in invalid(valid.copy(thresholdsText = "50, 50")))
        assertTrue(BudgetFormField.THRESHOLDS in invalid(valid.copy(thresholdsText = "mitad")))
        val defaults = assertIs<BudgetFormParseResult.Valid>(BudgetFormParser.parse(valid.copy(thresholdsText = "")))
        assertEquals(listOf(5000, 8000, 10000), defaults.value.thresholdsBps)
    }
}
