package com.kipu.app.feature.capture.domain

import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

enum class CaptureIssue { AMOUNT_MISSING, AMOUNT_AMBIGUOUS, DIRECTION_MISSING, DATE_INFERRED }

data class ParsedCapture(
    val amountMinor: Long?,
    val currency: String?,
    val direction: CaptureDirection?,
    val merchantSourceText: String?,
    val cardLast4: String?,
    val occurredAt: Long?,
    val occurredAtReliable: Boolean,
    val issues: Set<CaptureIssue>,
    val instrumentKind: CaptureInstrumentKind? = null,
)

fun interface NotificationCaptureParser {
    fun parse(signal: CaptureSignal): ParsedCapture
}

/** Explicit provider entry points share conservative parsing for the supported fixture formats. */
class YapeNotificationParser : NotificationCaptureParser by PeruNotificationParser()
class PlinNotificationParser : NotificationCaptureParser by PeruNotificationParser()
class BcpNotificationParser : NotificationCaptureParser by PeruNotificationParser()
class InterbankNotificationParser : NotificationCaptureParser by PeruNotificationParser()
class BbvaNotificationParser : NotificationCaptureParser by PeruNotificationParser()
class ScotiabankNotificationParser : NotificationCaptureParser by PeruNotificationParser()

object NotificationCaptureParsers {
    private val parsers = mapOf(
        CaptureProvider.YAPE to YapeNotificationParser(),
        CaptureProvider.PLIN to PlinNotificationParser(),
        CaptureProvider.BCP to BcpNotificationParser(),
        CaptureProvider.INTERBANK to InterbankNotificationParser(),
        CaptureProvider.BBVA to BbvaNotificationParser(),
        CaptureProvider.SCOTIABANK to ScotiabankNotificationParser(),
    )
    fun forProvider(provider: CaptureProvider): NotificationCaptureParser = parsers.getValue(provider)
}

private class PeruNotificationParser : NotificationCaptureParser {
    override fun parse(signal: CaptureSignal): ParsedCapture {
        val raw = signal.text.ifBlank { signal.title }
        val content = "${signal.title}\n$raw"
        val folded = fold(content)
        // A financial-looking amount/card/date is not evidence of a completed operation.
        if (NOT_COMPLETED.containsMatchIn(folded)) return ParsedCapture(
            null, null, null, null, null, null, false, setOf(CaptureIssue.DIRECTION_MISSING),
        )
        val income = Regex("\\b(recibiste|recibido|te yapeo|te envio|abono|deposito recibido)\\b").containsMatchIn(folded)
        val expense = Regex("\\b(yapeaste|enviaste|transferiste|consumo|compra|pagaste|pago realizado)\\b").containsMatchIn(folded)
        val direction = when {
            income && !expense -> CaptureDirection.INCOME
            expense && !income -> CaptureDirection.EXPENSE
            else -> null
        }
        val moneyMatches = MONEY.findAll(content).toList()
        val amounts = moneyMatches.map { match ->
            val currency = if (fold(match.groupValues[1]).startsWith("s/") ||
                match.groupValues[1].equals("PEN", true)) "PEN" else "USD"
            parseMinor(match.groupValues[2]) to currency
        }
        val distinct = amounts.distinct()
        val money = distinct.singleOrNull()?.takeIf { it.first != null }
        val occurredAt = parseDateTime(content)
        val last4 = LAST4.find(folded)?.groupValues?.get(1)
        val instrument = when {
            "credito" in folded -> CaptureInstrumentKind.CREDIT_CARD
            "debito" in folded -> CaptureInstrumentKind.DEBIT_ACCOUNT
            signal.provider == CaptureProvider.YAPE || signal.provider == CaptureProvider.PLIN -> CaptureInstrumentKind.WALLET
            else -> null
        }
        val merchant = when {
            income -> Regex("(?i)^(.+?)\\s+te\\s+(?:yape[oó]|envi[oó])(?=\\s|$)").find(raw)?.groupValues?.get(1)
                ?: Regex("(?i)\\bde\\s+([^\\d]+?)(?=\\s+(?:por|S/|PEN|USD|US\\$)|$)").find(raw)?.groupValues?.get(1)
            "compra" in folded || "consumo" in folded ->
                Regex("(?i)\\ben\\s+(.+?)(?=\\s+con\\b|\\s+el\\s+\\d{1,2}[/.-]|$)").find(raw)?.groupValues?.get(1)
            expense -> Regex("(?i)\\ba\\s+(.+?)(?=\\s+el\\s+\\d{1,2}[/.-]|$)").find(raw)?.groupValues?.get(1)
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() && !Regex("(?i)^(S/|PEN|USD|US\\$)").containsMatchIn(it) }
        return ParsedCapture(
            amountMinor = money?.first, currency = money?.second, direction = direction,
            merchantSourceText = merchant, cardLast4 = last4, occurredAt = occurredAt,
            occurredAtReliable = occurredAt != null, instrumentKind = instrument,
            issues = buildSet {
                if (money == null) add(CaptureIssue.AMOUNT_MISSING)
                if (distinct.size > 1) add(CaptureIssue.AMOUNT_AMBIGUOUS)
                if (direction == null) add(CaptureIssue.DIRECTION_MISSING)
                if (occurredAt == null) add(CaptureIssue.DATE_INFERRED)
            },
        )
    }

