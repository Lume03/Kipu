package com.kipu.app.feature.movements.data.sync

import android.app.Application
import androidx.room.Room
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinatorImpl
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.movements.data.remote.MovementApi
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SyncOwnerIsolationTest {
    @Test fun `owner switches during a later remote page so its data never enters old owner Room`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        // Skip unrelated historical repair: Robolectric's SQLite lacks json_extract on API 28.
        context.getSharedPreferences("movement_contract_recovery", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("wallet-channel-20261021-v4:A", true).commit()
        val db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
        try {
            val coordinator = SessionCoordinatorImpl()
            coordinator.setActiveOwner("A")
            val api = mockk<FinancialInstrumentsApi>()
            val params = mockk<WorkerParameters>(relaxed = true)
            every { params.inputData } returns workDataOf(SyncMovementsWorker.KEY_USER_ID to "A")
            var requests = 0
            coEvery { api.pullChanges(any(), "A") } coAnswers {
                requests++
                if (requests == 2) coordinator.setActiveOwner("B")
                FinancialApiResponse.Success(PullChangesResponseDto(
                    changes = listOf(SyncChangeItemDto(requests.toLong(), "CATEGORY", "id", 1, "UPSERT")),
                    nextSequence = requests.toLong(), hasMore = true))
            }
            val worker = SyncMovementsWorker(context, params, db.movementDao(), mockk<MovementApi>(), api,
                db.accountDao(), db.cardDao(), db, coordinator)
            val failure = runCatching { worker.doWork() }.exceptionOrNull()
            if (failure !is CancellationException) throw AssertionError("Expected owner cancellation, got $failure", failure)
            assertEquals(2, requests)
            assertEquals(1L, db.movementDao().getSyncCheckpoint("A")!!.sequence)
            assertNull(db.movementDao().getSyncCheckpoint("B"))
        } finally { db.close() }
    }

    @Test fun `page transaction rolls back if owner changes during its writes`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        // Skip unrelated historical repair: Robolectric's SQLite lacks json_extract on API 28.
        context.getSharedPreferences("movement_contract_recovery", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("wallet-channel-20261021-v4:A", true).commit()
        val db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
        try {
            val coordinator = SessionCoordinatorImpl()
            coordinator.setActiveOwner("A")
            val dao = spyk(db.movementDao())
            coEvery { dao.saveSyncCheckpoint(any()) } coAnswers {
                // This write occurs inside the page transaction; the commit guard must roll it back.
                db.movementDao().saveSyncCheckpoint(firstArg())
                coordinator.setActiveOwner("B")
            }
            val api = mockk<FinancialInstrumentsApi>()
            coEvery { api.pullChanges(any(), "A") } returns FinancialApiResponse.Success(PullChangesResponseDto(
                changes = listOf(SyncChangeItemDto(1, "CATEGORY", "id", 1, "UPSERT")), nextSequence = 1))
            val params = mockk<WorkerParameters>(relaxed = true)
            every { params.inputData } returns workDataOf(SyncMovementsWorker.KEY_USER_ID to "A")
            val worker = SyncMovementsWorker(context, params, dao, mockk<MovementApi>(), api,
                db.accountDao(), db.cardDao(), db, coordinator)
            val failure = runCatching { worker.doWork() }.exceptionOrNull()
            if (failure !is CancellationException) throw AssertionError("Expected owner cancellation, got $failure", failure)
            assertNull(db.movementDao().getSyncCheckpoint("A"))
        } finally { db.close() }
    }
}
