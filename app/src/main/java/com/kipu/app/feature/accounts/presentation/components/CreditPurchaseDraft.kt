package com.kipu.app.feature.accounts.presentation.components

import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.domain.model.Financing
import com.kipu.app.feature.accounts.domain.model.PurchaseRateSource
import com.kipu.app.feature.accounts.domain.model.PurchaseTerms
import com.kipu.app.feature.accounts.domain.model.PurchaseType
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogContext
import com.kipu.app.feature.accounts.presentation.instruments.resolveRateCatalogContext
import com.kipu.app.core.finance.domain.model.Currency
import java.math.BigDecimal

/** Editable presentation state, separate from the immutable terms accepted on save. */
data class CreditPurchaseDraft(
    val purchaseType: PurchaseType = PurchaseType.DIRECT,
    val financing: Financing = Financing.INTEREST_FREE,
    val installmentsText: String = "3",
    val teaPercentText: String = "",
    val rateSource: PurchaseRateSource? = null,
    val initializedCardId: String? = null,
) {
    fun forCard(card: CreditCard, products: List<CreditProductReference>): CreditPurchaseDraft {
        val current = if (initializedCardId == card.id.value) this else copy(
            initializedCardId = card.id.value, teaPercentText = "", rateSource = null,
        )
        if (current.rateSource != null || current.teaPercentText.isNotBlank()) return current
        val personal = card.personalTeaBps?.takeIf { it > 0 }
        val product = (resolveRateCatalogContext(card, products) as? RateCatalogContext.Resolved)?.product
        // The catalogue may publish a range: disclose that the suggested value is its minimum.
        val reference = product?.let {
            val bounds = if (card.currency == Currency.PEN) listOfNotNull(it.penTeaMinBps, it.penTeaMaxBps)
                else listOfNotNull(it.usdTeaMinBps, it.usdTeaMaxBps)
            bounds.filter { value -> value > 0 }.minOrNull()
        }
        val rate = personal ?: reference ?: return current
        return current.copy(
            teaPercentText = BigDecimal.valueOf(rate.toLong(), 2).toPlainString(),
            rateSource = if (personal != null) PurchaseRateSource.PERSONAL else PurchaseRateSource.REFERENCE,
        )
    }
}

data class CreditPurchaseValidation(
    val terms: PurchaseTerms? = null,
    val installmentsError: String? = null,
    val teaError: String? = null,
)

fun validateCreditPurchaseDraft(draft: CreditPurchaseDraft): CreditPurchaseValidation {
    if (draft.purchaseType == PurchaseType.DIRECT) return CreditPurchaseValidation(PurchaseTerms.interestFree())
    val count = draft.installmentsText.toIntOrNull()
    if (count == null || count !in 2..36) {
        return CreditPurchaseValidation(installmentsError = "Ingresa una cantidad de cuotas entre 2 y 36")
    }
    if (draft.financing == Financing.INTEREST_FREE) return CreditPurchaseValidation(PurchaseTerms.interestFree(count))
    val tea = runCatching {
        draft.teaPercentText.trim().replace(',', '.').toBigDecimal().movePointRight(2).intValueExact()
    }.getOrNull()
    if (tea == null || tea !in 1..50_000) {
        return CreditPurchaseValidation(teaError = "Ingresa una TEA mayor a 0% y de hasta 500%, con un máximo de dos decimales")
    }
    return CreditPurchaseValidation(PurchaseTerms(
        purchaseType = PurchaseType.INSTALLMENTS,
        financing = Financing.WITH_INTEREST,
        installmentsCount = count,
        teaBps = tea,
        rateSource = draft.rateSource ?: PurchaseRateSource.USER_ENTERED,
    ))
}

/** Shared saveable representation for locally hoisted purchase terms. */
val CreditPurchaseDraftSaver = androidx.compose.runtime.saveable.listSaver<CreditPurchaseDraft, String>(
    save = { it.toSavedValues() },
    restore = { CreditPurchaseDraft(
        purchaseType = PurchaseType.valueOf(it[0]), financing = Financing.valueOf(it[1]),
        installmentsText = it[2], teaPercentText = it[3],
        rateSource = PurchaseRateSource.entries.firstOrNull { source -> source.name == it[4] },
        initializedCardId = it[5].ifBlank { null },
    ) },
)

internal fun CreditPurchaseDraft.toSavedValues() = arrayListOf(purchaseType.name, financing.name,
    installmentsText, teaPercentText, rateSource?.name.orEmpty(), initializedCardId.orEmpty())
