package com.kipu.app.feature.categories.domain

import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry

/** Conservative suggestions only; callers must ask the user before assigning a match. */
object MerchantTextMatcher {
    private val excluded = Regex("^(yape|plin|transf|transferencia|deposito|pago[0-9]*|mant|mantenimiento)(?:[^a-z]|$)")
    private val processor = Regex("^(?:(?:dlc|yq|yp|izi|payu|merpago|mercadopago|niubiz|culqi|dl|bcp)[ *-]+)+")
    fun suggest(rawText: String, catalog: Iterable<MerchantCatalogEntry>): MerchantCatalogEntry? {
        var text = MerchantAliasRules.normalize(rawText)
        if (excluded.containsMatchIn(text)) return null
        text = text.replace(processor, "")
        if (excluded.containsMatchIn(text)) return null
        text = text.replace(Regex("\\.com\\b"), "")
            .replace(Regex("\\bsteamgames\\b"), "steam")
            .replace(Regex("\\bt?\\d+\\b"), " ")
        val clean = words(text)
        return catalog.filter { it.isActive && words(it.normalizedName).isNotBlank() &&
            (" $clean ").contains(" ${words(it.normalizedName)} ") }.distinctBy { it.id }.singleOrNull()
    }
    private fun words(text: String) = MerchantAliasRules.normalize(text).replace(Regex("[^a-z0-9]+"), " ").trim()
}
