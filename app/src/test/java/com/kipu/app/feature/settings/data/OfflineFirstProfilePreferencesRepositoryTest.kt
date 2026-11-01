package com.kipu.app.feature.settings.data

import com.kipu.app.core.network.AuthenticatedSession
import com.kipu.app.core.network.AuthenticatedSessionProvider
import com.kipu.app.feature.settings.data.local.ProfilePreferenceOutboxEntity
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.feature.settings.data.local.UserProfileCacheEntity
import com.kipu.app.feature.settings.domain.model.ProfilePreferenceDelta
import com.kipu.app.feature.settings.domain.model.ThemeMode
import io.github.jan.supabase.SupabaseClient
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OfflineFirstProfilePreferencesRepositoryTest {
    private val owner = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val dao = FakeProfileDao()
    // Strict mock: any call (token import, RPC) fails the test.
    private val supabase = mockk<SupabaseClient>()
    private var session: AuthenticatedSession? = null
    private var sessionFailure: Throwable? = null
    private val sessions = object : AuthenticatedSessionProvider {
        override suspend fun currentSession(): AuthenticatedSession? = sessionFailure?.let { throw it } ?: session
        override suspend fun refreshSession(): AuthenticatedSession? = currentSession()
    }
    private val repository = OfflineFirstProfilePreferencesRepository(dao, supabase, sessions)

    private fun pendingRow(operationId: UUID = UUID.randomUUID()) = ProfilePreferenceOutboxEntity(
        operationId = operationId, userId = owner, expectedRevision = 3L, payload = """{"hide_balances":true}""", status = "PENDING",
    )

    @Test fun `two offline edits coalesce into one outbox row where the last value wins`() = runTest {
        dao.profile = UserProfileCacheEntity(userId = owner, displayName = "Ana", remoteRevision = 3L)

        repository.updatePreferences(owner, ProfilePreferenceDelta(hideBalances = true, monthStart = 5))
        val first = dao.outbox.single()
        repository.updatePreferences(owner, ProfilePreferenceDelta(hideBalances = false, themeMode = ThemeMode.DARK))

        val row = dao.outbox.single()
        assertEquals(first.operationId, row.operationId)
        assertEquals(3L, row.expectedRevision)
        val payload = Json.parseToJsonElement(row.payload).jsonObject
        assertEquals(false, payload.getValue("hide_balances").jsonPrimitive.boolean)
        assertEquals(5, payload.getValue("month_start").jsonPrimitive.int)
        assertEquals("DARK", payload.getValue("theme_mode").jsonPrimitive.content)
        assertEquals(3, payload.size)
    }

    @Test fun `a row already in conflict is not merged into`() = runTest {
        dao.profile = UserProfileCacheEntity(userId = owner, displayName = "Ana", remoteRevision = 3L)
        dao.outbox += pendingRow().copy(status = "CONFLICT")

        repository.updatePreferences(owner, ProfilePreferenceDelta(hideBalances = true))

        assertEquals(listOf("CONFLICT", "PENDING"), dao.outbox.map { it.status })
    }

    @Test fun `sync aborts without touching the outbox when the active session is another user`() = runTest {
        val row = pendingRow().also { dao.outbox += it }
        session = AuthenticatedSession(other, "other-token")

        val result = repository.syncPendingPreferences(owner)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()?.message?.contains("conflict", ignoreCase = true) != true)
        assertEquals(listOf(row), dao.outbox)
    }

    @Test fun `sync aborts without touching the outbox when there is no session`() = runTest {
        val row = pendingRow().also { dao.outbox += it }
        session = null

        assertTrue(repository.syncPendingPreferences(owner).isFailure)
        assertEquals(listOf(row), dao.outbox)
    }

    @Test fun `cancellation is propagated by refresh and update`() {
        sessionFailure = CancellationException("cancelled")
        assertThrows(CancellationException::class.java) { runBlocking { repository.refreshProfile(owner) } }
        dao.failure = CancellationException("cancelled")
        assertThrows(CancellationException::class.java) {
            runBlocking { repository.updatePreferences(owner, ProfilePreferenceDelta(hideBalances = true)) }
        }
    }
}

private class FakeProfileDao : ProfilePreferencesDao() {
    var profile: UserProfileCacheEntity? = null
    val outbox = mutableListOf<ProfilePreferenceOutboxEntity>()
    var failure: Throwable? = null

    override suspend fun findProfile(userId: UUID): UserProfileCacheEntity? = failure?.let { throw it } ?: profile?.takeIf { it.userId == userId }
    override fun observeProfile(userId: UUID): Flow<UserProfileCacheEntity?> = flowOf(profile)
    override suspend fun putProfile(profile: UserProfileCacheEntity) { this.profile = profile }
    override suspend fun insertOutbox(outbox: ProfilePreferenceOutboxEntity) { this.outbox += outbox }
    override suspend fun updateOutbox(outbox: ProfilePreferenceOutboxEntity) {
        val index = this.outbox.indexOfFirst { it.operationId == outbox.operationId }
        if (index >= 0) this.outbox[index] = outbox
    }
    override suspend fun findOutbox(operationId: UUID) = outbox.firstOrNull { it.operationId == operationId }
    override suspend fun pendingOutboxCount(userId: UUID) = findPendingOutbox(userId).size
    override suspend fun findPendingOutbox(userId: UUID) =
        outbox.filter { it.userId == userId && it.status in setOf("PENDING", "IN_FLIGHT", "WAITING_FOR_AUTH") }
    override suspend fun findAllPendingOutbox() = outbox.filter { it.status == "PENDING" }
    override suspend fun findOldestPendingOutbox(userId: UUID) =
        outbox.filter { it.userId == userId && it.status == "PENDING" }.minByOrNull { it.createdAt }
    override suspend fun deleteOutbox(operationId: UUID) { outbox.removeAll { it.operationId == operationId } }
    override suspend fun markOutboxWaitingForAuth(userId: UUID) = Unit
}
