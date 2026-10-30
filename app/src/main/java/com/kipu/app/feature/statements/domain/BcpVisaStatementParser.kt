package com.kipu.app.feature.statements.domain

import com.kipu.app.core.finance.domain.model.Currency
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

class BcpVisaStatementParser @Inject constructor() : StatementParser {
    override fun canParse(pages: List<StatementPage>): Boolean {
        val text = normalizedStatementText(pages.flatMap { StatementLayout.rows(it.words) }.joinToString(" ") { it.text })
        return text.contains("ESTADO DE CUENTA TARJETA VISA") &&
            (text.replace(" ", "").contains("VIABCP") || Regex("\\bBCP\\b").containsMatchIn(text))
    }

    override fun parse(pages: List<StatementPage>): StatementParseResult {
        if (!canParse(pages)) return StatementParseResult.Unsupported
        return try {
            parseStatement(pages)?.let(StatementParseResult::Parsed) ?: StatementParseResult.InvalidStatement
        } catch (_: IllegalArgumentException) {
            StatementParseResult.InvalidStatement
        } catch (_: ArithmeticException) {
            StatementParseResult.InvalidStatement
        } catch (_: DateTimeException) {
            StatementParseResult.InvalidStatement
        }
    }

    private fun parseStatement(pages: List<StatementPage>): ParsedStatement? {
        val first = pages.minByOrNull { it.index } ?: return null
        val rows = StatementLayout.rows(first.words)
        val start = dateBelow(rows, "DEL") ?: return null
        val end = dateBelow(rows, "AL") ?: return null
        if (start > end) return null
        val due = dateBelow(rows, "FECHA LIMITE DE PAGO")
        val lastFour = Regex("\\b[0-9]{4}-[0-9Xx]{4}-[0-9Xx]{4}-([0-9]{4})\\b")
            .find(rows.joinToString(" ") { it.text })?.groupValues?.get(1) ?: return null
        val state = ParseState(start, end, lastFour)
        var columns: Columns? = null
        var section = Section.MOVEMENTS
        var installmentCurrency = Currency.PEN
        for (page in pages.sortedBy { it.index }) {
            val pageRows = StatementLayout.rows(page.words)
            val detected = findColumns(pageRows)
            if (detected != null) {
                columns = detected
                section = Section.MOVEMENTS
            }
            val layout = columns ?: continue
            for (row in pageRows) {
                val text = normalizedStatementText(row.text)
                if (text.startsWith("DETALLE PLAN CUOTAS")) {
                    section = Section.INSTALLMENTS
                    installmentCurrency = if (text.contains("DOLAR")) Currency.USD else Currency.PEN
                    continue
                }
                if (text.startsWith("TOTAL SOLES") || text.startsWith("TOTAL DOLARES") ||
                    text.contains("COMO ESTA COMPUESTA") || text.contains("COMO CALCULAR") ||
                    text.startsWith("PLAN DE CUOTAS DE LOS PROXIMOS")) {
                    section = Section.IGNORE
                    continue
                }
                if (row.y <= layout.headerY + 3) continue
                val dates = row.words.filter { shortDate.matches(normalizedStatementText(it.text)) && it.x0 < layout.descriptionStart }
                if (section == Section.INSTALLMENTS) {
                    if (dates.size >= 2) parseInstallment(row, dates, state, installmentCurrency)
                    continue
                }
                if (section == Section.IGNORE) continue
                when {
                    text.startsWith("SALDO ANTERIOR") -> state.previous = layout.amounts(row)
                    text.startsWith("SUB TOTAL") -> state.subtotal = layout.amounts(row)
                    text.startsWith("MONTO TOTAL FACTURADO") -> {
                        state.billed = layout.amounts(row)
                        section = Section.IGNORE
                    }
                    dates.size >= 2 -> parseMovement(row, dates, layout, state)
                    summaryKind(text) != null -> {
                        val description = row.words.filter { it.x0 < layout.amountStart }.joinToString(" ") { it.text }
                        for ((currency, amount) in layout.amounts(row)) {
                            if (amount != 0L) {
                                state.summary[currency] = Math.addExact(state.summary.getValue(currency), amount)
                                state.add(summaryKind(text)!!, end, end, description, abs(amount), currency)
                            }
                        }
                    }
                }
            }
        }
        val totals = Currency.entries.associateWith { currency ->
            StatementCurrencyTotals(state.previous?.get(currency) ?: state.previous?.let { 0L },
                state.subtotal?.get(currency) ?: state.subtotal?.let { 0L }, state.summary.getValue(currency),
                state.billed?.get(currency) ?: state.billed?.let { 0L })
        }
        val reconciliation = totals.mapValues { (currency, total) ->
            StatementReconciliation(
                if (total.previousBalanceMinor != null && total.subtotalMinor != null)
                    Math.subtractExact(Math.addExact(total.previousBalanceMinor, state.posted.getValue(currency)), total.subtotalMinor) else null,
                if (total.subtotalMinor != null && total.billedMinor != null)
                    Math.subtractExact(Math.addExact(total.subtotalMinor, total.summaryChargesMinor), total.billedMinor) else null,
            )
        }
        return ParsedStatement(ISSUER, lastFour, start, end, due, state.lines, totals, reconciliation,
            statementFingerprint(ISSUER, lastFour, start, end))
    }

