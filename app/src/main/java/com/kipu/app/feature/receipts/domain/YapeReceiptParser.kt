package com.kipu.app.feature.receipts.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

data class YapeReceiptFields(
    val amountMinor: Long? = null,
    val occurredAt: Long? = null,
    val recipient: String? = null,
    val reference: String? = null,
    val issues: List<String> = emptyList(),
    val isIncome: Boolean? = null
)

class YapeReceiptParser @Inject constructor() {

    fun parse(text: String): YapeReceiptFields {
        if (text.isBlank()) {
            return YapeReceiptFields(
                amountMinor = null,
                occurredAt = null,
                recipient = null,
                reference = null,
                issues = listOf(
                    ISSUE_EMPTY_TEXT,
                    ISSUE_MISSING_AMOUNT,
                    ISSUE_MISSING_DATE_TIME,
                    ISSUE_MISSING_RECIPIENT,
                    ISSUE_MISSING_REFERENCE
                ),
                isIncome = null
            )
        }

        val issues = mutableListOf<String>()

        val rawLines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // 0. Direction extraction (Income vs Expense)
        val isIncome = extractIsIncome(text)

        // 1. Amount extraction
        val amount = extractAmount(rawLines, text, issues)

        // 2. Date & Time extraction (in America/Lima, strictly without fallback to current time)
        val occurredAt = extractOccurredAt(rawLines, text, issues)

        // 3. Recipient extraction (prioritizing line between amount and date)
        val recipient = extractRecipient(rawLines, issues)

        // 4. Reference extraction (ignoring 3-digit security codes, preserving leading zeros)
        val reference = extractReference(rawLines, issues)

        return YapeReceiptFields(
            amountMinor = amount,
            occurredAt = occurredAt,
            recipient = recipient,
            reference = reference,
            issues = issues.toList(),
            isIncome = isIncome
        )
    }

    private fun extractIsIncome(fullText: String): Boolean? {
        val hasIncome = INCOME_HEADER_REGEX.containsMatchIn(fullText)
        val hasExpense = EXPENSE_HEADER_REGEX.containsMatchIn(fullText)

        return when {
            hasIncome && !hasExpense -> true
            !hasIncome && hasExpense -> false
            else -> null
        }
    }

    private fun extractAmount(lines: List<String>, fullText: String, issues: MutableList<String>): Long? {
        val candidates = mutableListOf<String>()

        // Check for currency prefix e.g. "S/ 12.50", "S/. 1,200.50", "-S/ 10.00", "S/20"
        val currencyMatches = CURRENCY_AMOUNT_REGEX.findAll(fullText)
        for (m in currencyMatches) {
            candidates.add(m.groupValues[1].trim())
        }

        // Check for split lines: e.g. line i is "S/" or "S/." and line i+1 is "12.50" or "20"
        for (i in 0 until lines.size - 1) {
            val curr = lines[i]
            if (curr.matches(SPLIT_CURRENCY_SYMBOL_REGEX)) {
                val next = lines[i + 1]
                if (next.matches(NUMERIC_TOKEN_REGEX)) {
                    candidates.add(next)
                }
            }
        }

        // If no currency matches, check labeled amount: "Monto: 12.50", "Total: S/ 10.00"
        if (candidates.isEmpty()) {
            val labeledMatches = LABELED_AMOUNT_REGEX.findAll(fullText)
            for (m in labeledMatches) {
                candidates.add(m.groupValues[1].trim())
            }
        }

        // If still no candidate, check for lines that consist solely of a decimal amount
        if (candidates.isEmpty()) {
            for (line in lines) {
                if (STANDALONE_DECIMAL_REGEX.matches(line)) {
                    candidates.add(line)
                }
            }
        }

        if (candidates.isEmpty()) {
            issues.add(ISSUE_MISSING_AMOUNT)
            return null
        }

        val parsedResults = candidates.map { parseAmountCandidate(it) }

        if (parsedResults.any { it is ParsedAmount.Overflow }) {
            issues.add(ISSUE_AMOUNT_OVERFLOW)
            return null
        }

        val validAmounts = parsedResults
            .filterIsInstance<ParsedAmount.Valid>()
            .map { it.minorUnits }
            .distinct()

        val hasZeroOrNegative = parsedResults.any { it is ParsedAmount.ZeroOrNegative }

        return when {
            validAmounts.size > 1 -> {
                issues.add(ISSUE_AMBIGUOUS_AMOUNT)
                null
            }
            validAmounts.size == 1 -> {
                if (hasZeroOrNegative) {
                    issues.add(ISSUE_AMBIGUOUS_AMOUNT)
                    null
                } else {
                    validAmounts.first()
                }
            }
            hasZeroOrNegative -> {
                issues.add(ISSUE_ZERO_OR_NEGATIVE_AMOUNT)
                null
            }
            else -> {
                issues.add(ISSUE_MISSING_AMOUNT)
                null
            }
        }
    }

