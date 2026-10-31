package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import java.time.LocalDate

enum class CardInstallmentStatus(val label: String) {
    PAID("PAGADA"), OVERDUE("VENCIDA"), UPCOMING("PRÓXIMA"), PENDING("PENDIENTE"),
}

data class CardInstallment(
    val id: String,
    val cardId: CardId,
    val cardName: String,
    val purchaseId: String,
    val merchant: String?,
    val description: String?,
    val installmentNumber: Int,
    val installmentsCount: Int,
    val dueDate: LocalDate,
    val billingClosingDate: LocalDate?,
    val principal: Money,
    val interest: Money,
    val total: Money,
    val paid: Money,
    val refunded: Money,
    val outstanding: Money,
    val terms: PurchaseTerms,
    val status: CardInstallmentStatus,
) {
    fun atDate(today: LocalDate): CardInstallment = copy(status = when {
        outstanding.minorUnits == 0L -> CardInstallmentStatus.PAID
        dueDate < today -> CardInstallmentStatus.OVERDUE
        dueDate <= today.plusDays(7) -> CardInstallmentStatus.UPCOMING
        else -> CardInstallmentStatus.PENDING
    })
}

data class CardInstallmentsSummary(
    val installments: List<CardInstallment>,
    val outstandingTotal: Money,
    val upcomingTotal: Money,
    val overdueTotal: Money,
    val paidTotal: Money,
) {
    val upcoming: List<CardInstallment> get() = installments.filter { it.status == CardInstallmentStatus.UPCOMING }
    val overdue: List<CardInstallment> get() = installments.filter { it.status == CardInstallmentStatus.OVERDUE }
    val paid: List<CardInstallment> get() = installments.filter { it.status == CardInstallmentStatus.PAID }

    companion object {
        fun from(installments: List<CardInstallment>, currency: Currency): CardInstallmentsSummary {
            fun total(rows: List<CardInstallment>) = Money(rows.fold(0L) { sum, row ->
                require(row.outstanding.currency == currency) { "Card summary cannot mix currencies" }
                Math.addExact(sum, row.outstanding.minorUnits)
            }, currency)
            return CardInstallmentsSummary(
                installments = installments,
                outstandingTotal = total(installments),
                upcomingTotal = total(installments.filter { it.status == CardInstallmentStatus.UPCOMING }),
                overdueTotal = total(installments.filter { it.status == CardInstallmentStatus.OVERDUE }),
                paidTotal = Money(installments.fold(0L) { sum, row -> Math.addExact(sum, row.paid.minorUnits) }, currency),
            )
        }
    }
}
