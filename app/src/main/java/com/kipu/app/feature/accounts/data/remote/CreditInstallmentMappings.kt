package com.kipu.app.feature.accounts.data.remote

import com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity
import com.kipu.app.feature.accounts.domain.model.Financing
import com.kipu.app.feature.accounts.domain.model.PurchaseRateSource
import com.kipu.app.feature.accounts.domain.model.PurchaseTerms
import com.kipu.app.feature.accounts.domain.model.PurchaseType
import com.kipu.app.feature.accounts.domain.model.RoundingPolicy
import java.time.LocalDate

/** Missing optional fields describe the legacy interest-free contract. */
internal fun toLocalInstallment(
    remote: CreditInstallmentResponseDto,
    userId: String,
    transactionId: String,
    count: Int,
    createdAt: Long,
    updatedAt: Long,
): CreditInstallmentEntity {
    val terms = PurchaseTerms(
        purchaseType = remote.purchaseType?.let(PurchaseType::valueOf)
            ?: if (count == 1) PurchaseType.DIRECT else PurchaseType.INSTALLMENTS,
        financing = remote.financing?.let(Financing::valueOf) ?: Financing.INTEREST_FREE,
        installmentsCount = remote.installmentsCount ?: count,
        teaBps = remote.teaBps,
        rateSource = remote.rateSource?.let(PurchaseRateSource::valueOf),
        roundingPolicy = remote.roundingPolicy?.let(RoundingPolicy::valueOf) ?: RoundingPolicy.LAST_INSTALLMENT_RESIDUAL,
    )
    require(remote.installmentNumber in 1..count) { "Remote installment number is outside the purchase plan" }
    require(terms.installmentsCount == count) { "Remote terms do not match purchase count" }
    return CreditInstallmentEntity(
        id = remote.id, userId = userId, transactionId = transactionId,
        installmentNumber = remote.installmentNumber,
        dueDate = LocalDate.parse(remote.dueDate).toEpochDay(),
        principalMinor = remote.principalMinor, interestMinor = remote.interestMinor,
        status = "PENDING", revision = 1L, createdAt = createdAt, updatedAt = updatedAt,
        purchaseType = terms.purchaseType.name, financing = terms.financing.name,
        installmentsCount = terms.installmentsCount, teaBps = terms.teaBps,
        rateSource = terms.rateSource?.name, roundingPolicy = terms.roundingPolicy.name,
        billingClosingDate = remote.billingClosingDate?.let { LocalDate.parse(it).toEpochDay() },
    )
}
