package com.kipu.app.feature.statements.domain

import com.kipu.app.core.finance.domain.model.Currency
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

class BcpSavingsStatementParser @Inject constructor() : StatementParser {
    override fun canParse(pages: List<StatementPage>): Boolean {
        val text = normalizedStatementText(pages.flatMap { StatementLayout.rows(it.words) }.joinToString(" ") { it.text })
        return text.contains("ESTADO DE CUENTA DE AHORROS") && Regex("\\bBCP\\b").containsMatchIn(text)
    }

    override fun parse(pages: List<StatementPage>): StatementParseResult {
        if (!canParse(pages)) return StatementParseResult.Unsupported
        return try { parseAccount(pages)?.let(StatementParseResult::Parsed) ?: StatementParseResult.InvalidStatement }
        catch (_: IllegalArgumentException) { StatementParseResult.InvalidStatement }
        catch (_: ArithmeticException) { StatementParseResult.InvalidStatement }
        catch (_: java.time.DateTimeException) { StatementParseResult.InvalidStatement }
    }

    private fun parseAccount(pages: List<StatementPage>): ParsedStatement? {
        val firstRows = StatementLayout.rows(pages.minByOrNull { it.index }?.words ?: return null)
        val header = normalizedStatementText(firstRows.joinToString(" ") { it.text })
        val period = Regex("\\bDEL\\s+(\\d{2}/\\d{2}/\\d{2,4})\\s+AL\\s+(\\d{2}/\\d{2}/\\d{2,4})").find(header) ?: return null
        fun date(s: String): LocalDate = LocalDate.parse(s, DateTimeFormatter.ofPattern(if (s.length == 8) "dd/MM/uu" else "dd/MM/uuuu", Locale.ROOT))
        val start = date(period.groupValues[1]); val end = date(period.groupValues[2])
        require(start <= end)
        val account = Regex("\\b\\d{3}-\\d+-\\d+-\\d+\\b").find(header)?.value?.filter(Char::isDigit)?.takeLast(4) ?: return null
        val currency = when {
            Regex("\\bSOLES\\b").containsMatchIn(header) -> Currency.PEN
            Regex("\\bDOLARES\\b").containsMatchIn(header) -> Currency.USD
            else -> return null
        }
        val titleIndex = firstRows.indexOfFirst { normalizedStatementText(it.text).contains("ESTADO DE CUENTA DE AHORROS") }
        val holder = firstRows.drop(titleIndex + 1).firstOrNull()?.words?.takeWhile { it.x0 < (pages.minByOrNull { it.index }?.width ?: 595f) / 2 }?.joinToString(" ") { it.text }.orEmpty()
        val names = normalizedStatementText(holder).split(' ').filter { it.isNotBlank() }
        val surname = names.firstOrNull().orEmpty(); val given = names.getOrNull(2).orEmpty()
        val lines = mutableListOf<StatementLine>()
        val repetitions = mutableMapOf<String, Int>()
        var previous: Long? = null; var debits: Long? = null; var credits: Long? = null; var closing: Long? = null
        for (page in pages.sortedBy { it.index }) {
            val rows = StatementLayout.rows(page.words)
            val debitAnchor = StatementLayout.anchor(rows, "CARGOS / DEBE") ?: continue
            val creditAnchor = StatementLayout.anchor(rows, "ABONOS / HABER") ?: continue
            val boundary = (debitAnchor.x0 + creditAnchor.x0) / 2
            fun amounts(words: List<StatementWord>) = words.filter { it.x0 >= debitAnchor.x0 && money.matches(it.text) }
            fun amount(word: StatementWord) = BigDecimal(word.text.replace(",", "")).movePointRight(2).longValueExact()
            fun infer(value: String, latest: LocalDate = end): LocalDate {
                val v = normalizedStatementText(value)
                val month = months.getValue(v.substring(2)); val day = v.take(2).toInt()
                return (start.year - 1..end.year + 1).mapNotNull { runCatching { LocalDate.of(it, month, day) }.getOrNull() }.filter { !it.isAfter(latest) }.maxOrNull() ?: throw IllegalArgumentException()
            }
            for (row in rows) {
                if (row.y <= debitAnchor.y0 + 2) continue
                val text = normalizedStatementText(row.text)
                if (text.contains("SALDO ANTERIOR")) { previous = amounts(row.words).lastOrNull()?.let(::amount); continue }
                if (text.startsWith("TOTAL MOVIMIENTO") || text == "SALDO") {
                    val nearby = amounts(page.words.filter { abs(it.y0 - row.y) <= 12f })
                    if (text.startsWith("TOTAL MOVIMIENTO")) {
                        nearby.filter { it.x0 < boundary }.minByOrNull { abs(it.y0 - row.y) }?.let { debits = amount(it) }
                        nearby.filter { it.x0 >= boundary }.minByOrNull { abs(it.y0 - row.y) }?.let { credits = amount(it) }
                    } else nearby.filter { it.x0 >= boundary }.minByOrNull { abs(it.y0 - row.y) }?.let { closing = amount(it) }
                    continue
                }
                val dates = row.words.filter { it.x0 < debitAnchor.x0 && shortDate.matches(normalizedStatementText(it.text)) }
                if (dates.size < 2) continue
                val values = amounts(row.words)
                if (values.size != 1) continue
                val value = amount(values.single()); if (value == 0L) continue
                require(value > 0)
                val process = infer(dates[0].text); val occurred = infer(dates[1].text, process)
                val description = row.words.filter { it.x0 > dates[1].x0 && it.x0 < debitAnchor.x0 }.joinToString(" ") { it.text }.trim()
                val kind = if (values.single().x0 < boundary) StatementLineKind.ACCOUNT_DEBIT else StatementLineKind.ACCOUNT_CREDIT
                val normalized = normalizedStatementText(description)
                // Transfers bearing the holder's first name and surname prefix may be own-account transfers.
                val own = given.isNotBlank() && surname.length >= 3 && Regex("\\b${Regex.escape(given)}\\s+${Regex.escape(surname.take(3))}").containsMatchIn(normalized)
                val base = statementFingerprint("BCP", account, occurred, value, currency, kind, normalized)
                val ordinal = repetitions.getOrDefault(base, 0); repetitions[base] = ordinal + 1
                val possibleOwnTransfer = own || normalized.startsWith("TRANSF.BCO.")
                lines += StatementLine(kind, process, occurred, description, value, currency,
                    defaultSelected = !possibleOwnTransfer, fingerprint = statementFingerprint(base, ordinal), possibleOwnTransfer = possibleOwnTransfer)
            }
        }
        val sumDebit = lines.filter { it.kind == StatementLineKind.ACCOUNT_DEBIT }.fold(0L) { sum, line -> Math.addExact(sum, line.amountMinor) }
        val sumCredit = lines.filter { it.kind == StatementLineKind.ACCOUNT_CREDIT }.fold(0L) { sum, line -> Math.addExact(sum, line.amountMinor) }
        val total = StatementCurrencyTotals(previous, null, 0L, null, debits, credits, closing)
        val reconciliation = StatementReconciliation(debits?.let { Math.subtractExact(sumDebit, it) },
            if (previous != null && debits != null && credits != null && closing != null) Math.subtractExact(Math.addExact(Math.subtractExact(previous, debits), credits), closing) else null,
            credits?.let { Math.subtractExact(sumCredit, it) })
        return ParsedStatement("BCP", "", start, end, null, lines, mapOf(currency to total), mapOf(currency to reconciliation),
            statementFingerprint("BCP", StatementSource.DEPOSIT_ACCOUNT, account, start, end), StatementSource.DEPOSIT_ACCOUNT, account)
    }
    companion object {
        private val money = Regex("(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\.[0-9]{2}")
        private val shortDate = Regex("[0-9]{2}(ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SET|SEP|OCT|NOV|DIC)")
        private val months = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SET", "OCT", "NOV", "DIC").mapIndexed { i, m -> m to i + 1 }.toMap() + ("SEP" to 9)
    }
}
