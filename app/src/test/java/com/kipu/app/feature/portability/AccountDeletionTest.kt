package com.kipu.app.feature.portability

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AccountDeletionTest {
    private class Store : DeletionRequestStore {
        val rows = mutableMapOf<String, DeletionRequest>()
        override suspend fun read(owner: String) = rows[owner]
        override suspend fun write(request: DeletionRequest) { rows[request.owner] = request }
    }
    private class Gateway : AccountDeletionGateway {
        var reauthFails = false
        var result = DeletionRemote.COMPLETED
        var fails = false
        var calls = 0
        var proof = false
        val ids = mutableListOf<String>()
        override suspend fun reauthenticate(owner: String, password: String) { check(!reauthFails && password == "correct"); proof = true }
        override suspend fun delete(request: DeletionRequest): DeletionRemote {
            calls++; ids += request.requestId
            if (fails) throw java.io.IOException("offline")
            return result
        }
        override fun clearReauthentication() { proof = false }
    }
    private class Cleanup : AccountDeletionCleanup {
        val locals = mutableMapOf("owner" to "unsynced-data", "other" to "other-data")
        var fails = false
        var finished = false
        var finishFails = false
        override suspend fun clean(owner: String) { check(!fails); locals.remove(owner) }
        override suspend fun finish(owner: String) { check(!finishFails); finished = true }
    }
    private class Fixture {
        val store = Store(); val gateway = Gateway(); val cleanup = Cleanup()
        var owner: String? = "owner"
        fun controller() = AccountDeletion(store, gateway, cleanup, { owner }, { 4 })
        val deletion = controller()
        suspend fun confirmed() {
            deletion.load(); deletion.open(); deletion.continueToReauthentication(); deletion.reauthenticate("correct"); deletion.confirm()
        }
    }
    @Test fun cancelAtSummaryAndAfterReauthComparesEntirePersistedState() = runTest {
        val f = Fixture()
        val before = f.cleanup.locals.toMap() to f.store.rows.toMap()
        f.deletion.load(); f.deletion.open()
        assertEquals(4, f.deletion.state.value.pending)
        f.deletion.cancel()
        assertEquals(before, f.cleanup.locals.toMap() to f.store.rows.toMap())
        f.deletion.open(); f.deletion.continueToReauthentication(); f.deletion.reauthenticate("correct"); f.deletion.cancel()
        assertEquals(before, f.cleanup.locals.toMap() to f.store.rows.toMap())
        assertEquals(0, f.gateway.calls)
        assertFalse(f.gateway.proof)
        assertFalse(f.cleanup.finished)
    }
    @Test fun explicitConfirmationAndSuccessfulReauthAreRequired() = runTest {
        val f = Fixture()
        f.deletion.load(); f.deletion.open(); f.deletion.confirm()
        assertEquals(0, f.gateway.calls)
        f.deletion.continueToReauthentication(); f.deletion.reauthenticate("wrong"); f.deletion.confirm()
        assertEquals(0, f.gateway.calls)
        assertTrue(f.store.rows.isEmpty())
        f.deletion.reauthenticate("correct")
        assertEquals(0, f.gateway.calls)
        f.deletion.confirm()
        assertEquals(1, f.gateway.calls)
        assertEquals(DeletionLocal.CLEARED, f.store.rows["owner"]?.local)
        assertEquals("", f.store.rows["owner"]?.secret)
        assertEquals(mapOf("other" to "other-data"), f.cleanup.locals)
        assertTrue(f.cleanup.finished)
    }
    @Test fun timeoutPersistsRetryAndRetainsUnsyncedRowsAcrossControllerRestart() = runTest {
        val f = Fixture(); f.gateway.fails = true
        f.confirmed()
        assertEquals(DeletionRemote.ERROR, f.store.rows["owner"]?.remote)
        assertEquals("unsynced-data", f.cleanup.locals["owner"])
        assertEquals(DeletionLocal.RETAINED, f.store.rows["owner"]?.local)
        val newController = f.controller(); newController.load()
        assertEquals(DeletionStep.STATUS, newController.state.value.step)
        f.gateway.fails = false; newController.retry()
        assertEquals(1, f.gateway.ids.distinct().size)
        assertEquals(DeletionRemote.COMPLETED, f.store.rows["owner"]?.remote)
        assertEquals(DeletionLocal.CLEARED, f.store.rows["owner"]?.local)
    }
    @Test fun pendingAndServerErrorNeverCleanLocals() = runTest {
        for (status in listOf(DeletionRemote.PENDING, DeletionRemote.ERROR)) {
            val f = Fixture(); f.gateway.result = status; f.confirmed()
            assertEquals(status, f.deletion.state.value.request?.remote)
            assertEquals("unsynced-data", f.cleanup.locals["owner"])
            assertFalse(f.cleanup.finished)
        }
    }
    @Test fun cleanupFailureKeepsRemoteCompletedAndRetriesOnlyCleanup() = runTest {
        val f = Fixture(); f.cleanup.fails = true; f.confirmed()
        assertEquals(DeletionRemote.COMPLETED, f.store.rows["owner"]?.remote)
        assertEquals(DeletionLocal.PENDING, f.store.rows["owner"]?.local)
        assertFalse(f.cleanup.finished)
        f.cleanup.fails = false
        val restarted = f.controller(); restarted.load(); restarted.retry()
        assertEquals(1, f.gateway.calls)
        assertEquals(DeletionLocal.CLEARED, restarted.state.value.request?.local)
        assertTrue(f.cleanup.finished)
    }
    @Test fun switchingOwnerBeforeConfirmationCannotDeleteEitherPartition() = runTest {
        val f = Fixture(); f.deletion.load(); f.deletion.open(); f.deletion.continueToReauthentication(); f.deletion.reauthenticate("correct")
        f.owner = "other"; f.deletion.confirm()
        assertEquals(0, f.gateway.calls)
        assertTrue(f.store.rows.isEmpty())
        assertEquals(2, f.cleanup.locals.size)
    }
    @Test fun anotherOwnersRequestIsNeverRestoredOrRetried() = runTest {
        val f = Fixture(); f.gateway.result = DeletionRemote.PENDING; f.confirmed()
        f.owner = "other"
        val another = f.controller(); another.load(); another.retry()
        assertNull(another.state.value.request)
        assertEquals(1, f.gateway.calls)
    }
    @Test fun tokenCleanupFailureRemainsDurableAndRetryDoesNotCallRemoteAgain() = runTest {
        val f = Fixture(); f.cleanup.finishFails = true; f.confirmed()
        assertEquals(DeletionLocal.SESSION_PENDING, f.store.rows["owner"]?.local)
        assertFalse(f.cleanup.finished)
        f.cleanup.finishFails = false
        val restarted = f.controller(); restarted.load(); restarted.retry()
        assertEquals(1, f.gateway.calls)
        assertEquals(DeletionLocal.CLEARED, f.store.rows["owner"]?.local)
        assertTrue(f.cleanup.finished)
    }
    @Test fun completionSurvivesSessionClearedWhileRemoteCallReturns() = runTest {
        var owner: String? = "owner"
        val store = Store()
        val cleanup = Cleanup()
        val gateway = object : AccountDeletionGateway {
            override suspend fun reauthenticate(owner: String, password: String) {}
            override fun clearReauthentication() {}
            override suspend fun delete(request: DeletionRequest): DeletionRemote { owner = null; return DeletionRemote.COMPLETED }
        }
        val deletion = AccountDeletion(store,gateway,cleanup,{owner},{0})
        deletion.load(); deletion.open(); deletion.continueToReauthentication(); deletion.reauthenticate("correct"); deletion.confirm()
        assertEquals(DeletionRemote.COMPLETED,store.rows["owner"]?.remote)
        assertEquals(DeletionLocal.CLEARED,store.rows["owner"]?.local)
    }
}
