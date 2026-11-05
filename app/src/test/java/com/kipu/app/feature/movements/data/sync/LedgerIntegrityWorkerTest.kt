package com.kipu.app.feature.movements.data.sync

import com.kipu.app.feature.debts.data.sync.NoOpDebtSyncScheduler
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.local.*
import com.kipu.app.feature.movements.data.remote.*
import com.kipu.app.feature.movements.domain.*
import com.kipu.app.feature.movements.domain.model.*
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LedgerIntegrityWorkerTest {
    private val userId = UUID.randomUUID().toString()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: KipuDatabase
    private lateinit var dao: MovementDao
    private lateinit var local: MovementLocalDataSource
    private val api = mockk<MovementApi>()
    private val financialApi = mockk<FinancialInstrumentsApi>()
    private val session = mockk<SessionCoordinator>(relaxed = true)
    private val params = mockk<WorkerParameters>(relaxed = true)
    private val prefs get() = context.getSharedPreferences("movement_contract_recovery", Context.MODE_PRIVATE)
    private val repairKey get() = "ledger-net-20261026-v1:$userId"

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
        dao = database.movementDao()
        local = MovementLocalDataSource(database, dao, BalanceProjectionStore(dao))
        every { params.inputData } returns workDataOf(SyncMovementsWorker.KEY_USER_ID to userId)
        every { session.currentOwner } returns LocalOwner(userId)
        every { session.remoteSession } returns MutableStateFlow(RemoteSession.Absent)
        coEvery { financialApi.pullChanges(any(), any()) } answers {
            FinancialApiResponse.Success(PullChangesResponseDto(emptyList(), firstArg<PullChangesRequestDto>().afterSequence, false))
        }
        for (id in listOf("cash", "destination", "other")) {
            database.accountDao().insert(AccountEntity(id = id, userId = userId, creationOperationId = UUID.randomUUID().toString(),
                alias = id, type = "CASH", currency = "PEN", presetId = null, color = null, icon = null,
                initialBalanceMinorUnits = 0L, openedAt = 1L, createdAt = 1L, updatedAt = 1L))
        }
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun rejectedTransferPreservesEvidenceAndReversesBothAccountsExactlyOnce() = runBlocking {
        val tx = register(command(MovementType.TRANSFER))
        val outbox = dao.getPendingOutboxForTransaction(userId, tx.id).single()
        coEvery { api.registerTransaction(any(), any()) } returns MovementApiResponse.Success(RegisterTransactionResponseDto(status = "REJECTED"))
        worker().doWork()
        assertEquals("VOIDED", dao.getTransactionById(userId, tx.id)?.status)
        assertEquals("REJECTED", dao.getReceipt(userId, outbox.idempotencyKey)?.status)
        assertEquals("FAILED_PERMANENT", dao.getOutboxById(userId, outbox.id)?.state)
        assertEquals(0L, dao.getBalanceProjection(userId, "cash")?.balanceMinor)
        assertEquals(0L, dao.getBalanceProjection(userId, "destination")?.balanceMinor)
        val evidence = dao.getLedgerEntriesForTransaction(userId, tx.id)
        assertTrue(evidence.any { it.signedAmountMinor == -2_000L })
        worker().doWork()
        assertEquals(evidence, dao.getLedgerEntriesForTransaction(userId, tx.id))
    }

    @Test
    fun permanentHttp422CompensatesBut401And403KeepCommittedMoney() = runBlocking {
        for (status in listOf(401, 403, 422)) {
            val tx = register(command())
            coEvery { api.registerTransaction(any(), any()) } returns MovementApiResponse.Error(status, null)
            worker().doWork()
            val stored = requireNotNull(dao.getTransactionById(userId, tx.id))
            assertEquals(if (status == 422) "VOIDED" else "ACTIVE", stored.status)
            assertEquals(if (status == 422) 0L else 2_000L,
                dao.getLedgerEntriesForTransaction(userId, tx.id).sumOf { it.signedAmountMinor })
            // Finish auth retries so each iteration exercises only its own command.
            dao.getPendingOutboxForTransaction(userId, tx.id).forEach {
                dao.updateOutboxResult(userId, it.id, "SYNCED", null, null)
            }
        }
    }

    @Test
    fun rejectedOfflineRevisionRestoresOriginalHeadAndDoesNotVoidOriginal() = runBlocking {
        val tx = register(command(MovementType.TRANSFER))
        acceptRegistration(tx.id)
        val original = requireNotNull(local.getRevisionHead(userId, tx.id))
        val revise = MovementRevisionCommand.Revise(UUID.randomUUID().toString(), tx.id, original.commandBaseRevision,
            payload = original.payload.copy(amountMinor = 1_500L, destinationAccountId = "other"))
        assertTrue(local.commitRevisionAtomic(userId, revise, MovementRevisionRequestHasher().computeHash(revise)) is MovementMutationResult.Success)
        coEvery { api.reviseOrVoidTransaction(any(), any()) } returns MovementApiResponse.Success(
            ReviseOrVoidTransactionResponseDto(status = "REJECTED"))
        worker().doWork()
        val restored = requireNotNull(dao.getTransactionById(userId, tx.id))
        assertEquals("ACTIVE", restored.status)
        assertEquals(2_000L, restored.amountMinor)
        assertEquals(1L, restored.revision)
        assertEquals("destination", restored.destinationAccountId)
        assertEquals(-2_000L, dao.getBalanceProjection(userId, "cash")?.balanceMinor)
        assertEquals(2_000L, dao.getBalanceProjection(userId, "destination")?.balanceMinor)
        assertEquals(0L, dao.getBalanceProjection(userId, "other")?.balanceMinor)
        val entries = dao.getLedgerEntriesForTransaction(userId, tx.id)
        assertTrue(dao.getRevisions(userId, tx.id).any { it.commandType == "VOID" && it.provenance == "RECONCILIATION" })
        worker().doWork()
        assertEquals(entries, dao.getLedgerEntriesForTransaction(userId, tx.id))
    }

    @Test
    fun failedEligibleRepairDoesNotSaveMarkerAndNextPassRepairsExactlyOnce() = runBlocking {
        val tx = register(command())
        acceptRegistration(tx.id)
        dao.insertLedgerEntries(listOf(LedgerEntryEntity("duplicated", userId, tx.id, "cash", "DESTINATION", 2_000L, "PEN")))
        dao.saveSyncCheckpoint(MovementSyncCheckpointEntity(userId, 1L))
        fun page(entries: String) = PullChangesResponseDto(listOf(SyncChangeItemDto(1L, "TRANSACTION", tx.id, 1L, "UPSERT",
            Json.parseToJsonElement("""{"id":"${tx.id}","type":"INCOME","amount_minor":2000,"currency_code":"PEN","source_account_id":"cash","occurred_at":"2026-10-25T17:00:00Z","status":"ACTIVE","ledger_entries":$entries}"""))), 1L, false)
        coEvery { financialApi.pullChanges(match { it.afterSequence == 0L }, any()) } returns FinancialApiResponse.Success(page("[]"))
        worker().doWork()
        assertFalse(prefs.getBoolean(repairKey, false))
        assertEquals(4_000L, dao.calculateLedgerSumForAccount(userId, "cash"))
        coEvery { financialApi.pullChanges(match { it.afterSequence == 0L }, any()) } returns FinancialApiResponse.Success(
            page("""[{"account_id":"cash","role":"DESTINATION","signed_amount_minor":2000,"currency_code":"PEN"}]"""))
        worker().doWork()
        assertTrue(prefs.getBoolean(repairKey, false))
        assertEquals(2_000L, dao.getBalanceProjection(userId, "cash")?.balanceMinor)
        val entries = dao.getLedgerEntriesForTransaction(userId, tx.id)
        worker().doWork()
        assertEquals(entries, dao.getLedgerEntriesForTransaction(userId, tx.id))
    }

    @Test
    fun exactReplayAndHashConflictTakePriorityOverSimilarityForRealRoomRepository() = runBlocking {
        val repository = OfflineFirstMovementRepository(local, BalanceProjectionStore(dao), TransactionRequestHasher(),
            MovementRevisionRequestHasher(), mockk(relaxed = true), database.accountDao(), database.cardDao(),
            database.categoryDao(), database.merchantCatalogDao())
        val useCase = RegisterTransaction(repository, RegisterTransactionValidator())
        val cmd = command()
        val first = useCase(cmd) as RegisterTransactionResult.Success
        val replay = useCase(cmd)
        assertTrue(replay is RegisterTransactionResult.Success)
        assertEquals(first.transaction, (replay as RegisterTransactionResult.Success).transaction)
        assertTrue(replay.isDuplicate)
        assertTrue(useCase(cmd.copy(note = "changed")) is RegisterTransactionResult.Conflict)
        assertTrue(useCase(cmd.copy(idempotencyKey = UUID.randomUUID().toString())) is RegisterTransactionResult.SimilarTransactionWarning)
        assertEquals(1, dao.getLedgerEntriesForTransaction(userId, first.transaction.id).size)
    }

    @Test
    fun rejectionRollsBackAllEvidenceAndOutboxIfProjectionWriteFailsAndStaleResponseIsIgnored() = runBlocking {
        val tx = register(command())
        val claimed = dao.claimPendingOutbox(userId, System.currentTimeMillis()).single()
        val failingDao = object : MovementDao by dao {
            override suspend fun upsertBalanceProjection(projection: BalanceProjectionEntity) {
                error("projection unavailable")
            }
        }
        try {
            MovementRejectionResolver(database, failingDao).rejectClaimed(claimed, "REJECTED")
            fail("Projection failure must roll back the entire rejection")
        } catch (_: IllegalStateException) { }
        assertEquals("ACTIVE", dao.getTransactionById(userId, tx.id)?.status)
        assertEquals("IN_FLIGHT", dao.getOutboxById(userId, claimed.id)?.state)
        assertEquals("APPLIED", dao.getReceipt(userId, claimed.idempotencyKey)?.status)
        assertTrue(dao.getRevisions(userId, tx.id).isEmpty())
        assertEquals(1, dao.getLedgerEntriesForTransaction(userId, tx.id).size)
        val resolver = MovementRejectionResolver(database, dao)
        assertFalse(resolver.rejectClaimed(claimed.copy(leaseUntil = 0L), "REJECTED"))
        assertTrue(resolver.rejectClaimed(claimed, "REJECTED"))
        val entries = dao.getLedgerEntriesForTransaction(userId, tx.id)
        assertFalse(resolver.rejectClaimed(claimed, "REJECTED"))
        assertEquals(entries, dao.getLedgerEntriesForTransaction(userId, tx.id))
    }

    @Test
    fun rejectedRevisionBlocksAndNeutralizesUnsentChildWhileKeepingAcceptedBase() = runBlocking {
        val tx = register(command())
        acceptRegistration(tx.id)
        val original = requireNotNull(local.getRevisionHead(userId, tx.id))
        val parent = MovementRevisionCommand.Revise(UUID.randomUUID().toString(), tx.id, original.commandBaseRevision,
            payload = original.payload.copy(amountMinor = 1_500L))
        local.commitRevisionAtomic(userId, parent, MovementRevisionRequestHasher().computeHash(parent))
        val next = requireNotNull(local.getRevisionHead(userId, tx.id))
        val child = MovementRevisionCommand.Revise(UUID.randomUUID().toString(), tx.id, next.commandBaseRevision,
            payload = next.payload.copy(amountMinor = 1_000L), dependsOnCommandId = parent.idempotencyKey)
        local.commitRevisionAtomic(userId, child, MovementRevisionRequestHasher().computeHash(child))
        // Ensure the rejected parent is claimed first, independently of timestamp ties.
        val parentOutbox = dao.getPendingOutboxForTransaction(userId, tx.id).single { it.idempotencyKey == parent.idempotencyKey }
        dao.setOutboxLease(userId, parentOutbox.id, "IN_FLIGHT", 123L, 100L)
        val claimed = requireNotNull(dao.getOutboxById(userId, parentOutbox.id))
        assertTrue(MovementRejectionResolver(database, dao).rejectClaimed(claimed, "REJECTED"))
        assertEquals(2_000L, dao.getTransactionById(userId, tx.id)?.amountMinor)
        assertEquals(2_000L, dao.getBalanceProjection(userId, "cash")?.balanceMinor)
        assertTrue(dao.getPendingOutboxForTransaction(userId, tx.id).isEmpty())
        assertEquals("REJECTED", dao.getReceipt(userId, child.idempotencyKey)?.status)
    }

    @Test
    fun exceptionAfterEligibleRepairRollsBackAndLeavesMarkerForNextPass() = runBlocking {
        val tx = register(command())
        acceptRegistration(tx.id)
        dao.insertLedgerEntries(listOf(LedgerEntryEntity("duplicated", userId, tx.id, "cash", "DESTINATION", 2_000L, "PEN")))
        dao.saveSyncCheckpoint(MovementSyncCheckpointEntity(userId, 1L))
        coEvery { financialApi.pullChanges(match { it.afterSequence == 0L }, any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOf(SyncChangeItemDto(1L, "TRANSACTION", tx.id, 1L, "UPSERT", Json.parseToJsonElement(
                """{"id":"${tx.id}","type":"INCOME","amount_minor":2000,"currency_code":"PEN","source_account_id":"cash","occurred_at":"2026-10-25T17:00:00Z","status":"ACTIVE","ledger_entries":[{"account_id":"cash","role":"DESTINATION","signed_amount_minor":2000,"currency_code":"PEN"}]}"""))), 1L, false))
        val failingDao = object : MovementDao by dao {
            override suspend fun upsertBalanceProjection(projection: BalanceProjectionEntity) { error("write failed") }
        }
        worker(failingDao).doWork()
        assertFalse(prefs.getBoolean(repairKey, false))
        assertEquals(4_000L, dao.calculateLedgerSumForAccount(userId, "cash"))
        assertEquals(2, dao.getLedgerEntriesForTransaction(userId, tx.id).size)
        worker().doWork()
        assertTrue(prefs.getBoolean(repairKey, false))
        assertEquals(2_000L, dao.calculateLedgerSumForAccount(userId, "cash"))
        assertEquals(3, dao.getLedgerEntriesForTransaction(userId, tx.id).size)
    }

    @Test
    fun pendingLocalCommandIsAnIntentionalRepairSkipRatherThanAnEligibleFailure() = runBlocking {
        val tx = register(command())
        dao.saveSyncCheckpoint(MovementSyncCheckpointEntity(userId, 1L))
        coEvery { api.registerTransaction(any(), any()) } returns MovementApiResponse.NetworkFailure(java.io.IOException())
        coEvery { financialApi.pullChanges(match { it.afterSequence == 0L }, any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOf(SyncChangeItemDto(1L, "TRANSACTION", tx.id, 1L, "UPSERT", Json.parseToJsonElement(
                """{"id":"${tx.id}","type":"INCOME","amount_minor":2000,"currency_code":"PEN","source_account_id":"cash","occurred_at":"2026-10-25T17:00:00Z","status":"ACTIVE","ledger_entries":[]}"""))), 1L, false))
        worker().doWork()
        assertTrue(prefs.getBoolean(repairKey, false))
        assertEquals(2_000L, dao.getBalanceProjection(userId, "cash")?.balanceMinor)
        assertEquals(1, dao.getLedgerEntriesForTransaction(userId, tx.id).size)
        assertEquals("RETRY", dao.getPendingOutboxForTransaction(userId, tx.id).single().state)
    }

    @Test
    fun retryableBusinessResponseKeepsTheLocalCommit() = runBlocking {
        val tx = register(command())
        coEvery { api.registerTransaction(any(), any()) } returns MovementApiResponse.Success(
            RegisterTransactionResponseDto(status = "REJECTED", error = RemoteErrorDto("TEMPORARY", retryable = true)))
        worker().doWork()
        assertEquals("ACTIVE", dao.getTransactionById(userId, tx.id)?.status)
        assertEquals(2_000L, dao.getBalanceProjection(userId, "cash")?.balanceMinor)
        assertEquals("RETRY", dao.getPendingOutboxForTransaction(userId, tx.id).single().state)
        assertTrue(dao.getRevisions(userId, tx.id).isEmpty())
    }

    private fun command(type: MovementType = MovementType.INCOME) = RegisterTransactionCommand(
        UUID.randomUUID().toString(), userId, type, 2_000L, "PEN", "cash",
        destinationAccountId = if (type == MovementType.TRANSFER) "destination" else null, occurredAt = 1_000L)

    private suspend fun register(cmd: RegisterTransactionCommand): Transaction =
        (local.commitTransactionAtomic(cmd, TransactionRequestHasher().computeHash(cmd)) as RegisterTransactionResult.Success).transaction

    private suspend fun acceptRegistration(transactionId: String) {
        dao.getPendingOutboxForTransaction(userId, transactionId).forEach { dao.updateOutboxResult(userId, it.id, "SYNCED", null, null) }
        dao.updateTransactionSyncStatus(userId, transactionId, "SYNCED")
    }

    private fun worker(workerDao: MovementDao = dao) = SyncMovementsWorker(context, params, workerDao, api, financialApi,
        database.accountDao(), database.cardDao(), database, session, NoOpDebtSyncScheduler)
}
