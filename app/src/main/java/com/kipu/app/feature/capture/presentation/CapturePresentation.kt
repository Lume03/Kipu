package com.kipu.app.feature.capture.presentation

import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.feature.capture.domain.CaptureProvider
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val CAPTURE_ZONE: ZoneId = ZoneId.of("America/Lima")
private val CAPTURE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es", "PE"))
private val CAPTURE_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "PE"))

internal fun CaptureProvider.displayName(): String = when (this) {
    CaptureProvider.YAPE -> "Yape"
    CaptureProvider.PLIN -> "Plin"
    CaptureProvider.BCP -> "BCP"
    CaptureProvider.INTERBANK -> "Interbank"
    CaptureProvider.BBVA -> "BBVA"
    CaptureProvider.SCOTIABANK -> "Scotiabank"
}

internal fun CaptureConfidenceLevel.displayName(): String = when (this) {
    CaptureConfidenceLevel.HIGH -> "Confianza alta"
    CaptureConfidenceLevel.MEDIUM -> "Confianza media"
    CaptureConfidenceLevel.LOW -> "Confianza baja"
}

internal fun currencySymbol(currency: String?): String = when (currency) {
    "PEN" -> "S/"
    "USD" -> "US$"
    null -> ""
    else -> currency
}

/** Minor units are exact Longs; formatting goes through BigDecimal, never Double. */
internal fun formatCaptureAmount(amountMinor: Long?, currency: String?): String? {
    if (amountMinor == null) return null
    val value = BigDecimal.valueOf(amountMinor, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
    return listOf(currencySymbol(currency), value).filter(String::isNotBlank).joinToString(" ")
}

internal fun formatCaptureDateTime(millis: Long): String =
    CAPTURE_DATE_FORMAT.format(Instant.ofEpochMilli(millis).atZone(CAPTURE_ZONE))

internal fun formatCaptureDay(millis: Long): String =
    CAPTURE_DAY_FORMAT.format(Instant.ofEpochMilli(millis).atZone(CAPTURE_ZONE))

/** Parses user input such as "25", "25.5" or "1,250.00" into minor units; null if invalid or over 2 decimals. */
internal fun parseCaptureAmountMinor(text: String): Long? {
    val normalized = text.trim().replace(",", ".").let { raw ->
        // "1.250,00" style is not expected here: the field only accepts digits and one separator.
        if (raw.count { it == '.' } > 1) return null else raw
    }
    if (normalized.isEmpty()) return null
    val value = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
    if (value.scale() > 2 || value.signum() <= 0) return null
    return runCatching { value.movePointRight(2).longValueExact() }.getOrNull()
}

internal fun amountTextOf(amountMinor: Long?): String =
    amountMinor?.let { BigDecimal.valueOf(it, 2).setScale(2).toPlainString() }.orEmpty()

/** Replaces the calendar day of [millis] (Lima) with [day], keeping the time of day. */
internal fun withCaptureDay(millis: Long, day: LocalDate): Long {
    val time = Instant.ofEpochMilli(millis).atZone(CAPTURE_ZONE).toLocalTime()
    return day.atTime(time).atZone(CAPTURE_ZONE).toInstant().toEpochMilli()
}
