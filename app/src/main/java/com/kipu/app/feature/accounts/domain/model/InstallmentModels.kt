package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Money
import java.time.Instant
import java.time.LocalDate

enum class CandidateStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}

enum class RateSource {
    PERSONAL_TEA,
    REFERENTIAL_CATALOG,
    USER_ENTERED,
    NONE
}

enum class PurchaseType { DIRECT, INSTALLMENTS }
enum class Financing { WITH_INTEREST, INTEREST_FREE }
enum class PurchaseRateSource { PERSONAL, REFERENCE, USER_ENTERED }

/** Legacy schedules retain LAST; new purchases put the principal remainder in installment one. */
enum class RoundingPolicy { LAST_INSTALLMENT_RESIDUAL, FIRST_INSTALLMENT_RESIDUAL }

/** Immutable terms approved for one purchase; subsequent card edits never change them. */
data class PurchaseTerms(
    val purchaseType: PurchaseType,
    val financing: Financing,
    val installmentsCount: Int,
    val teaBps: Int? = null,
    val rateSource: PurchaseRateSource? = null,
    val roundingPolicy: RoundingPolicy = RoundingPolicy.FIRST_INSTALLMENT_RESIDUAL,
) {
    init {
        require(installmentsCount in 1..36) { "Installments count must be between 1 and 36" }
        require(purchaseType != PurchaseType.DIRECT ||
            (installmentsCount == 1 && financing == Financing.INTEREST_FREE)) { "Direct purchases require one interest-free payment" }
        require(financing != Financing.INTEREST_FREE || (teaBps == null && rateSource == null)) {
            "Interest-free purchases cannot carry a rate"
        }
        require(financing != Financing.WITH_INTEREST ||
            (purchaseType == PurchaseType.INSTALLMENTS && teaBps != null && teaBps in 1..100_000 && rateSource != null)) {
            "Financed purchases require a positive TEA up to 1000% and its source"
        }
    }

    companion object {
        fun interestFree(installmentsCount: Int = 1) = PurchaseTerms(
            if (installmentsCount == 1) PurchaseType.DIRECT else PurchaseType.INSTALLMENTS,
            Financing.INTEREST_FREE,
            installmentsCount,
        )
    }
}

data class PurchaseCandidate(
    val id: String,
    val cardId: CardId,
    val amount: Money,
    val merchant: String,
    val occurredAt: Instant,
    val suggestedInstallments: Int = 1,
    val status: CandidateStatus = CandidateStatus.PENDING,
    val categoryId: String? = null,
    val processedAt: Instant? = null,
) {
    init {
        require(amount.minorUnits > 0) { "Purchase amount must be positive: ${amount.minorUnits}" }
        require(merchant.isNotBlank()) { "Merchant cannot be blank" }
        require(suggestedInstallments in 1..36) { "Suggested installments must be between 1 and 36: $suggestedInstallments" }
    }
}

data class InstallmentScheduleItem(
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val amount: Money,
    val principalPortion: Money,
    val interestPortion: Money,
    val billingClosingDate: LocalDate? = null,
) {
    init {
        require(installmentNumber >= 1) { "Installment number must be >= 1: $installmentNumber" }
        require(amount.minorUnits >= 0) { "Installment amount cannot be negative" }
        require(principalPortion.currency == amount.currency && interestPortion.currency == amount.currency) {
            "Currency mismatch in installment schedule item"
        }
    }
}

data class InstallmentSimulation(
    val candidateId: String? = null,
    val cardId: CardId,
    val principal: Money,
    val installmentsCount: Int,
    val rateSource: RateSource,
    val appliedTeaBps: Int? = null,
    val appliedTemPercentage: Double? = null,
    val totalInterest: Money,
    val totalFinanced: Money,
    val schedule: List<InstallmentScheduleItem>,
    val disclaimer: String,
    val terms: PurchaseTerms = PurchaseTerms.interestFree(installmentsCount),
) {
    init {
        require(installmentsCount in 1..36) { "Installments count must be between 1 and 36: $installmentsCount" }
        require(schedule.size == installmentsCount) { "Schedule size (${schedule.size}) must match installments count ($installmentsCount)" }
        require(principal.currency == totalFinanced.currency && principal.currency == totalInterest.currency) {
            "Currency mismatch in simulation totals"
        }
        val sumSchedule = schedule.fold(0L) { acc, item -> Math.addExact(acc, item.amount.minorUnits) }
        require(sumSchedule == totalFinanced.minorUnits) {
            "Sum of schedule installments ($sumSchedule) does not match total financed (${totalFinanced.minorUnits})"
        }
    }

    companion object {
        const val DISCLAIMER_WITH_RATE =
            "Simulación informativa con tasa referencial o personal. No incluye seguro de desgravamen ni comisiones del emisor. Al confirmar, los términos aceptados se guardan como obligación en el cronograma."

        const val DISCLAIMER_WITHOUT_RATE =
            "Distribución referencial sin intereses. Para estimar el costo financiero, configure la TEA de su tarjeta en el catálogo o ingrese su tasa contractual."
    }
}
