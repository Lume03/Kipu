package com.kipu.app.feature.statements.domain

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal fun normalizedStatementText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

internal fun statementFingerprint(vararg fields: Any): String = MessageDigest.getInstance("SHA-256")
    .digest(fields.joinToString("") { "${it.toString().length}:$it" }.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

data class StatementRow(val words: List<StatementWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
    val y: Float get() = words.map { it.y0 }.average().toFloat()
}

object StatementLayout {
    fun rows(words: List<StatementWord>, tolerance: Float = 2.5f): List<StatementRow> {
        val rows = mutableListOf<MutableList<StatementWord>>()
        words.filter { it.text.isNotBlank() }.sortedBy { it.y0 }.forEach { word ->
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().y0 - word.y0) <= tolerance) row += word
            else rows += mutableListOf(word)
        }
        return rows.map { StatementRow(it.sortedBy { word -> word.x0 }) }
    }

    internal fun anchor(rows: List<StatementRow>, phrase: String): StatementWord? {
        val target = normalizedStatementText(phrase)
        for (row in rows) {
            for (start in row.words.indices) {
                var text = ""
                for (end in start until row.words.size) {
                    text = (text + " " + normalizedStatementText(row.words[end].text)).trim()
                    if (text == target) return row.words[start]
                    if (!target.startsWith(text)) break
                }
            }
        }
        return null
    }
}
