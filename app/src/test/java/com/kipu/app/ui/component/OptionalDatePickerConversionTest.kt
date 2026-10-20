package com.kipu.app.ui.component

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptionalDatePickerConversionTest {
    @Test
    fun calendarSelectionKeepsTheSameIsoDayAcrossDeviceTimeZones() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Lima", "Pacific/Kiritimati", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (date in listOf("2026-11-10", "2028-02-29", "2026-12-31", "1800-01-01", "2200-12-31", "0001-01-01", "9999-12-31")) {
                    val millis = requireNotNull(optionalDatePickerInitialMillis(date))
                    assertEquals("$date in $zone", date, optionalDatePickerIso(millis))
                }
                assertEquals("2026-11-10", optionalDatePickerIso(Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun emptyInvalidAndUnrepresentableInputNeverPreselectsTodayOrCrashes() {
        for (raw in listOf("", "2026-11-", "2026-02-30", "0000-01-01", "+10000-01-01")) {
            assertNull(raw, optionalDatePickerInitialMillis(raw))
        }
    }
}
