package com.kipu.app.feature.movements.presentation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementEditorDateTest {
    @Test fun `picker preserves Lima time and selected civil date`() {
        val original = Instant.parse("2026-10-28T03:45:00Z").toEpochMilli()
        val selected = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-11-02T03:45:00Z").toEpochMilli(), editorPickerDateToInstant(selected, original))
        assertEquals(Instant.parse("2026-10-27T00:00:00Z").toEpochMilli(), editorPickerInitialMillis(original))
    }
}
