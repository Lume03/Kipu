package com.kipu.app.feature.recurrences.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.notifications.data.local.ReminderEventReconciler
import com.kipu.app.feature.notifications.data.local.ReminderReconciliationCoordinator
import com.kipu.app.feature.recurrences.data.local.*
import com.kipu.app.feature.recurrences.data.repository.OfflineFirstRecurrenceRepository
import com.kipu.app.feature.recurrences.data.remote.*
import com.kipu.app.feature.recurrences.data.sync.*
import com.kipu.app.feature.recurrences.domain.*
import com.kipu.app.feature.recurrences.domain.model.RecurrenceCommandResult
import io.mockk.*
import java.time.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class RecurrenceSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KipuDatabase
    private lateinit var repository: OfflineFirstRecurrenceRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-01T02:00:00Z"), ZoneOffset.UTC)
    private val scheduler = mockk<RecurrencesSyncScheduler>(relaxed = true)
    private val reminders = mockk<RecurrenceReminderScheduler>(relaxed = true)
    private val api = mockk<RecurrencesApi>()
    private val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(OWNER) }
    private val runner = object : DatabaseTransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = db.withTransaction { block() }
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
        repository = repository(db.recurrenceOutboxDao())
        coEvery { api.pullChanges(OWNER, any()) } answers {
            RecurrencesApiResponse.Success(com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto(nextSequence = secondArg()))
        }
    }
    @After fun close() { db.close() }

    @Test fun localCreatePersistsRuleAndCommandBeforeScheduling() = runTest {
        every { scheduler.scheduleSync(OWNER) } answers {
            assertEquals(1L, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM recurrences").use { it.moveToFirst(); it.getLong(0) })
            assertEquals(1L, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM recurrence_command_outbox").use { it.moveToFirst(); it.getLong(0) })
        }
        val result = create()
        assertEquals("PENDING", db.recurrenceDao().get(OWNER, result.recurrence.id)?.syncState)
        verify(exactly = 1) { scheduler.scheduleSync(OWNER) }
    }

    @Test fun failedSchedulingKeepsCommittedCommandForRescue() = runTest {
        every { scheduler.scheduleSync(OWNER) } throws IllegalStateException("WorkManager unavailable")
        val created = create()
        assertEquals("PENDING", db.recurrenceOutboxDao().get(OWNER, created.operationId)?.state)
        assertEquals("PENDING", db.recurrenceDao().get(OWNER, created.recurrence.id)?.syncState)
    }

    @Test fun workerCancellationLeavesLeaseForIdempotentRecovery() = runTest {
        val created = create()
        coEvery { api.execute(OWNER, any(), any()) } throws kotlinx.coroutines.CancellationException("stop")
        try { worker().doWork(); fail("Cancellation must propagate") } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals("LEASED", db.recurrenceOutboxDao().get(OWNER, created.operationId)?.state)
    }

    @Test fun failedOutboxInsertRollsBackLocalRule() = runTest {
        val failing = object : RecurrenceOutboxDao by db.recurrenceOutboxDao() {
            override suspend fun insert(command: RecurrenceCommandOutboxEntity) { error("disk full") }
        }
        try { repository(failing).create(OWNER, draft()); fail("Expected write failure") } catch (_: IllegalStateException) { }
        assertEquals(0, db.recurrenceDao().observeAll(OWNER).first().size)
        verify(exactly = 0) { scheduler.scheduleSync(any()) }
    }

    @Test fun updateAndStatusRollBackWhenOutboxWriteFails() = runTest {
        val created = create()
        val failing = object : RecurrenceOutboxDao by db.recurrenceOutboxDao() {
            override suspend fun insert(command: RecurrenceCommandOutboxEntity) { error("disk full") }
        }
        val repo = repository(failing)
        val update = RecurrenceUpdate(draft().templateTransaction, "FIXED", "MONTHLY", 1, null, null, LocalDate.parse("2026-12-01"), 3)
        try { repo.update(OWNER, created.recurrence.id, 1, update); fail("Expected write failure") } catch (_: IllegalStateException) { }
        assertEquals("2026-11-01", db.recurrenceDao().get(OWNER, created.recurrence.id)?.nextDueDate)
        assertEquals(1L, db.recurrenceDao().get(OWNER, created.recurrence.id)?.revision)
        try { repo.setStatus(OWNER, created.recurrence.id, 1, "PAUSED"); fail("Expected write failure") } catch (_: IllegalStateException) { }
        assertEquals("ACTIVE", db.recurrenceDao().get(OWNER, created.recurrence.id)?.status)
        assertEquals(1, db.recurrenceOutboxDao().getForRecurrence(OWNER, created.recurrence.id).size)
        verify(exactly = 1) { scheduler.scheduleSync(OWNER) }
    }

    @Test fun appliedReceiptAndReplaySynchronizeWithoutResending() = runTest {
        val created = create()
        coEvery { api.execute(OWNER, any(), any()) } returns RecurrencesApiResponse.Success(RecurrenceRemoteResult("APPLIED", created.recurrence.id, 1))
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertEquals("SYNCED", db.recurrenceDao().get(OWNER, created.recurrence.id)?.syncState)
        assertEquals("APPLIED", db.recurrenceOutboxDao().get(OWNER, created.operationId)?.state)
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { api.execute(OWNER, any(), any()) }
    }

    @Test fun expiredLeaseReplaysSameOperationAndHash() = runTest {
        val created = create()
        val command = db.recurrenceOutboxDao().get(OWNER, created.operationId)!!
        db.recurrenceOutboxDao().update(command.copy(state = "LEASED", leaseUntil = clock.millis() - 1))
        val sent = slot<JsonObject>()
        coEvery { api.execute(OWNER, any(), capture(sent)) } returns RecurrencesApiResponse.Success(RecurrenceRemoteResult("APPLIED", created.recurrence.id, 1))
        worker().doWork()
        assertEquals(command.operationId, sent.captured["operation_id"]!!.jsonPrimitive.content)
        assertEquals(command.requestHash, sent.captured["request_hash"]!!.jsonPrimitive.content)
        assertEquals("SYNCED", db.recurrenceDao().get(OWNER, created.recurrence.id)?.syncState)
    }

    @Test fun serverConflictIsVisibleAndStopsDependentCommands() = runTest {
        val created = create()
        repository.setStatus(OWNER, created.recurrence.id, 1, "PAUSED")
        coEvery { api.execute(OWNER, any(), any()) } returns RecurrencesApiResponse.Success(
            RecurrenceRemoteResult("CONFLICT", error = RecurrenceRemoteError("REVISION_MISMATCH")),
        )
        worker().doWork()
        assertEquals("CONFLICT", db.recurrenceDao().get(OWNER, created.recurrence.id)?.syncState)
        coVerify(exactly = 1) { api.execute(OWNER, any(), any()) }
    }

    @Test fun intermediateAcceptedRevisionDoesNotConflictWithLaterLocalEdit() = runTest {
        val created = create()
        repository.setStatus(OWNER, created.recurrence.id, 1, "PAUSED")
        coEvery { api.execute(OWNER, "CREATE_RECURRENCE", any()) } returns RecurrencesApiResponse.Success(RecurrenceRemoteResult("APPLIED", created.recurrence.id, 1))
        coEvery { api.execute(OWNER, "SET_RECURRENCE_STATUS", any()) } coAnswers {
            assertEquals("PENDING", db.recurrenceDao().get(OWNER, created.recurrence.id)?.syncState)
            RecurrencesApiResponse.Success(RecurrenceRemoteResult("APPLIED", created.recurrence.id, 2))
        }
        worker().doWork()
        assertEquals("SYNCED", db.recurrenceDao().get(OWNER, created.recurrence.id)?.syncState)
        assertEquals("PAUSED", db.recurrenceDao().get(OWNER, created.recurrence.id)?.status)
    }

    @Test fun httpServerErrorRetainsCommandWithBackoffAndClientErrorFailsVisibly() = runTest {
        val created = create()
        coEvery { api.execute(OWNER, any(), any()) } returns RecurrencesApiResponse.Error(503, "unavailable")
        assertTrue(worker().doWork() is ListenableWorker.Result.Retry)
        val pending = db.recurrenceOutboxDao().get(OWNER, created.operationId)!!
        assertEquals("RETRYABLE", pending.state)
        assertTrue(pending.nextAttemptAt!! > clock.millis())
        assertTrue(db.recurrenceOutboxDao().claimReady(OWNER, clock.millis()).isEmpty())
        db.recurrenceOutboxDao().update(pending.copy(nextAttemptAt = null))
        coEvery { api.execute(OWNER, any(), any()) } returns RecurrencesApiResponse.Error(422, "invalid")
        worker().doWork()
        assertEquals("FAILED", db.recurrenceDao().get(OWNER, created.recurrence.id)?.syncState)
    }

    @Test fun changedOwnerBeforeSendDoesNotSendOrPull() = runTest {
        create()
        every { session.currentOwner } returnsMany listOf(LocalOwner(OWNER), LocalOwner(OTHER_OWNER))
        assertTrue(worker().doWork() is ListenableWorker.Result.Retry)
        coVerify(exactly = 0) { api.execute(any(), any(), any()) }
        coVerify(exactly = 0) { api.pullChanges(any(), any()) }
    }

    @Test fun feedHydratesCreateAndStatusRulesWithIndependentCheckpoint() = runTest {
        val created = create()
        coEvery { api.execute(OWNER, any(), any()) } returns RecurrencesApiResponse.Success(RecurrenceRemoteResult("APPLIED", created.recurrence.id, 1))
        val createRule = buildJsonObject {
            put("recurrence_id", "remote"); put("revision", 1); put("updated_at", "2026-10-31T22:00:00Z")
            put("rule", buildJsonObject {
                put("template_transaction", draft().templateTransaction); put("amount_mode", "FIXED"); put("frequency", "MONTHLY")
                put("interval_count", 1); put("start_date", "2026-10-01"); put("next_due_date", "2026-11-01"); put("reminder_lead_days", 2)
            })
        }
        val change = com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto(10, "RECURRENCE", "remote", 1, "UPSERT", createRule)
        coEvery { api.pullChanges(OWNER, 0) } returns RecurrencesApiResponse.Success(
            com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto(listOf(change), 10),
        )
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertEquals("SYNCED", db.recurrenceDao().get(OWNER, "remote")?.syncState)
        assertEquals("ACTIVE", db.recurrenceDao().get(OWNER, "remote")?.status)
        assertEquals(10L, db.recurrenceDao().checkpoint(OWNER)?.sequence)
        val hydration = RecurrenceHydration(db.recurrenceDao(), repository, api, session)
        repeat(2) { assertTrue(hydration.hydrateFully(OWNER)) }
        assertEquals(1L, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM recurrences WHERE id='remote'").use { it.moveToFirst(); it.getLong(0) })
    }

    @Test fun feedWithForeignOwnerIsRejectedWithoutAdvancingCheckpoint() = runTest {
        val payload = buildJsonObject {
            put("rule", buildJsonObject { put("user_id", OTHER_OWNER) })
        }
        val change = com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto(10, "RECURRENCE", "remote", 1, "UPSERT", payload)
        coEvery { api.pullChanges(OWNER, 0) } returns RecurrencesApiResponse.Success(
            com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto(listOf(change), 10),
        )
        assertTrue(worker().doWork() is ListenableWorker.Result.Retry)
        assertNull(db.recurrenceDao().checkpoint(OWNER))
    }

    @Test fun resumeUsesLimaDateAndSchedulesAfterLocalWrite() = runTest {
        val created = create()
        repository.setStatus(OWNER, created.recurrence.id, 1, "PAUSED")
        val result = repository.setStatus(OWNER, created.recurrence.id, 2, "ACTIVE", LocalDate.parse("2026-11-01"))
        assertTrue(result is RecurrenceCommandResult.Pending)
        verify(exactly = 3) { scheduler.scheduleSync(OWNER) }
    }

    private fun repository(outbox: RecurrenceOutboxDao) = OfflineFirstRecurrenceRepository(
        runner, db.recurrenceDao(), outbox, reminders, mockk<ReminderEventReconciler>(relaxed = true),
        ReminderReconciliationCoordinator(), clock, syncScheduler = scheduler,
    )
    private suspend fun create() = repository.create(OWNER, draft()) as RecurrenceCommandResult.Pending
    private fun draft() = RecurrenceDraft(
        buildJsonObject {
            put("concept", "Servicio"); put("type", "EXPENSE"); put("amount_minor", 1200L); put("currency_code", "PEN")
            listOf("source_account_id", "destination_account_id", "category_id", "merchant_id", "merchant_provisional_text").forEach { put(it, JsonNull) }
        }, "FIXED", "MONTHLY", 1, null, LocalDate.parse("2026-10-01"), null, LocalDate.parse("2026-11-01"), 2,
    )
    private fun worker() = SyncRecurrenceCommandsWorker(
        context, mockk<WorkerParameters> { every { inputData } returns workDataOf(SyncRecurrenceCommandsWorker.KEY_USER_ID to OWNER) },
        db.recurrenceOutboxDao(), db.recurrenceDao(), repository, api, session, clock,
    )
    companion object {
        const val OWNER = "61000000-0000-4000-8000-000000000001"
        const val OTHER_OWNER = "61000000-0000-4000-8000-000000000002"
    }
}