    private fun parseAmountCandidate(raw: String): ParsedAmount {
        if (raw.contains('-')) {
            return ParsedAmount.ZeroOrNegative
        }

        // Strip currency symbols and prefixes
        var cleaned = raw
            .replace(Regex("""(?i)s\s*\/\.?|pen|soles|monto\s*:?|importe\s*:?|total\s*:?"""), "")
            .replace("+", "")
            .trim()
            .trimEnd('.', ',')
            .trim()

        if (cleaned.isEmpty() || cleaned.any { !it.isDigit() && it != '.' && it != ',' }) {
            return ParsedAmount.Invalid
        }

        val dotCount = cleaned.count { it == '.' }
        val commaCount = cleaned.count { it == ',' }

        return try {
            when {
                // No separators at all: integer soles e.g. "120", "20"
                dotCount == 0 && commaCount == 0 -> {
                    val whole = cleaned.toLongOrNull() ?: return ParsedAmount.Overflow
                    if (whole > MAX_AMOUNT_MINOR / 100L) return ParsedAmount.Overflow
                    val total = whole * 100L
                    if (total <= 0L) ParsedAmount.ZeroOrNegative else ParsedAmount.Valid(total)
                }

                // Exactly one separator type: either only dot(s) or only comma(s)
                (dotCount > 0 && commaCount == 0) || (commaCount > 0 && dotCount == 0) -> {
                    val sep = if (dotCount > 0) '.' else ','
                    val count = if (dotCount > 0) dotCount else commaCount

                    if (count == 1) {
                        val parts = cleaned.split(sep)
                        val wholeStr = parts[0]
                        val fracStr = parts[1]

                        if (fracStr.length in 1..2) {
                            val whole = if (wholeStr.isEmpty()) 0L else (wholeStr.toLongOrNull() ?: return ParsedAmount.Overflow)
                            val frac = if (fracStr.length == 1) {
                                (fracStr[0] - '0') * 10L
                            } else {
                                (fracStr[0] - '0') * 10L + (fracStr[1] - '0')
                            }
                            if (whole > (MAX_AMOUNT_MINOR - frac) / 100L) return ParsedAmount.Overflow
                            val total = whole * 100L + frac
                            if (total <= 0L) ParsedAmount.ZeroOrNegative else ParsedAmount.Valid(total)
                        } else {
                            // Single separator followed by 3 digits is ambiguous (grouping vs cents)
                            ParsedAmount.Invalid
                        }
                    } else {
                        // Multiple identical separators: must be 3-digit thousands grouping without decimals
                        val groups = cleaned.split(sep)
                        if (groups.first().length !in 1..3 || groups.drop(1).any { it.length != 3 }) {
                            ParsedAmount.Invalid
                        } else {
                            val whole = groups.joinToString("").toLongOrNull() ?: return ParsedAmount.Overflow
                            if (whole > MAX_AMOUNT_MINOR / 100L) return ParsedAmount.Overflow
                            val total = whole * 100L
                            if (total <= 0L) ParsedAmount.ZeroOrNegative else ParsedAmount.Valid(total)
                        }
                    }
                }

                // Both separators present: e.g. "1,200.50" or "1.200,50"
                else -> {
                    val lastDot = cleaned.lastIndexOf('.')
                    val lastComma = cleaned.lastIndexOf(',')
                    val (decSep, grpSep, decIdx) = if (lastDot > lastComma) {
                        Triple('.', ',', lastDot)
                    } else {
                        Triple(',', '.', lastComma)
                    }

                    val fracStr = cleaned.substring(decIdx + 1)
                    if (fracStr.length !in 1..2) return ParsedAmount.Invalid

                    val wholeStr = cleaned.substring(0, decIdx)
                    if (wholeStr.contains(decSep)) return ParsedAmount.Invalid

                    val groups = wholeStr.split(grpSep)
                    if (groups.first().length !in 1..3 || groups.drop(1).any { it.length != 3 }) {
                        return ParsedAmount.Invalid
                    }

                    val whole = groups.joinToString("").toLongOrNull() ?: return ParsedAmount.Overflow
                    val frac = if (fracStr.length == 1) {
                        (fracStr[0] - '0') * 10L
                    } else {
                        (fracStr[0] - '0') * 10L + (fracStr[1] - '0')
                    }
                    if (whole > (MAX_AMOUNT_MINOR - frac) / 100L) return ParsedAmount.Overflow
                    val total = whole * 100L + frac
                    if (total <= 0L) ParsedAmount.ZeroOrNegative else ParsedAmount.Valid(total)
                }
            }
        } catch (_: ArithmeticException) {
            ParsedAmount.Overflow
        }
    }

