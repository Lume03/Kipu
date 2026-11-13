package com.kipu.app.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.movements.data.sync.MovementHydration
import com.kipu.app.feature.debts.data.sync.DebtChangeFeedApplier
import com.kipu.app.feature.goals.data.sync.GoalChangeFeedApplier
import com.kipu.app.feature.recurrences.data.sync.RecurrenceHydration
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import com.kipu.app.feature.recurrences.data.local.RecurrenceSyncCheckpointEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class SharedChangeFeedRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KipuDatabase
    private val owner = "owner"
    private val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(owner) }
    private val api = mockk<FinancialInstrumentsApi>()
    private val repository = mockk<RecurrenceRepository>()
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(context,KipuDatabase::class.java).allowMainThreadQueries().build()
        coEvery { repository.applyChangeFeed(owner,any(),any()) } coAnswers {
            db.recurrenceDao().saveCheckpoint(RecurrenceSyncCheckpointEntity(owner,secondArg(),0))
        }
    }
    @After fun close() { db.close() }
    private fun reader()=SharedChangeFeed(api,db,session,
        MovementHydration(db.movementDao(),api,db.accountDao(),db.cardDao(),db,session),
        DebtChangeFeedApplier(db,db.debtDao(),db.movementDao()),
        GoalChangeFeedApplier(db,db.goalDao(),db.goalOutboxDao(),db.accountDao(),db.movementDao()),
        RecurrenceHydration(db.recurrenceDao(),repository,mockk(),session))
    private fun change(seq:Long,type:String,id:String,payload:String)=SyncChangeItemDto(seq,type,id,1,"UPSERT",Json.parseToJsonElement(payload))
    private fun debt(seq:Long,id:String="debt")=change(seq,"DEBT",id,
        """{"id":"$id","user_id":"$owner","obligation_type":"PAYABLE","counterparty_name":"Ana","total_minor":1000,"currency_code":"PEN","opened_on":"2026-11-01","opening_mode":"HISTORICAL","status":"ACTIVE","revision":1,"created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-01T00:00:00Z"}""")
    private fun account(seq:Long)=change(seq,"ACCOUNT","cash",
        """{"id":"cash","alias":"Caja","type":"CASH","currency":"PEN","initial_balance_minor_units":0,"opened_at":"2026-11-01T00:00:00Z"}""")
    private fun goal(seq:Long)=change(seq,"GOAL","goal",
        """{"id":"goal","user_id":"$owner","name":"Viaje","target_minor":5000,"currency_code":"PEN","status":"ACTIVE","revision":1,"created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-01T00:00:00Z"}""")
    private fun feed(vararg changes:SyncChangeItemDto) {
        coEvery { api.pullChanges(any(),owner) } answers {
            val cursor=firstArg<PullChangesRequestDto>().afterSequence
            val remaining=changes.filter { it.sequence>cursor }
            FinancialApiResponse.Success(PullChangesResponseDto(remaining,remaining.lastOrNull()?.sequence ?: cursor))
        }
    }
    @Test fun mixedPageDownloadsOnceAndCommitsEachExistingApplier()=runTest {
        val movement=change(2,"TRANSACTION","income",
            """{"id":"income","type":"INCOME","amount_minor":2000,"currency_code":"PEN","destination_account_id":"cash","occurred_at":"2026-11-01T00:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"entry","account_id":"cash","role":"DESTINATION","signed_amount_minor":2000,"currency_code":"PEN"}]}""")
        feed(account(1),movement,debt(3),goal(4))
        assertTrue(reader().pullChanges(owner))
        coVerify(exactly=1) { api.pullChanges(any(),owner) }
        assertNotNull(db.accountDao().getById(owner,"cash"))
        assertNotNull(db.movementDao().getTransactionById(owner,"income"))
        assertNotNull(db.debtDao().getDebtIncludingDeleted(owner,"debt"))
        assertNotNull(db.goalDao().getGoalIncludingArchived(owner,"goal"))
        assertEquals(4L,db.movementDao().getSyncCheckpoint(owner)?.sequence)
        assertEquals(4L,db.debtDao().getSyncCheckpoint(owner)?.sequence)
        assertEquals(4L,db.goalDao().getSyncCheckpoint(owner)?.sequence)
    }
    @Test fun invalidDebtRollsBackOnlyItsModuleAndRetryDoesNotReapplyOthers()=runTest {
        feed(account(1),debt(2).copy(payload=Json.parseToJsonElement("{}")),goal(3))
        assertFalse(reader().pullChanges(owner))
        assertNull(db.debtDao().getSyncCheckpoint(owner))
        assertEquals(3L,db.movementDao().getSyncCheckpoint(owner)?.sequence)
        assertEquals(3L,db.goalDao().getSyncCheckpoint(owner)?.sequence)
        feed(account(1),debt(2),goal(3))
        assertTrue(reader().pullChanges(owner))
        assertEquals(3L,db.debtDao().getSyncCheckpoint(owner)?.sequence)
    }
    @Test fun staleDebtConflictDoesNotBlockAnotherDebtOrOtherModules()=runTest {
        feed(debt(1)); assertTrue(reader().pullChanges(owner))
        db.openHelper.writableDatabase.execSQL("UPDATE debts SET revision=2,sync_state='PENDING' WHERE user_id=?",arrayOf(owner))
        feed(debt(2),debt(3,"other"),account(4),goal(5))
        assertTrue(reader().pullChanges(owner))
        assertEquals("CONFLICT",db.debtDao().getDebtIncludingDeleted(owner,"debt")?.syncState)
        assertNotNull(db.debtDao().getDebtIncludingDeleted(owner,"other"))
        assertNotNull(db.goalDao().getGoalIncludingArchived(owner,"goal"))
        assertEquals(5L,db.debtDao().getSyncCheckpoint(owner)?.sequence)
        assertEquals(1,db.debtOutboxDao().getRemoteSnapshots(owner,"debt").size)
    }
    @Test fun changedOwnerDuringDownloadCannotApplyAnyModule()=runTest {
        coEvery { api.pullChanges(any(),owner) } answers {
            every { session.currentOwner } returns LocalOwner("other-owner")
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(account(1)),1))
        }
        try { reader().pullChanges(owner); fail("Owner switch must cancel the pull") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertNull(db.movementDao().getSyncCheckpoint(owner))
        assertNull(db.debtDao().getSyncCheckpoint(owner))
        assertNull(db.goalDao().getSyncCheckpoint(owner))
        assertNull(db.recurrenceDao().checkpoint(owner))
        assertNull(db.accountDao().getById(owner,"cash"))
    }

    @Test fun schema41CheckpointSurvivesReopenAndDoesNotReplayHistory()=runTest {
        db.close()
        val name="shared-feed-checkpoint-test"
        context.deleteDatabase(name)
        try {
            db=Room.databaseBuilder(context,KipuDatabase::class.java,name).allowMainThreadQueries().build()
            assertEquals(41,db.openHelper.writableDatabase.version)
            db.movementDao().saveSyncCheckpoint(com.kipu.app.feature.movements.data.local.MovementSyncCheckpointEntity(owner,100))
            db.debtDao().upsertSyncCheckpoint(com.kipu.app.feature.debts.data.local.DebtSyncCheckpointEntity(owner,110,0))
            db.goalDao().upsertSyncCheckpoint(com.kipu.app.feature.goals.data.local.GoalSyncCheckpointEntity(owner,105,0))
            db.recurrenceDao().saveCheckpoint(RecurrenceSyncCheckpointEntity(owner,110,0))
            db.close()
            db=Room.databaseBuilder(context,KipuDatabase::class.java,name).allowMainThreadQueries().build()
            feed(account(103),goal(110))
            assertTrue(reader().pullChanges(owner))
            coVerify(exactly=1) { api.pullChanges(match { it.afterSequence==100L },owner) }
            assertEquals(110L,db.movementDao().getSyncCheckpoint(owner)?.sequence)
            assertEquals(110L,db.debtDao().getSyncCheckpoint(owner)?.sequence)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
