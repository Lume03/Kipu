package com.kipu.app.core.finance.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.InstallmentScheduleItem
import com.kipu.app.feature.accounts.domain.model.InstallmentSimulation
import com.kipu.app.feature.accounts.domain.model.RateSource
import com.kipu.app.feature.accounts.domain.model.PurchaseTerms
import com.kipu.app.feature.accounts.domain.model.PurchaseType
import com.kipu.app.feature.accounts.domain.model.Financing
import com.kipu.app.feature.accounts.domain.model.PurchaseRateSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.math.MathContext
import com.kipu.app.feature.accounts.domain.model.RoundingPolicy
import java.time.LocalDate

object InstallmentCalculator {
    private val calculationContext = MathContext(34, RoundingMode.HALF_UP)

    fun simulateForPurchase(
        cardId: CardId,
        principal: Money,
        installmentsCount: Int,
        configuration: BillingCycleConfiguration,
        operationDate: LocalDate,
        processingDate: LocalDate? = null,
        teaBps: Int? = null,
        rateSource: RateSource = if (teaBps != null && teaBps > 0) RateSource.PERSONAL_TEA else RateSource.NONE,
        candidateId: String? = null,
        terms: PurchaseTerms? = null,
    ): InstallmentSimulation {
        val cycles = BillingCycleCalculator.installmentCycles(
            configuration, operationDate, installmentsCount, processingDate,
        )
        return simulate(
            cardId = cardId,
            principal = principal,
            installmentsCount = installmentsCount,
            firstDueDate = cycles.first().dueDate,
            dueDay = configuration.dueDay,
            teaBps = teaBps,
            rateSource = rateSource,
            candidateId = candidateId,
            billingCycles = cycles,
            terms = terms,
        )
    }

