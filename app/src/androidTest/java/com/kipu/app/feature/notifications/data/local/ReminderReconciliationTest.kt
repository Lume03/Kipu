package com.kipu.app.feature.notifications.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderReconciliationTest {
    private lateinit var database: KipuDatabase
    private lateinit var reconciler: ReminderEventReconciler

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KipuDatabase::class.java,
        ).build()
        reconciler = ReminderEventReconciler(database.localReminderEventDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun changedDueDateImmediatelyTombstonesOldEventOnlyForThatOwnerAndSource() = runBlocking {
        val dao = database.localReminderEventDao()
        dao.upsert(event("old-a", OWNER_A, SOURCE_A, "2026-10-20"))
        dao.upsert(event("new-a", OWNER_A, SOURCE_A, "2026-11-20"))
        dao.upsert(event("sister-a", OWNER_A, SISTER_SOURCE, "2026-10-20"))
        dao.upsert(event("old-b", OWNER_B, SOURCE_A, "2026-10-20"))

        reconciler.retainCurrentDate(OWNER_A, SOURCE_A, "2026-11-20", NOW)

        assertEquals(setOf("new-a", "sister-a"), dao.observeActive(OWNER_A).first().map { it.id }.toSet())
        assertEquals(setOf("old-b"), dao.observeActive(OWNER_B).first().map { it.id }.toSet())
    }

    @Test
    fun invalidSourceImmediatelyTombstonesEventsWithoutTouchingSiblingOrOtherOwner() = runBlocking {
        val dao = database.localReminderEventDao()
        dao.upsert(event("source-a", OWNER_A, SOURCE_A, "2026-10-20"))
        dao.upsert(event("sister-a", OWNER_A, SISTER_SOURCE, "2026-10-20"))
        dao.upsert(event("source-b", OWNER_B, SOURCE_A, "2026-10-20"))

        reconciler.invalidateSource(OWNER_A, SOURCE_A, NOW)

        assertEquals(listOf("sister-a"), dao.observeActive(OWNER_A).first().map { it.id })
        assertEquals(listOf("source-b"), dao.observeActive(OWNER_B).first().map { it.id })
        assertTrue(dao.observeActive(OWNER_A).first().none { it.id == "source-a" })
    }

    private fun event(id: String, owner: String, source: String, dueDate: String) =
        LocalReminderEventEntity(
            id = id,
            userId = owner,
            sourceId = source,
            dueDate = dueDate,
            createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        )

    private companion object {
        val NOW = Instant.parse("2026-10-19T12:00:00Z")
        const val OWNER_A = "owner-a"
        const val OWNER_B = "owner-b"
        const val SOURCE_A = "source-a"
        const val SISTER_SOURCE = "sister-source"
    }
}
