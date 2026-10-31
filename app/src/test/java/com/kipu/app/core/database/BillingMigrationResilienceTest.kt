package com.kipu.app.core.database

import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BillingMigrationResilienceTest {
    private val columns = arrayOf("user_id", "id", "installment_number", "occurred_at", "billing_day", "due_day", "closing_day_inclusive", "cycle_date_basis")
    private val epoch = Instant.parse("2026-06-25T12:00:00Z").toEpochMilli()
    private fun migrate(rows: MatrixCursor): List<List<Any?>> {
        val updates = mutableListOf<List<Any?>>()
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.query(any<String>()) } returns rows
        every { db.execSQL(match { it.startsWith("UPDATE credit_installments SET billing_closing_date =") }, any()) } answers {
            updates += secondArg<Array<Any?>>().toList()
        }
        MIGRATION_31_32.migrate(db)
        return updates
    }
    @Test fun `zero and negative installment numbers clamp cycle offset to zero`() {
        val rows = MatrixCursor(columns)
        for (n in listOf(0, -5, 1, 2)) rows.addRow(arrayOf<Any>("owner", "i$n", n, epoch, 26, 19, 0, "OPERATION"))
        val updates = migrate(rows)
        assertEquals(listOf(26, 26, 26, 26), updates.map { LocalDate.ofEpochDay(it.first() as Long).dayOfMonth })
        assertEquals(listOf(6, 6, 6, 7), updates.map { LocalDate.ofEpochDay(it.first() as Long).monthValue })
    }
    @Test fun `calculation exception leaves null and continues to the next row`() {
        val rows = MatrixCursor(columns)
        // Simulate a malformed row reaching the calculation boundary; the public SQL still filters days.
        rows.addRow(arrayOf<Any>("owner", "bad", 1, epoch, 0, 19, 0, "OPERATION"))
        rows.addRow(arrayOf<Any>("owner", "good", 1, epoch, 26, 19, 0, "OPERATION"))
        val updates = migrate(rows)
        assertEquals(2, updates.size)
        assertNull(updates[0][0]); assertEquals("bad", updates[0][2])
        assertEquals(LocalDate.parse("2026-06-26").toEpochDay(), updates[1][0])
        assertEquals("good", updates[1][2])
    }
}