    private fun parseMovement(row: StatementRow, dates: List<StatementWord>, columns: Columns, state: ParseState) {
        val process = inferDate(dates[0].text, state.start, state.end)
        val occurred = inferDate(dates[1].text, state.start, state.end, process)
        val operation = normalizedStatementText(row.words.filter { it.x0 >= columns.operationStart - 2 && it.x0 < columns.amountStart }
            .joinToString(" ") { it.text })
        val descriptionWords = row.words.filter { it.x0 >= columns.descriptionStart && it.x0 < columns.operationStart - 2 }
        // City and country are separate unlabeled BCP columns. Their boundaries scale with the header anchors.
        val baseDescription = descriptionWords.filter { it.x0 < columns.locationStart }.joinToString(" ") { it.text }.trim()
        val extendedDescription = descriptionWords.joinToString(" ") { it.text }
        val normalized = normalizedStatementText(extendedDescription)
        val kind = when {
            normalized.startsWith("CUOTA DEL MES") -> StatementLineKind.INSTALLMENT_MONTHLY_CHARGE
            normalized.startsWith("PAGO CON PUNTOS") -> StatementLineKind.POINTS_REDEMPTION
            operation == "PAGO" -> StatementLineKind.CARD_PAYMENT
            operation == "DEVOLUCION" -> StatementLineKind.REFUND
            operation == "CONSUMO" -> StatementLineKind.PURCHASE
            operation == "CARGO" -> summaryKind(normalized) ?: StatementLineKind.FEE
            else -> StatementLineKind.UNKNOWN
        }
        val description = if (kind in setOf(StatementLineKind.INSURANCE, StatementLineKind.INTEREST, StatementLineKind.FEE)) extendedDescription else baseDescription
        for ((currency, signedAmount) in columns.amounts(row)) {
            if (signedAmount == 0L) continue
            val actualKind = if (kind == StatementLineKind.PURCHASE && signedAmount < 0) StatementLineKind.REFUND else kind
            val amount = abs(signedAmount)
            val postedAmount = when (actualKind) {
                StatementLineKind.CARD_PAYMENT, StatementLineKind.POINTS_REDEMPTION, StatementLineKind.REFUND -> -amount
                StatementLineKind.UNKNOWN -> signedAmount
                else -> amount
            }
            state.posted[currency] = Math.addExact(state.posted.getValue(currency), postedAmount)
            if (actualKind != StatementLineKind.INSTALLMENT_MONTHLY_CHARGE) state.add(actualKind, process, occurred, description, amount, currency)
        }
    }

    private fun parseInstallment(row: StatementRow, dates: List<StatementWord>, state: ParseState, currency: Currency) {
        val installment = row.words.firstOrNull { installmentPattern.matches(it.text) } ?: return
        val (number, count) = installment.text.split('/').map(String::toInt)
        if (number != 1 || count < 2) return
        val purchase = row.words.lastOrNull { it.x0 < installment.x0 && moneyPattern.matches(it.text) } ?: return
        val amount = abs(parseMinor(purchase.text))
        if (amount == 0L) return
        val description = row.words.filter { it.x0 > dates[1].x0 && it.x0 < purchase.x0 }.joinToString(" ") { it.text }
        val process = inferDate(dates[0].text, state.start, state.end)
        val occurred = inferDate(dates[1].text, state.start, state.end, process)
        state.add(StatementLineKind.INSTALLMENT_PURCHASE, process, occurred, description, amount, currency, count)
    }

    private fun findColumns(rows: List<StatementRow>): Columns? {
        for ((index, row) in rows.withIndex()) {
            val pen = StatementLayout.anchor(listOf(row), "SOLES") ?: continue
            val usd = StatementLayout.anchor(listOf(row), "DOLARES") ?: continue
            val operation = StatementLayout.anchor(listOf(row), "TIPO DE OPERACION")
                ?: StatementLayout.anchor(listOf(row), "TIPO") ?: continue
            val process = StatementLayout.anchor(listOf(row), "FECHA DE PROCESO")
                ?: StatementLayout.anchor(listOf(row), "FECHA DE") ?: continue
            val span = operation.x0 - process.x0
            if (span <= 0 || pen.x0 <= operation.x0 || usd.x0 <= pen.x0) continue
            val headerBottom = rows.getOrNull(index + 1)?.takeIf { it.y - row.y < 12 && normalizedStatementText(it.text).contains("PROCESO") }?.y ?: row.y
            return Columns(process.x0 + span * .22f, process.x0 + span * .56f, operation.x0, pen.x0 - span * .04f, usd.x0 - 1f, headerBottom)
        }
        return null
    }

