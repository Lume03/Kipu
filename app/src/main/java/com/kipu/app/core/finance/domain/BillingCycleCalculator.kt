package com.kipu.app.core.finance.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class CycleDateBasis {
    OPERATION,
    PROCESSING;

    companion object {
        fun fromValue(value: String?): CycleDateBasis =
            entries.firstOrNull { it.name == value } ?: OPERATION
    }
}

/** Calendar rules; issuer-specific behaviour is still to be validated against a real statement. */
data class BillingCycleConfiguration(
    val closingDay: Int,
    val dueDay: Int,
    val closingDayInclusive: Boolean = false,
    val cycleDateBasis: CycleDateBasis = CycleDateBasis.OPERATION,
) {
    init {
        require(closingDay in 1..31) { "Closing day must be between 1 and 31" }
        require(dueDay in 1..31) { "Due day must be between 1 and 31" }
    }
}

/** Consumption boundaries are both inclusive; closingDate is the billing date. */
data class BillingCycle(
    val consumptionStartDate: LocalDate,
    val consumptionEndDate: LocalDate,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
)

data class InstallmentBillingCycle(
    val installmentNumber: Int,
    val cycle: BillingCycle,
) {
    val dueDate: LocalDate get() = cycle.dueDate
}

/** Pure civil-date calculation, without weekend or holiday adjustments. */
object BillingCycleCalculator {
    fun cycleForPurchase(
        configuration: BillingCycleConfiguration,
        operationDate: LocalDate,
        processingDate: LocalDate? = null,
    ): BillingCycle {
        val date = when (configuration.cycleDateBasis) {
            CycleDateBasis.OPERATION -> operationDate
            CycleDateBasis.PROCESSING -> processingDate ?: operationDate
        }
        val month = YearMonth.from(date)
        val closingDate = effectiveDate(configuration.closingDay, month)
        val closesThisMonth = date.isBefore(closingDate) ||
            (configuration.closingDayInclusive && date == closingDate)
        return cycleClosingIn(configuration, if (closesThisMonth) month else month.plusMonths(1))
    }

    fun currentCycle(
        configuration: BillingCycleConfiguration,
        today: LocalDate = LocalDate.now(ZoneId.of("America/Lima")),
    ): BillingCycle = cycleForPurchase(configuration, today)

    /** Advances by closing months, preserving the preferred day through short months. */
    fun nextCycle(
        configuration: BillingCycleConfiguration,
        cycle: BillingCycle,
        count: Int = 1,
    ): BillingCycle {
        require(count >= 0) { "Cycle offset cannot be negative" }
        return cycleClosingIn(configuration, YearMonth.from(cycle.closingDate).plusMonths(count.toLong()))
    }

    fun installmentCycles(
        configuration: BillingCycleConfiguration,
        operationDate: LocalDate,
        installmentsCount: Int,
        processingDate: LocalDate? = null,
    ): List<InstallmentBillingCycle> {
        require(installmentsCount in 1..36) { "Installments count must be between 1 and 36" }
        val firstCycle = cycleForPurchase(configuration, operationDate, processingDate)
        return (1..installmentsCount).map { number ->
            InstallmentBillingCycle(number, nextCycle(configuration, firstCycle, number - 1))
        }
    }

    /** Compatibility for callers that only know a first due date, without card closing rules. */
    fun installmentDueDate(firstDueDate: LocalDate, dueDay: Int, offset: Int): LocalDate {
        require(offset >= 0) { "Installment offset cannot be negative" }
        return effectiveDate(dueDay, YearMonth.from(firstDueDate).plusMonths(offset.toLong()))
    }

    private fun cycleClosingIn(configuration: BillingCycleConfiguration, month: YearMonth): BillingCycle {
        val closingDate = effectiveDate(configuration.closingDay, month)
        val previousClosingDate = effectiveDate(configuration.closingDay, month.minusMonths(1))
        return BillingCycle(
            consumptionStartDate = if (configuration.closingDayInclusive) previousClosingDate.plusDays(1) else previousClosingDate,
            consumptionEndDate = if (configuration.closingDayInclusive) closingDate else closingDate.minusDays(1),
            closingDate = closingDate,
            dueDate = CreditCalculations.calculateNextDate(configuration.dueDay, closingDate.plusDays(1)),
        )
    }

    private fun effectiveDate(day: Int, month: YearMonth): LocalDate =
        CreditCalculations.calculateEffectiveDate(day, month.year, month.month)
}
