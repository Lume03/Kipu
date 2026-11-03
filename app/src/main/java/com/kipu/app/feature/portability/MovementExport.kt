package com.kipu.app.feature.portability

import com.kipu.app.feature.movements.data.local.TransactionEntity

/** Stable CSV representation; money remains in integer minor units. */
object MovementExport {
    fun csv(owner: String, transactions: List<TransactionEntity>): String = buildString {
        require(owner.isNotBlank())
        append("id,type,amount_minor,currency_code,occurred_at,note,source_account_id,destination_account_id,card_id,category_id,merchant_id\r\n")
        transactions.asSequence().filter { it.userId == owner }.sortedBy { it.id }.forEach { item ->
            append(listOf(item.id, item.type, item.amountMinor.toString(), item.currencyCode,
                item.occurredAt.toString(), item.note.orEmpty(), item.sourceAccountId.orEmpty(),
                item.destinationAccountId.orEmpty(), item.cardId.orEmpty(), item.categoryId.orEmpty(),
                item.merchantId.orEmpty()).joinToString(",") { escape(it) })
            append("\r\n")
        }
    }

    private fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else value
}