    private fun dateBelow(rows: List<StatementRow>, label: String): LocalDate? {
        val target = normalizedStatementText(label)
        val anchor = StatementLayout.anchor(rows, target) ?: rows.flatMap { it.words }.firstOrNull {
            normalizedStatementText(it.text).startsWith("$target ")
        } ?: return null
        val date = rows.flatMap { it.words }.filter {
            fullDate.matches(it.text) && it.y0 >= anchor.y0 - 2 && it.y0 <= anchor.y0 + 24
        }.minByOrNull { abs(it.x0 - anchor.x0) + abs(it.y0 - anchor.y0) * .1f } ?: return null
        return LocalDate.parse(date.text, if (date.text.length == 8) twoDigitDate else fourDigitDate)
    }

    private fun inferDate(text: String, start: LocalDate, end: LocalDate, latest: LocalDate = end): LocalDate {
        val normalized = normalizedStatementText(text)
        val month = months.getValue(normalized.substring(2))
        val day = normalized.take(2).toInt()
        val candidates = (start.year - 1..end.year + 1).mapNotNull { year ->
            runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }.filter { !it.isAfter(latest) }
        return candidates.maxOrNull() ?: throw IllegalArgumentException("Invalid statement date")
    }

    private fun summaryKind(text: String): StatementLineKind? = when {
        text.contains("INTERES") -> StatementLineKind.INTEREST
        text.contains("SEGURO") || text.contains("DESGRAVAMEN") -> StatementLineKind.INSURANCE
        text.startsWith("COMISION") || text.startsWith("ENVIO FISICO") -> StatementLineKind.FEE
        else -> null
    }

    private data class Columns(val descriptionStart: Float, val locationStart: Float, val operationStart: Float,
        val amountStart: Float, val usdStart: Float, val headerY: Float) {
        fun amounts(row: StatementRow): Map<Currency, Long> = row.words.filter { it.x1 > amountStart && moneyPattern.matches(it.text) }
            .associate { (if (it.x0 >= usdStart) Currency.USD else Currency.PEN) to parseMinor(it.text) }
    }

    private class ParseState(val start: LocalDate, val end: LocalDate, val lastFour: String) {
        val lines = mutableListOf<StatementLine>()
        val posted = Currency.entries.associateWith { 0L }.toMutableMap()
        val summary = Currency.entries.associateWith { 0L }.toMutableMap()
        var previous: Map<Currency, Long>? = null
        var subtotal: Map<Currency, Long>? = null
        var billed: Map<Currency, Long>? = null
        private val repetitions = mutableMapOf<String, Int>()

        fun add(kind: StatementLineKind, process: LocalDate, occurred: LocalDate, description: String, amount: Long,
            currency: Currency, installmentCount: Int? = null) {
            val base = statementFingerprint(ISSUER, lastFour, occurred, amount, currency, kind, normalizedStatementText(description))
            val ordinal = repetitions.getOrDefault(base, 0)
            repetitions[base] = ordinal + 1
            lines += StatementLine(kind, process, occurred, description, amount, currency,
                installmentNumber = installmentCount?.let { 1 }, installmentCount = installmentCount,
                installmentTotalMinor = installmentCount?.let { amount },
                defaultSelected = kind !in setOf(StatementLineKind.CARD_PAYMENT, StatementLineKind.POINTS_REDEMPTION, StatementLineKind.UNKNOWN),
                fingerprint = statementFingerprint(base, ordinal))
        }
    }

    private enum class Section { MOVEMENTS, INSTALLMENTS, IGNORE }

    companion object {
        const val ISSUER = "BCP"
        private val shortDate = Regex("[0-9]{2}(ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SET|SEP|OCT|NOV|DIC)")
        private val fullDate = Regex("[0-9]{2}/[0-9]{2}/(?:[0-9]{4}|[0-9]{2})")
        private val moneyPattern = Regex("-?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\.[0-9]{2}-?")
        private val installmentPattern = Regex("[0-9]{1,2}/[0-9]{1,2}")
        private val twoDigitDate = DateTimeFormatter.ofPattern("dd/MM/uu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
        private val fourDigitDate = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
        private val months = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SET", "OCT", "NOV", "DIC")
            .mapIndexed { index, month -> month to index + 1 }.toMap() + ("SEP" to 9)
        private fun parseMinor(text: String): Long {
            val negative = text.endsWith('-') || text.startsWith('-')
            val amount = BigDecimal(text.trim('-').replace(",", "")).movePointRight(2).longValueExact()
            return if (negative) Math.negateExact(amount) else amount
        }
    }
}