    private fun extractOccurredAt(lines: List<String>, fullText: String, issues: MutableList<String>): Long? {
        val date = extractLocalDate(lines, fullText)
        val time = extractLocalTime(lines, fullText)

        if (date == null || time == null) {
            issues.add(ISSUE_MISSING_DATE_TIME)
            return null
        }

        return try {
            val zonedDateTime = ZonedDateTime.of(date, time, LIMA_ZONE)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            issues.add(ISSUE_INVALID_DATE_TIME)
            null
        }
    }

    private fun extractLocalDate(lines: List<String>, fullText: String): LocalDate? {
        // 1. Spanish named month: e.g. "21 oct. 2026", "21 de octubre de 2026", "21 oct 2026"
        val wordMatch = SPANISH_NAMED_DATE_REGEX.find(fullText)
        if (wordMatch != null) {
            val day = wordMatch.groupValues[1].toIntOrNull() ?: return null
            val rawMonth = wordMatch.groupValues[2].lowercase().removeSuffix(".")
            val month = SPANISH_MONTH_MAP[rawMonth] ?: return null
            val year = wordMatch.groupValues[3].toIntOrNull() ?: return null
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }

        // 2. Numeric date: e.g. "21/10/2026", "21-10-2026"
        val numMatch = NUMERIC_DATE_REGEX.find(fullText)
        if (numMatch != null) {
            val day = numMatch.groupValues[1].toIntOrNull() ?: return null
            val month = numMatch.groupValues[2].toIntOrNull() ?: return null
            val year = numMatch.groupValues[3].toIntOrNull() ?: return null
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }

        return null
    }

    private fun extractLocalTime(lines: List<String>, fullText: String): LocalTime? {
        // Search time pattern: e.g. "10:35 a. m.", "10:35 p. m.", "10:35 am", "14:35"
        val matches = TIME_REGEX.findAll(fullText)
        for (match in matches) {
            val rawH = match.groupValues[1].toIntOrNull() ?: continue
            val rawM = match.groupValues[2].toIntOrNull() ?: continue
            val rawS = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val ampm = match.groupValues[4].lowercase().replace(".", "").replace(" ", "").trim()

            val hour = when (ampm) {
                "am", "a" -> {
                    if (rawH !in 1..12) continue
                    if (rawH == 12) 0 else rawH
                }
                "pm", "p" -> {
                    if (rawH !in 1..12) continue
                    if (rawH == 12) 12 else rawH + 12
                }
                "" -> {
                    if (rawH !in 0..23) continue
                    rawH
                }
                else -> continue
            }

            if (rawM !in 0..59 || rawS !in 0..59) continue
            val parsedTime = runCatching { LocalTime.of(hour, rawM, rawS) }.getOrNull()
            if (parsedTime != null) {
                return parsedTime
            }
        }

        return null
    }

