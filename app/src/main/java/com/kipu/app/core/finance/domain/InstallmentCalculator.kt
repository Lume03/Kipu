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
import java.time.LocalDate
import kotlin.math.pow

object InstallmentCalculator {

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
        val teaDecimal = (resolvedTerms.teaBps ?: 0) / 10_000.0
        val temDecimal = if (hasApplicableRate) (1.0 + teaDecimal).pow(1.0 / 12.0) - 1.0 else 0.0
        val n = installmentsCount.toDouble()
        val regularPayment = if (hasApplicableRate) {
            roundMinorUnits(principal.minorUnits * temDecimal / (1.0 - (1.0 + temDecimal).pow(-n)))
        } else principal.minorUnits / installmentsCount
        var outstanding = principal.minorUnits
        val schedule = (0 until installmentsCount).map { index ->
            val interest = if (hasApplicableRate) roundMinorUnits(outstanding * temDecimal) else 0L
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
            appliedTemPercentage = if (hasApplicableRate) temDecimal * 100.0 else null,
            totalInterest = totalInterest,
            totalFinanced = totalFinanced,
            schedule = schedule,
            disclaimer = disclaimer,
            terms = resolvedTerms,
        )
    }

    private fun roundMinorUnits(value: Double): Long {
        require(value.isFinite() && value >= 0.0) { "Installment component is outside the supported range" }
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

}