    /**
     * Simulates an installment plan with deterministic cent distribution.
     * Guaranteed: sum of schedule installment amounts == totalFinanced.
     */
    fun simulate(
        cardId: CardId,
        principal: Money,
        installmentsCount: Int,
        firstDueDate: LocalDate,
        dueDay: Int,
        teaBps: Int? = null,
        rateSource: RateSource = if (teaBps != null && teaBps > 0) RateSource.PERSONAL_TEA else RateSource.NONE,
        candidateId: String? = null,
        billingCycles: List<InstallmentBillingCycle>? = null,
        terms: PurchaseTerms? = null,
    ): InstallmentSimulation {
        require(billingCycles == null || billingCycles.size == installmentsCount) { "Cycle count must match installments" }
        require(installmentsCount in 1..36) {
            "Installments count must be between 1 and 36: $installmentsCount"
        }
        require(principal.minorUnits > 0) {
            "Principal must be positive: ${principal.minorUnits}"
        }
        require(dueDay in 1..31) {
            "Due day must be between 1 and 31: $dueDay"
        }

        val resolvedTerms = terms ?: if (teaBps != null && teaBps > 0 && installmentsCount > 1) {
            PurchaseTerms(
                PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, installmentsCount, teaBps,
                when (rateSource) {
                    RateSource.PERSONAL_TEA -> PurchaseRateSource.PERSONAL
                    RateSource.REFERENTIAL_CATALOG -> PurchaseRateSource.REFERENCE
                    RateSource.NONE, RateSource.USER_ENTERED -> PurchaseRateSource.USER_ENTERED
                },
            )
        } else PurchaseTerms.interestFree(installmentsCount)
        require(resolvedTerms.installmentsCount == installmentsCount) { "Terms do not match the installment count" }
        val hasApplicableRate = resolvedTerms.financing == Financing.WITH_INTEREST
        val teaDecimal = BigDecimal(resolvedTerms.teaBps ?: 0).movePointLeft(4)
        val growth = if (hasApplicableRate) twelfthRoot(BigDecimal.ONE.add(teaDecimal)) else BigDecimal.ONE
        val temDecimal = growth.subtract(BigDecimal.ONE)
        val regularPayment = if (hasApplicableRate) {
            roundMinorUnits(BigDecimal.valueOf(principal.minorUnits).multiply(temDecimal, calculationContext).divide(
                BigDecimal.ONE.subtract(BigDecimal.ONE.divide(growth.pow(installmentsCount, calculationContext), calculationContext)),
                calculationContext,
            ))
        } else principal.minorUnits / installmentsCount
        var outstanding = principal.minorUnits
        var schedule = (0 until installmentsCount).map { index ->
            val interest = if (hasApplicableRate) roundMinorUnits(BigDecimal.valueOf(outstanding).multiply(temDecimal, calculationContext)) else 0L
            val principalPortion = if (index == installmentsCount - 1) outstanding else
                (regularPayment - interest).coerceIn(0L, outstanding)
            outstanding = Math.subtractExact(outstanding, principalPortion)
            InstallmentScheduleItem(
                installmentNumber = index + 1,
                dueDate = billingCycles?.get(index)?.dueDate
                    ?: BillingCycleCalculator.installmentDueDate(firstDueDate, dueDay, index),
                amount = Money(Math.addExact(principalPortion, interest), principal.currency),
                principalPortion = Money(principalPortion, principal.currency),
                interestPortion = Money(interest, principal.currency),
                billingClosingDate = billingCycles?.get(index)?.cycle?.closingDate,
            )
        }
        if (resolvedTerms.roundingPolicy == RoundingPolicy.FIRST_INSTALLMENT_RESIDUAL && installmentsCount > 1) {
            // Move the final principal residual to installment one; estimated interest stays informational.
            val residual = schedule.last().amount.minorUnits - regularPayment
            val deltas = LongArray(installmentsCount)
            if (residual >= 0L) {
                deltas[0] = residual
                deltas[installmentsCount - 1] = -residual
            } else {
                // Tiny principals can settle early; absorb a negative residual from the earliest principal available.
                var remaining = -residual
                for (index in 0 until installmentsCount - 1) {
                    val removed = minOf(remaining, schedule[index].principalPortion.minorUnits)
                    deltas[index] = -removed
                    remaining -= removed
                }
                check(remaining == 0L)
                deltas[installmentsCount - 1] = -residual
            }
            schedule = schedule.mapIndexed { index, item ->
                item.copy(
                    amount = Money(Math.addExact(item.amount.minorUnits, deltas[index]), principal.currency),
                    principalPortion = Money(Math.addExact(item.principalPortion.minorUnits, deltas[index]), principal.currency),
                )
            }
        }
        val totalInterestMinor = schedule.fold(0L) { sum, item -> Math.addExact(sum, item.interestPortion.minorUnits) }
        val totalFinancedMinor = Math.addExact(principal.minorUnits, totalInterestMinor)
        check(outstanding == 0L)
        check(schedule.sumOf { it.principalPortion.minorUnits } == principal.minorUnits)
        check(schedule.sumOf { it.amount.minorUnits } == totalFinancedMinor)

        val totalFinanced = Money(totalFinancedMinor, principal.currency)
        val totalInterest = Money(totalInterestMinor, principal.currency)

        val disclaimer = if (hasApplicableRate) {
            InstallmentSimulation.DISCLAIMER_WITH_RATE
        } else {
            InstallmentSimulation.DISCLAIMER_WITHOUT_RATE
        }

        return InstallmentSimulation(
            candidateId = candidateId,
            cardId = cardId,
            principal = principal,
            installmentsCount = installmentsCount,
            rateSource = when (resolvedTerms.rateSource) {
                PurchaseRateSource.PERSONAL -> RateSource.PERSONAL_TEA
                PurchaseRateSource.REFERENCE -> RateSource.REFERENTIAL_CATALOG
                PurchaseRateSource.USER_ENTERED -> RateSource.USER_ENTERED
                null -> RateSource.NONE
            },
            appliedTeaBps = resolvedTerms.teaBps,
            appliedTemPercentage = if (hasApplicableRate) temDecimal.movePointRight(2).toDouble() else null,
            totalInterest = totalInterest,
            totalFinanced = totalFinanced,
            schedule = schedule,
            disclaimer = disclaimer,
            terms = resolvedTerms,
        )
    }

    private fun roundMinorUnits(value: BigDecimal): Long {
        require(value.signum() >= 0) { "Installment component is outside the supported range" }
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun twelfthRoot(value: BigDecimal): BigDecimal {
        var root = BigDecimal.ONE
        val twelve = BigDecimal(12)
        repeat(100) {
            val next = root.multiply(BigDecimal(11), calculationContext)
                .add(value.divide(root.pow(11, calculationContext), calculationContext), calculationContext)
                .divide(twelve, calculationContext)
            if (next == root) return next
            root = next
        }
        return root
    }
}