    private fun extractRecipient(lines: List<String>, issues: MutableList<String>): String? {
        // Priority 1: Positional - line between amount and date
        val amountIdx = findAmountLineIndex(lines)
        val dateIdx = findDateLineIndex(lines)

        if (amountIdx != -1 && dateIdx != -1 && amountIdx < dateIdx) {
            val candidate = extractRecipientBetweenAmountAndDate(lines, amountIdx, dateIdx)
            if (candidate != null) {
                return candidate
            }
        }

        // Priority 2: Labeled "Yapeaste a: [Name]" or "Yapeaste a [Name]"
        for (i in lines.indices) {
            val line = lines[i]
            val yapeasteMatch = YAPEASTE_A_REGEX.find(line)
            if (yapeasteMatch != null) {
                val candidateOnSameLine = sanitizeRecipientCandidate(yapeasteMatch.groupValues[1].trim())
                if (candidateOnSameLine != null && isValidRecipientName(candidateOnSameLine)) {
                    return candidateOnSameLine
                }
                if (i + 1 < lines.size) {
                    val candidateNextLine = sanitizeRecipientCandidate(lines[i + 1])
                    if (candidateNextLine != null && isValidRecipientName(candidateNextLine)) {
                        return candidateNextLine
                    }
                }
            }
        }

        // Priority 3: Labeled "Destinatario: [Name]" or "Para: [Name]"
        for (i in lines.indices) {
            val line = lines[i]
            val labelMatch = RECIPIENT_LABEL_REGEX.find(line)
            if (labelMatch != null) {
                val candidateOnSameLine = sanitizeRecipientCandidate(labelMatch.groupValues[1].trim())
                if (candidateOnSameLine != null && isValidRecipientName(candidateOnSameLine)) {
                    return candidateOnSameLine
                }
                if (i + 1 < lines.size) {
                    val candidateNextLine = sanitizeRecipientCandidate(lines[i + 1])
                    if (candidateNextLine != null && isValidRecipientName(candidateNextLine)) {
                        return candidateNextLine
                    }
                }
            }
        }

        // Unanchored prose or noise is not evidence of a counterparty.
        issues.add(ISSUE_MISSING_RECIPIENT)
        return null
    }

    private fun extractRecipientBetweenAmountAndDate(
        lines: List<String>,
        amountIdx: Int,
        dateIdx: Int
    ): String? {
        val candidates = lines.subList(amountIdx + 1, dateIdx)
        for (i in candidates.indices) {
            val line = candidates[i]

            val yapeasteMatch = YAPEASTE_A_REGEX.find(line)
            if (yapeasteMatch != null) {
                val candSame = sanitizeRecipientCandidate(yapeasteMatch.groupValues[1].trim())
                if (candSame != null && isValidRecipientName(candSame)) {
                    return candSame
                }
                if (i + 1 < candidates.size) {
                    val candNext = sanitizeRecipientCandidate(candidates[i + 1])
                    if (candNext != null && isValidRecipientName(candNext)) {
                        return candNext
                    }
                }
            }

            val sanitized = sanitizeRecipientCandidate(line)
            if (sanitized != null && isValidRecipientName(sanitized)) {
                return sanitized
            }
        }
        return null
    }

    private fun findAmountLineIndex(lines: List<String>): Int {
        for (i in lines.indices) {
            val line = lines[i]
            if (CURRENCY_AMOUNT_REGEX.containsMatchIn(line)) {
                return i
            }
            if (SPLIT_CURRENCY_SYMBOL_REGEX.matches(line)) {
                if (i + 1 < lines.size && NUMERIC_TOKEN_REGEX.matches(lines[i + 1])) {
                    return i + 1
                }
                // The Yape logo also contains S/; it is not an amount anchor without digits.
                continue
            }
            if (LABELED_AMOUNT_REGEX.containsMatchIn(line)) {
                return i
            }
            if (STANDALONE_DECIMAL_REGEX.matches(line)) {
                return i
            }
        }
        return -1
    }

    private fun findDateLineIndex(lines: List<String>): Int {
        for (i in lines.indices) {
            val line = lines[i]
            if (SPANISH_NAMED_DATE_REGEX.containsMatchIn(line) || NUMERIC_DATE_REGEX.containsMatchIn(line)) {
                return i
            }
        }
        return -1
    }

    private fun sanitizeRecipientCandidate(text: String): String? {
        var clean = text
            .replace("¡", "")
            .replace("!", "")
            .trim()

        clean = clean
            .replace(Regex("""(?i)^(?:destinatario|para|destino|contacto|servicio|titular|yapeaste\s+a)\s*:?\s*"""), "")
            .replace(Regex("""^[aA]\s+"""), "")
            .trim()
            .removeSuffix(":")
            .removeSuffix(".")
            .trim()

        if (clean.length < 2 || clean.length > 70) return null
        return clean
    }