    private fun parseMinor(raw: String): Long? = runCatching {
        if (raw.startsWith('-')) return null
        val token = raw.trimEnd('.', ',')
        val lastSeparator = token.indexOfLast { it == '.' || it == ',' }
        val fractionSize = if (lastSeparator >= 0) token.length - lastSeparator - 1 else 0
        val hasDecimal = fractionSize in 1..2
        val whole = if (hasDecimal) token.substring(0, lastSeparator) else token
        // Validate grouping instead of silently accepting malformed separators.
        val groups = whole.split('.', ',')
        if (groups.size > 1 && (groups.first().length !in 1..3 || groups.drop(1).any { it.length != 3 })) return null
        if (whole.contains('.') && whole.contains(',')) return null
        if (hasDecimal && whole.contains(token[lastSeparator])) return null
        val normalized = groups.joinToString("") + if (hasDecimal) ".${token.substring(lastSeparator + 1)}" else ""
        BigDecimal(normalized).movePointRight(2).longValueExact().takeIf { it > 0 }
    }.getOrNull()

    private fun parseDateTime(text: String): Long? {
        val date = DATE.findAll(text).distinctBy { it.value }.singleOrNull() ?: return null
        val time = TIME.findAll(text).distinctBy { it.value }.singleOrNull() ?: return null
        return runCatching {
            val localDate = LocalDate.of(date.groupValues[3].toInt(), date.groupValues[2].toInt(), date.groupValues[1].toInt())
            val rawHour = time.groupValues[1].toInt()
            val suffix = fold(time.groupValues[3]).replace(".", "").replace(" ", "")
            val hour = if (suffix.isEmpty()) rawHour else {
                require(rawHour in 1..12)
                rawHour % 12 + if (suffix == "pm") 12 else 0
            }
            localDate.atTime(LocalTime.of(hour, time.groupValues[2].toInt()))
                .atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli()
        }.getOrNull()
    }

    companion object {
        private val NOT_COMPLETED = Regex("\\b(?:rechazad[ao]s?|denegad[ao]s?|declinad[ao]s?|fallid[ao]s?|anulad[ao]s?|cancelad[ao]s?|pendientes?|revertid[ao]s?|reversad[ao]s?|no\\s+(?:fue\\s+)?(?:procesad|autorizad|aprobad|realizad|completad)[ao]s?|no\\s+(?:se\\s+)?(?:pudo\\s+)?(?:procesar|realizar|completar)|en\\s+proceso)\\b")
        private fun fold(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        private val MONEY = Regex("(?i)(S/\\.?|PEN\\b|USD\\b|US\\$)\\s*(-?\\d[\\d.,]*)")
        private val LAST4 = Regex("(?:terminad[ao](?:s)?\\s+en\\s*|[*x]{2,}\\s*)(\\d{4})(?!\\d)")
        private val DATE = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})\\b")
        private val TIME = Regex("(?i)\\b(\\d{1,2}):(\\d{2})(?!\\d)(?:\\s*([ap]\\.?\\s*m\\.?))?")
    }
}
