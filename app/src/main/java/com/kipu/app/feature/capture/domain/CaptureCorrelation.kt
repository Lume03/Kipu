package com.kipu.app.feature.capture.domain

import java.text.Normalizer
import java.util.Locale

data class CorrelationFacts(
    val root: String, val source: String, val reference: String?, val amount: Long?,
    val currency: String?, val income: Boolean?, val counterparty: String?, val time: Long?,
)

/** HU-49 D1: similarity is a proposal, never authority to post or silently merge. */
object CaptureCorrelation {
    fun reference(value: String?): String? = value?.uppercase(Locale.ROOT)
        ?.replace(Regex("[^A-Z0-9]"), "")?.takeIf { it.isNotBlank() }
    private fun party(value: String?): String? = value?.let {
        Normalizer.normalize(it, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    }?.takeIf { it.isNotBlank() }
    fun sameReference(a: CorrelationFacts, b: CorrelationFacts): Boolean =
        reference(a.reference) != null && reference(a.reference) == reference(b.reference)
    fun compatible(a: CorrelationFacts, b: CorrelationFacts): Boolean {
        if (a.root == b.root || a.source == b.source) return false
        val ar = reference(a.reference); val br = reference(b.reference)
        if (ar != null && br != null) return false // equal references use the strong path
        return a.amount != null && a.amount > 0 && a.amount == b.amount &&
            a.currency != null && a.currency == b.currency && a.income != null && a.income == b.income &&
            party(a.counterparty) != null && party(a.counterparty) == party(b.counterparty) &&
            a.time != null && b.time != null && java.math.BigInteger.valueOf(a.time)
                .subtract(java.math.BigInteger.valueOf(b.time)).abs() <= java.math.BigInteger.valueOf(600_000)
    }
    fun suggestion(incoming: CorrelationFacts, existing: List<CorrelationFacts>): String? =
        existing.groupBy { it.root }.filter { (_, rows) -> rows.none { it.source == incoming.source } }
            .filter { (_, rows) ->
                !(reference(incoming.reference) != null && rows.any { reference(it.reference) != null }) &&
                    rows.any { compatible(incoming, it) }
            }.keys.singleOrNull()
}
