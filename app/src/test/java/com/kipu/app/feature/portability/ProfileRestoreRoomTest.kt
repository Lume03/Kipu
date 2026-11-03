package com.kipu.app.feature.portability

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.settings.data.local.*
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class ProfileRestoreRoomTest {
    @Test fun pendingAndConflictFieldsSurviveRepeatedRemoteHydration() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.profilePreferencesDao()
            val owner = UUID.randomUUID()
            val now = java.time.Instant.parse("2026-11-03T12:00:00Z")
            val local = UserProfileCacheEntity(owner, "Local", hideBalances = true, remoteRevision = 3, syncState = "PENDING", updatedLocallyAt = now)
            dao.putProfile(local)
            val row = ProfilePreferenceOutboxEntity(UUID.randomUUID(), owner, 3, """{"display_name":"Local","hide_balances":true}""", "PENDING", createdAt = now, updatedAt = now)
            dao.insertOutbox(row)
            val remote = local.copy(displayName = "Remote", hideBalances = false, themeMode = "DARK", remoteRevision = 8, syncState = "SYNCED")
            for (status in listOf("PENDING", "WAITING_FOR_AUTH", "IN_FLIGHT", "CONFLICT")) {
                val pending = row.copy(status = status)
                dao.updateOutbox(pending)
                repeat(2) {
                    val hydrated = dao.hydrateProfile(remote)
                    assertEquals("Local", hydrated.displayName)
                    assertTrue(hydrated.hideBalances)
                    assertEquals("DARK", hydrated.themeMode)
                    assertEquals(3L, hydrated.remoteRevision)
                    assertEquals("PENDING", hydrated.syncState)
                    assertEquals(pending, dao.findOutbox(row.operationId))
                }
            }
            dao.deleteOutbox(row.operationId)
            assertEquals(remote, dao.hydrateProfile(remote))
            val other = UUID.randomUUID()
            assertEquals(remote.copy(userId = other), dao.hydrateProfile(remote.copy(userId = other)))
        } finally { db.close() }
    }

    @Test fun restoreQueriesProfileDespitePendingAndPropagatesRealFailure() = runTest {
        val owner = UUID.randomUUID()
        val session = mockk<SessionCoordinator>()
        every { session.currentOwner } returns mockk { every { verifiedUserId } returns owner.toString() }
        val profile = mockk<ProfilePreferencesRepository>()
        val restore = RestoreUserData(session, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), profile)
        coEvery { profile.refreshProfile(owner) } returns Result.failure(IllegalStateException("offline"))
        assertEquals(false, restore(owner.toString()).modules["Perfil y preferencias"])
        coEvery { profile.refreshProfile(owner) } returns Result.success(com.kipu.app.feature.settings.domain.model.UserProfile(owner, "Local"))
        assertEquals(true, restore(owner.toString()).modules["Perfil y preferencias"])
        coVerify(exactly = 2) { profile.refreshProfile(owner) }
    }
}