    private fun isValidRecipientName(name: String): Boolean {
        val lower = name.lowercase().trim()

        if (lower.none { it.isLetter() }) return false

        // Check against known system words
        if (BLACKLISTED_WORDS.contains(lower)) return false
        if (BLACKLISTED_WORDS.any { lower.startsWith("$it:") || lower.startsWith("$it ") || lower == it }) {
            if (!lower.startsWith("bcp - ") && !lower.startsWith("plin - ")) {
                return false
            }
        }

        // Do not accept currency tokens or date strings
        if (lower.contains("s/") || lower.contains("s/.") || lower.contains("pen")) return false
        if (SPANISH_NAMED_DATE_REGEX.containsMatchIn(name) || NUMERIC_DATE_REGEX.containsMatchIn(name)) return false
        if (TIME_REGEX.containsMatchIn(name)) return false

        // Must not contain operation, security code or phone keywords
        if (lower.contains("operaci") || lower.contains("seguridad") || lower.contains("celular")) return false

        return true
    }

    private fun extractReference(lines: List<String>, issues: MutableList<String>): String? {
        for (i in lines.indices) {
            val line = lines[i]

            // Defensive: ignore any line mentioning security code (3 digits)
            if (line.contains("seguridad", ignoreCase = true)) {
                continue
            }

            val match = OPERATION_LABEL_REGEX.find(line)
            if (match != null) {
                val candidateOnSameLine = cleanReferenceCandidate(match.groupValues[1])
                if (isValidReference(candidateOnSameLine)) {
                    return candidateOnSameLine
                }

                // Check next line if label is standalone
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (!nextLine.contains("seguridad", ignoreCase = true)) {
                        val candidateNextLine = cleanReferenceCandidate(nextLine)
                        if (isValidReference(candidateNextLine)) {
                            return candidateNextLine
                        }
                    }
                }
            }
        }

