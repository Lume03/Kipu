package com.kipu.app.feature.portability

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Operation
import androidx.work.WorkManager
import com.google.common.util.concurrent.Futures
import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.security.StoredAuthSession
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.mockk.*
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class AccountDeletionCleanupTest {
    private val owner = "a0611111-1111-4111-8111-111111111111"
    @Test fun cleanupCancelsOwnerWorkRemovesOnlyOwnerPreferencesAndFilesThenRevokesTokens() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = mockk<SessionCoordinator>(relaxed = true)
        var active: LocalOwner? = LocalOwner(owner)
        every { session.currentOwner } answers { active }
        coEvery { session.clearActiveOwner(any()) } coAnswers { active = null }
        val rows = mockk<DeleteLocalAccountRows>()
        coEvery { rows(owner) } coAnswers { assertNull(session.currentOwner) }
        val auth = mockk<Auth>(relaxed = true)
        val client = mockk<SupabaseClient>()
        every { client.pluginManager } returns mockk { every { installedPlugins } returns mapOf(Auth.key to auth) }
        every { auth.currentUserOrNull() } returns null
        val storage = mockk<KeystoreEncryptedSessionStorage>(relaxed = true)
        var stored: String? = StoredAuthSession.encode(StoredAuthSession("token","refresh",3600,"bearer",owner))
        coEvery { storage.load() } answers { stored }
        coEvery { storage.delete() } coAnswers { stored = null }
        val manager = mockk<WorkManager>()
        val operation = mockk<Operation>()
        every { operation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        every { manager.cancelUniqueWork(any()) } returns operation
        every { manager.cancelAllWorkByTag(any()) } returns operation
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns manager
        try {
            val prefs = context.getSharedPreferences("goal_display",Context.MODE_PRIVATE)
            prefs.edit().putString("$owner:paused","mine").putString("other:paused","theirs").commit()
            val mine = File(context.cacheDir,"ocr/$owner/test.png").apply { parentFile!!.mkdirs(); writeText("evidence") }
            val theirs = File(context.cacheDir,"ocr/other/test.png").apply { parentFile!!.mkdirs(); writeText("other") }
            val cleanup = LocalAccountDeletionCleanup(context,rows,session,storage,client)
            val temporarySession = File(context.filesDir,"kipu_session.enc.tmp").apply { writeText("encrypted-session") }
            cleanup.clean(owner)
            assertFalse(prefs.contains("$owner:paused"))
            assertEquals("theirs",prefs.getString("other:paused",null))
            assertFalse(mine.exists()); assertTrue(theirs.exists())
            assertNotNull(stored) // durable local completion is committed before finish revokes session
            cleanup.finish(owner)
            assertNull(stored)
            assertFalse(temporarySession.exists())
            coVerify(exactly = 1) { rows(owner); auth.clearSession() }
            verify { manager.cancelUniqueWork("movement-sync_$owner"); manager.cancelUniqueWork("recurrence-sync-rescue:$owner") }
            verify { manager.cancelAllWorkByTag("debt-reminder-user:$owner") }
        } finally { unmockkObject(WorkManager.Companion) }
    }
}
