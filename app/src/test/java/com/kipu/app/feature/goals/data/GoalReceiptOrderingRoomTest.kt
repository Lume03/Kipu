package com.kipu.app.feature.goals.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.goals.data.local.*
import com.kipu.app.feature.goals.data.remote.GoalCommandGoalRemoteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalReceiptOrderingRoomTest {
    @Test fun olderCompositeReceiptPreservesLaterCompletedProjectionAndPendingState() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).allowMainThreadQueries().build()
        try {
            val local = GoalLocalDataSource(db, db.goalDao(), db.goalOutboxDao(), db.movementDao())
            db.goalDao().upsertGoal(GoalEntity("goal", "owner", "Meta", 100, "PEN", status="COMPLETED", revision=6, syncState="PENDING", createdAt=1, updatedAt=2))
            val first = GoalOutboxEntity("owner", "first", "goal", "RELEASE_GOAL", "hash", expectedRevision=4, payload="{}", createdAt=1, updatedAt=1)
            val second = GoalOutboxEntity("owner", "second", "goal", "UPDATE_GOAL", "hash2", expectedRevision=5, payload="{}", createdAt=2, updatedAt=2)
            db.goalOutboxDao().insert(first)
            db.goalOutboxDao().insert(second)
            assertTrue(local.finishRemoteCommand(first, "APPLIED", null, 5, listOf(GoalCommandGoalRemoteResult("goal", "ACTIVE", 5, 1))))
            val pending = db.goalDao().getGoalIncludingArchived("owner", "goal")!!
            assertEquals("COMPLETED", pending.status)
            assertEquals(6L, pending.revision)
            assertEquals("PENDING", pending.syncState)
            assertTrue(local.finishRemoteCommand(second, "APPLIED", null, 6))
            assertEquals("COMPLETED", db.goalDao().getGoalIncludingArchived("owner", "goal")!!.status)
            assertEquals("SYNCED", db.goalDao().getGoalIncludingArchived("owner", "goal")!!.syncState)
            // A delayed duplicate cannot revert an already confirmed newer state either.
            assertFalse(local.finishRemoteCommand(first, "DUPLICATE", null, 5, listOf(GoalCommandGoalRemoteResult("goal", "ACTIVE", 5, 1))))
            db.goalDao().updateRemoteGoalResult("owner", "goal", "ACTIVE", 5, "SYNCED")
            assertEquals("COMPLETED", db.goalDao().getGoalIncludingArchived("owner", "goal")!!.status)
        } finally { db.close() }
    }
}
