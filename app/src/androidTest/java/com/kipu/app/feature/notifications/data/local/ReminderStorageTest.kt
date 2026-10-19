package com.kipu.app.feature.notifications.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.database.MIGRATION_23_24
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ReminderStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KipuDatabase::class.java,
    )

    @After
    fun cleanUp() {
        DATABASE_NAMES.forEach(context::deleteDatabase)
    }

    @Test
    fun migration23To24AddsReminderStorageWithoutDiscardingExistingRows() {
        val name = "reminder-v23-to-v24"
        helper.createDatabase(name, 23).use { db ->
            db.execSQL(
                """INSERT INTO recurrence_sync_checkpoints (user_id, sequence, updated_at)
                    VALUES ('owner-a', 7, 7000)""",
            )
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM recurrence_sync_checkpoints WHERE user_id='owner-a' AND sequence=7"))
        }
        helper.runMigrationsAndValidate(name, 24, true, MIGRATION_23_24).use { db ->
            assertEquals(1L, db.scalarLong("SELECT COUNT(*) FROM recurrence_sync_checkpoints WHERE user_id='owner-a' AND sequence=7"))
            assertTrue("v24 reminder migration must create reminder_schedules", db.hasTable("reminder_schedules"))
            assertTrue("v24 reminder migration must create local_reminder_events", db.hasTable("local_reminder_events"))
            assertTrue(db.hasUniqueIndex("reminder_schedules", listOf("user_id", "source_id", "event_type", "due_date")))
            assertTrue(db.hasUniqueIndex("local_reminder_events", listOf("user_id", "source_id", "event_type", "due_date")))
        }
    }

    @Test
    fun reminderRowsAndQueriesAreScopedByOwner() = runBlocking {
        withDatabase("reminder-owner-scope") { database ->
            val scheduleDao = database.reminderScheduleDao()
            val eventDao = database.localReminderEventDao()
            scheduleDao.upsert(schedule("owner-a", "source-1", "2026-11-01"))
            scheduleDao.upsert(schedule("owner-b", "source-1", "2026-11-01"))
            eventDao.upsert(event("event-a", "owner-a", "source-1", "2026-11-01"))
            eventDao.upsert(event("event-b", "owner-b", "source-1", "2026-11-01"))
            assertEquals(listOf("owner-a"), scheduleDao.getForSource("owner-a", "source-1").map { it.userId })
            assertEquals(listOf("event-a"), eventDao.observeActive("owner-a").first().map { it.id })
            assertEquals(listOf("event-b"), eventDao.observeActive("owner-b").first().map { it.id })
        }
    }

    @Test
    fun scheduleAndLocalEventEnforceOwnerSourceTypeDateUniqueness() {
        helper.createDatabase("reminder-tuple-uniqueness", 24).use { db ->
            assertTrue(db.hasUniqueIndex("reminder_schedules", listOf("user_id", "source_id", "event_type", "due_date")))
            assertTrue(db.hasUniqueIndex("local_reminder_events", listOf("user_id", "source_id", "event_type", "due_date")))
        }
    }

    @Test
    fun reschedulingWithdrawsOldScheduleAndTombstonesPreviouslyEmittedEvent() = runBlocking {
        withDatabase("reminder-reschedule-tombstone") { database ->
            val now = Instant.parse("2026-10-01T00:00:00Z")
            val scheduleDao = database.reminderScheduleDao()
            val eventDao = database.localReminderEventDao()
            scheduleDao.upsert(schedule("owner-a", "source-1", "2026-10-01", now))
            eventDao.upsert(event("old-event", "owner-a", "source-1", "2026-10-01", now))
            scheduleDao.upsert(schedule("owner-a", "source-1", "2026-11-01", now))
            assertEquals(1, scheduleDao.cancelReplacedSchedules("owner-a", "source-1", "2026-11-01", now))
            assertEquals(1, eventDao.tombstoneReplacedEvents("owner-a", "source-1", "2026-11-01", now))
            assertEquals(listOf("CANCELLED", "SCHEDULED"), scheduleDao.getForSource("owner-a", "source-1").map { it.status }.sorted())
            assertTrue(eventDao.observeActive("owner-a").first().isEmpty())
            assertEquals(1, database.openHelper.readableDatabase.scalarLong(
                "SELECT COUNT(*) FROM local_reminder_events WHERE id='old-event' AND deleted_at IS NOT NULL",
            ))
        }
    }

    @Test
    fun retryUpsertKeepsOneScheduleAndOneLocalEventForTheSameTuple() = runBlocking {
        withDatabase("reminder-idempotent-upsert") { database ->
            val scheduleDao = database.reminderScheduleDao()
            val eventDao = database.localReminderEventDao()
            val schedule = schedule("owner-a", "source-1", "2026-11-01")
            val event = event("event-1", "owner-a", "source-1", "2026-11-01")
            scheduleDao.upsert(schedule)
            scheduleDao.upsert(schedule)
            eventDao.upsert(event)
            eventDao.upsert(event)
            assertEquals(1, scheduleDao.getForSource("owner-a", "source-1").size)
            assertEquals(listOf("event-1"), eventDao.observeActive("owner-a").first().map { it.id })
            assertEquals(1, database.openHelper.readableDatabase.scalarLong(
                "SELECT COUNT(*) FROM local_reminder_events WHERE user_id='owner-a' AND source_id='source-1' AND event_type='BILLING_DUE' AND due_date='2026-11-01'",
            ))
        }
    }

    private suspend fun withDatabase(name: String, block: suspend (KipuDatabase) -> Unit) {
        helper.createDatabase(name, 24).close()
        val database = Room.databaseBuilder(context, KipuDatabase::class.java, name)
            .addMigrations(MIGRATION_23_24)
            .build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun schedule(owner: String, source: String, dueDate: String, updatedAt: Instant = Instant.parse("2026-10-01T00:00:00Z")) =
        ReminderScheduleEntity(owner, source, dueDate = dueDate, leadDays = 2, updatedAt = updatedAt)

    private fun event(id: String, owner: String, source: String, dueDate: String, createdAt: Instant = Instant.parse("2026-10-01T00:00:00Z")) =
        LocalReminderEventEntity(id, owner, source, dueDate = dueDate, createdAt = createdAt)

    private fun SupportSQLiteDatabase.hasTable(table: String): Boolean =
        scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$table'") == 1L


    private fun SupportSQLiteDatabase.hasUniqueIndex(table: String, columns: List<String>): Boolean {
        val indexes = query("PRAGMA index_list(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
            }
        }
        return indexes.any { index ->
            query("PRAGMA index_info(`$index`)").use { cursor ->
                val indexedColumns = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                indexedColumns == columns
            }
        }
    }

    private fun SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        val DATABASE_NAMES = listOf(
            "reminder-v23-to-v24",
            "reminder-owner-scope",
            "reminder-tuple-uniqueness",
            "reminder-reschedule-tombstone",
            "reminder-idempotent-upsert",
        )
    }
}