        issues.add(ISSUE_MISSING_REFERENCE)
        return null
    }

    private fun cleanReferenceCandidate(raw: String): String {
        val withoutSuffix = raw.replace(Regex("""(?i)\b(?:copiar|compartir|ver)\b.*$"""), "").trim()
        return withoutSuffix
            .replace(Regex("""^[#:]+"""), "")
            .replace(" ", "")
            .replace("-", "")
            .trim()
    }

    private fun isValidReference(ref: String): Boolean {
        // Defensive: must NOT be a 3-digit security code
        if (ref.length < 4 || ref.length > 32) return false
        // Must contain digits
        if (!ref.any { it.isDigit() }) return false
        // Must not be a date or time or contain masked phone asterisk
        if (ref.contains(':') || ref.contains('/') || ref.contains('*')) return false
        // Operation numbers should be alphanumeric without punctuation
        return ref.all { it.isLetterOrDigit() }
    }

    private sealed interface ParsedAmount {
        data class Valid(val minorUnits: Long) : ParsedAmount
        data object ZeroOrNegative : ParsedAmount
        data object Overflow : ParsedAmount
        data object Invalid : ParsedAmount
    }

    companion object {
        val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")
        const val MAX_AMOUNT_MINOR: Long = 99_999_999_999_999L

        const val ISSUE_EMPTY_TEXT = "EMPTY_TEXT"
        const val ISSUE_MISSING_AMOUNT = "MISSING_AMOUNT"
        const val ISSUE_AMBIGUOUS_AMOUNT = "AMBIGUOUS_AMOUNT"
        const val ISSUE_ZERO_OR_NEGATIVE_AMOUNT = "ZERO_OR_NEGATIVE_AMOUNT"
        const val ISSUE_AMOUNT_OVERFLOW = "AMOUNT_OVERFLOW"

        const val ISSUE_MISSING_DATE_TIME = "MISSING_DATE_TIME"
        const val ISSUE_INVALID_DATE_TIME = "INVALID_DATE_TIME"

        const val ISSUE_MISSING_RECIPIENT = "MISSING_RECIPIENT"
        const val ISSUE_MISSING_REFERENCE = "MISSING_REFERENCE"

        // OCR sometimes reads the opening ¡ as i/l. Keep that tolerance limited to headers.
        private val INCOME_HEADER_REGEX = Regex("""(?i)\b[il|]?(?:te\s+yapear[oó]n|te\s+yape[oó])[il|]?\b""")
        private val EXPENSE_HEADER_REGEX = Regex("""(?i)\b[il|]?yapeaste[il|]?\b""")

        private val CURRENCY_AMOUNT_REGEX = Regex(
            """(?i)(?:^|[^\w/])([+-]?\s*(?:S\s*\/\.?|PEN|SOLES)\s*[+-]?\s*\d[\d.,]*)"""
        )

        private val LABELED_AMOUNT_REGEX = Regex(
            """(?i)(?:monto|importe|total)\s*:?\s*([+-]?\s*(?:S\s*\/\.?|PEN|SOLES)?\s*[+-]?\s*\d[\d.,]*)"""
        )

        private val SPLIT_CURRENCY_SYMBOL_REGEX = Regex("""(?i)^(?:S\s*\/\.?|PEN|SOLES)$""")
        private val NUMERIC_TOKEN_REGEX = Regex("""^[+-]?\d[\d.,]*$""")
        private val STANDALONE_DECIMAL_REGEX = Regex("""^[+-]?\s*\d[\d.,]*[.,]\d{1,2}$""")

        private val SPANISH_NAMED_DATE_REGEX = Regex(
            """(?i)\b(\d{1,2})\s*(?:de\s+)?([a-záéíóúÁÉÍÓÚ]{3,12}\.?)\s*(?:de\s+|del\s+)?(\d{4})\b"""
        )

        private val NUMERIC_DATE_REGEX = Regex(
            """\b(\d{1,2})[/-](\d{1,2})[/-](\d{4})\b"""
        )

        private val TIME_REGEX = Regex(
            """(?i)\b(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\s*([ap]\.?\s*m\.?|[ap]m))?"""
        )

        private val YAPEASTE_A_REGEX = Regex(
            """(?i)(?:^|\b)yapeaste\s+a\s*:?\s*(.*)$"""
        )

        private val RECIPIENT_LABEL_REGEX = Regex(
            """(?i)(?:^|\b)(?:destinatario|nombre\s+del\s+destinatario|para|contacto)\s*:?\s*(.*)$"""
        )

        private val OPERATION_LABEL_REGEX = Regex(
            """(?i)(?:n(?:[úu]mero|ro\.?|um\.?|[°ºo])?\s*de\s*operaci[óo]n|c[óo]digo\s*de\s*operaci[óo]n|cod\.?\s*de\s*operaci[óo]n|operaci[óo]n\s*[#:]|ref(?:erencia)?\s*[#:]|pago\s+por\s+internet\s*:?\s*(?:n[°ºo.]*)?)\s*[#:]?\s*(.*)$"""
        )

        private val SPANISH_MONTH_MAP = mapOf(
            "ene" to 1, "enero" to 1,
            "feb" to 2, "febrero" to 2,
            "mar" to 3, "marzo" to 3,
            "abr" to 4, "abril" to 4,
            "may" to 5, "mayo" to 5,
            "jun" to 6, "junio" to 6,
            "jul" to 7, "julio" to 7,
            "ago" to 8, "agosto" to 8,
            "set" to 9, "sep" to 9, "setiembre" to 9, "septiembre" to 9,
            "oct" to 10, "octubre" to 10,
            "nov" to 11, "noviembre" to 11,
            "dic" to 12, "diciembre" to 12
        )

        private val BLACKLISTED_WORDS = setOf(
            "yape", "yapeaste", "yapeaste a", "¡yapeaste!", "!yapeaste!",
            "te yapearon", "¡te yapearon!", "!te yapearon!", "te yapeo", "te yapeó", "¡te yapeó!", "!te yapeo!",
            "destinatario", "para", "destino", "contacto",
            "monto", "importe", "total", "saldo", "confirmación", "confirmacion",
            "fecha", "hora", "fecha y hora",
            "numero de operacion", "número de operación", "nro de operacion", "nro. de operación",
            "codigo de seguridad", "código de seguridad", "seguridad",
            "numero de celular", "número de celular", "celular", "telefono", "teléfono",
            "compartir", "inicio", "ir a inicio", "nuevo yapeo", "descargar", "comprobante",
            "comprobante de pago", "constancia", "constancia de pago", "bcp", "banco",
            "mensaje", "ver más", "ver mas",
            "plin", "destino yape", "destino plin", "datos de la transaccion", "datos de la transacción",
            "titular", "servicio", "pago por internet"
        )
    }
}
