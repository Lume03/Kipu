package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementOutboxLeaseTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: MovementDao
    private val owner = "lease-owner"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java,
        ).build()
        dao = database.movementDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun missingEndpointRecoveryPreservesIdentityAndExcludesOtherErrorsAndOwners() = runTest {
        val missing = command("missing").copy(commandType = "REGISTER", state = "FAILED_PERMANENT", lastErrorCode = "HTTP_404")
        val forbidden = command("forbidden").copy(commandType = "REGISTER", state = "FAILED_PERMANENT", lastErrorCode = "HTTP_403")
        val foreign = missing.copy(id = "foreign", userId = "other-owner")
        listOf(missing, forbidden, foreign).forEach { dao.insertOutbox(it) }
        assertEquals(1, dao.recoverMissingRegistrationEndpoint(owner, 1000))
        assertEquals(0, dao.recoverMissingRegistrationEndpoint(owner, 1001))
        val recovered = dao.claimPendingOutbox(owner, 1002).single()
        assertEquals(missing.idempotencyKey, recovered.idempotencyKey)
        assertEquals(missing.payload, recovered.payload)
        assertEquals("FAILED_PERMANENT", dao.getOutboxById(owner, forbidden.id)?.state)
        assertEquals("FAILED_PERMANENT", dao.getOutboxById("other-owner", foreign.id)?.state)
    }

    @Test
    fun expiredInFlightLeaseIsReclaimedWithOriginalPayloadAndIdentity() = runTest {
        val command = command().copy(state = "IN_FLIGHT", leaseUntil = 1_000L)
        dao.insertOutbox(command)
        val reclaimed = dao.claimPendingOutbox(owner, 1_000L).single()
        assertEquals(command.id, reclaimed.id)
        assertEquals(command.idempotencyKey, reclaimed.idempotencyKey)
        assertEquals(command.payload, reclaimed.payload)
        val stored = requireNotNull(dao.getOutboxById(owner, command.id))
        assertEquals("IN_FLIGHT", stored.state)
        assertTrue(requireNotNull(stored.leaseUntil) > 1_000L)
        assertTrue(dao.claimPendingOutbox(owner, 1_001L).isEmpty())
    }

    @Test
    fun pendingCommandIsClaimedAtomicallyAndCannotBeClaimedTwice() = runTest {
        dao.insertOutbox(command())
        val claims = coroutineScope {
            val first = async { dao.claimPendingOutbox(owner, 1_000L) }
            val second = async { dao.claimPendingOutbox(owner, 1_000L) }
            first.await() + second.await()
        }
        assertEquals(1, claims.size)
        assertEquals("IN_FLIGHT", dao.getOutboxById(owner, "command-1")?.state)
    }

    @Test
    fun liveLeaseFutureRetryTerminalStateAndOtherOwnerAreNotClaimed() = runTest {
        dao.insertOutbox(command("live").copy(state = "IN_FLIGHT", leaseUntil = 1_001L))
        dao.insertOutbox(command("future").copy(state = "RETRY", nextAttemptAt = 1_001L))
        dao.insertOutbox(command("done").copy(state = "SYNCED"))
        dao.insertOutbox(command("conflict").copy(state = "CONFLICT"))
        dao.insertOutbox(command("foreign").copy(userId = "other-owner"))
        assertTrue(dao.claimPendingOutbox(owner, 1_000L).isEmpty())
    }

    @Test
    fun lateResponseCannotCompleteReplacedLease() = runTest {
        dao.insertOutbox(command())
        val first = dao.claimPendingOutbox(owner, 1_000L).single()
        val replacement = dao.claimPendingOutbox(owner, 61_000L).single()
        assertEquals(false, dao.completeClaimedOutbox(first, "SYNCED", null, null))
        assertEquals(replacement, dao.getOutboxById(owner, first.id))
        assertEquals(true, dao.completeClaimedOutbox(replacement, "SYNCED", null, null))
        assertEquals("SYNCED", dao.getOutboxById(owner, first.id)?.state)
        assertEquals(false, dao.completeClaimedOutbox(replacement, "RETRY", 62_000L, "LATE"))
        assertEquals("SYNCED", dao.getOutboxById(owner, first.id)?.state)
    }

    private fun command(id: String = "command-1") = MovementOutboxEntity(
        id = id, userId = owner, idempotencyKey = "key-$id", aggregateId = "movement-$id",
        payload = "immutable-original-payload", createdAt = 0L, updatedAt = 0L,
    )
}
