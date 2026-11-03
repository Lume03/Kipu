package com.kipu.app.feature.portability

import android.content.Context
import android.net.Uri
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortabilityViewModelTest {
    @Test fun pendingPartialRetryWriteFailureAndOwnerSwitchAreVisible() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = mockk<SessionCoordinator>()
            every { session.currentOwner } returns LocalOwner("owner")
            val pending = mockk<PendingLocalChanges>()
            coEvery { pending("owner") } returns PendingLocalSummary(mapOf("movement_outbox" to 2))
            val store = mockk<PortabilityStore>()
            coEvery { store.json("owner") } throws java.io.IOException()
            val restore = mockk<RestoreUserData>()
            val partial = RestoreResult("owner", mapOf("goals" to false, "movements" to true))
            coEvery { restore("owner", null) } returns partial
            coEvery { restore("owner", partial) } returns partial.copy(modules = mapOf("goals" to true, "movements" to true))
            val vm = PortabilityViewModel(session, pending, store, restore, mockk<Context>(relaxed = true))
            advanceUntilIdle()
            assertEquals(2, vm.state.value.pending?.total)
            vm.restore(); advanceUntilIdle()
            assertEquals(RestoreStatus.PARTIAL, vm.state.value.restore?.status)
            vm.restore(true); advanceUntilIdle()
            assertEquals(RestoreStatus.COMPLETED, vm.state.value.restore?.status)
            vm.export(mockk<Uri>(), true); advanceUntilIdle()
            assertTrue(vm.state.value.message!!.startsWith("No se pudo"))
            every { session.currentOwner } returns LocalOwner("other")
            vm.restore(); advanceUntilIdle()
            coVerify(exactly = 2) { restore(any(), any()) }
            assertFalse(vm.state.value.busy)
        } finally { Dispatchers.resetMain() }
    }
}
