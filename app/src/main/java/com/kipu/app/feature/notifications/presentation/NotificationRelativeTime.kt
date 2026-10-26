package com.kipu.app.feature.notifications.presentation

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-PE"))

/**
 * Compact received-time label for a notification row ("Ahora", "Hace 5 min", "Hace 2 h", "Ayer", "20 oct").
 * Future timestamps (clock skew) fall back to the calendar date instead of a negative duration.
 */
internal fun relativeReceivedLabel(
    createdAt: Instant,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val elapsed = Duration.between(createdAt, now)
    if (!elapsed.isNegative) {
        val days = calendarDaysBetween(createdAt, now, zone)
        when {
            elapsed.toMinutes() < 1 -> return "Ahora"
            elapsed.toMinutes() < 60 -> return "Hace ${elapsed.toMinutes()} min"
            days == 0L -> return "Hace ${elapsed.toHours()} h"
            days == 1L -> return "Ayer"
        }
    }
    return shortDateFormatter.format(createdAt.atZone(zone)).trimEnd('.')
}

/** Calendar days between [createdAt] and [now] in [zone]. */
private fun calendarDaysBetween(createdAt: Instant, now: Instant, zone: ZoneId): Long =
    ChronoUnit.DAYS.between(createdAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
