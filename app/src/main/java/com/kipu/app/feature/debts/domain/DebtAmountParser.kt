package com.kipu.app.feature.debts.domain

import com.kipu.app.core.finance.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

object DebtAmountParser {
    private val decimalAmount = Regex("^[0-9]+(?:[.,][0-9]{1,2})?$")
    private val zeroAmount = Regex("^0(?:[.,]0{1,2})?$")

    fun toMinorUnits(input: String): Long? {
        val normalized = input.trim().takeIf(decimalAmount::matches)?.replace(',', '.') ?: return null
        return runCatching {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
                .takeIf { it in 1L..Money.MAX_MONEY_MINOR }
        }.getOrNull()
    }

    fun toNonNegativeMinorUnits(input: String): Long? {
        val normalized = input.trim()
        if (normalized.isEmpty() || zeroAmount.matches(normalized)) return 0L
        return toMinorUnits(normalized)
    }
}
