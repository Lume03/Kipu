package com.kipu.app.feature.portability

import android.content.Context
import android.content.ContentResolver
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.movements.data.local.TransactionEntity
import io.mockk.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortabilityOfflineAcceptanceTest {
    /** HU-06: no network means no remote restore, but local export still works and loses no rows. */
    @Test fun offlineRestoreShowsMessageAndLocalExportSurvivesWriteFailureAndRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = "owner"
            val local = listOf(TransactionEntity("pending", owner, "EXPENSE", 12345, "PEN", occurredAt = 1L, syncStatus = "PENDING"))
            val before = local.toList()
            val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(owner) }
            val pending = mockk<PendingLocalChanges> { coEvery { this@mockk.invoke(owner) } returns PendingLocalSummary(mapOf("movement_outbox" to 1)) }
            val store = mockk<PortabilityStore> { coEvery { csv(owner) } answers { MovementExport.csv(owner, local) } }
            val restore = mockk<RestoreUserData>()
            val connectivity = mockk<ConnectivityManager> { every { activeNetwork } returns null }
            val output = ByteArrayOutputStream()
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            var failWrite = true
            every { resolver.openOutputStream(uri, "wt") } answers { if (failWrite) throw IOException("synthetic write failure") else output }
            val context = mockk<Context> {
                every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
                every { contentResolver } returns resolver
            }
            val vm = PortabilityViewModel(session, pending, store, restore, context)
            advanceUntilIdle()
            vm.restore(); advanceUntilIdle()
            assertTrue(vm.state.value.message!!.startsWith("Sin conexi\u00f3n."))
            coVerify(exactly = 0) { restore(any(), any()) }
            vm.export(uri, false)
            vm.state.first { !it.busy }
            assertTrue(vm.state.value.message!!.startsWith("No se pudo"))
            assertEquals(before, local)
            assertEquals(1, vm.state.value.pending!!.total)
            failWrite = false
            vm.export(uri, false)
            vm.state.first { !it.busy }
            assertEquals("Exportaci\u00f3n guardada.", vm.state.value.message)
            assertEquals(MovementExport.csv(owner, before), output.toString("UTF-8"))
            assertEquals(before, local)
            assertEquals(1, vm.state.value.pending!!.total)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun restorationRecoversAfterConnectivityReturnsAndRetriesOnlyFailedModules() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner("owner") }
            val pending = mockk<PendingLocalChanges> { coEvery { this@mockk.invoke("owner") } returns PendingLocalSummary(mapOf("movement_outbox" to 2)) }
            var online = false
            var failGoals = true
            var historyCalls = 0
            var goalCalls = 0
            val runner = RestoreRunner(linkedMapOf<String, suspend (String) -> Boolean>(
                "history" to { historyCalls++; true },
                "goals" to { goalCalls++; if (failGoals) throw IOException("offline") else true },
            ))
            val restore = mockk<RestoreUserData> {
                coEvery { this@mockk.invoke("owner", any()) } coAnswers { runner.run("owner", secondArg()) }
            }
            val network = mockk<Network>()
            val connectivity = mockk<ConnectivityManager> { every { activeNetwork } answers { if (online) network else null } }
            val context = mockk<Context> { every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity }
            val vm = PortabilityViewModel(session, pending, mockk(), restore, context)
            advanceUntilIdle()
            vm.restore(); advanceUntilIdle()
            assertTrue(vm.state.value.message!!.startsWith("Sin conexi\u00f3n."))
            assertEquals(0, historyCalls)
            online = true
            vm.restore(); advanceUntilIdle()
            assertEquals(RestoreStatus.PARTIAL, vm.state.value.restore!!.status)
            assertEquals(2, vm.state.value.pending!!.total)
            failGoals = false
            vm.restore(true); advanceUntilIdle()
            assertEquals(RestoreStatus.COMPLETED, vm.state.value.restore!!.status)
            assertEquals(1, historyCalls)
            assertEquals(2, goalCalls)
            assertEquals(2, vm.state.value.pending!!.total)
        } finally { Dispatchers.resetMain() }
    }
}
