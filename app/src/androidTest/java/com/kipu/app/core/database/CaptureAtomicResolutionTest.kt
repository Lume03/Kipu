package com.kipu.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.domain.usecase.ConfirmCreditPurchase
import com.kipu.app.feature.capture.data.RoomCaptureRegistrationReceipts
import com.kipu.app.feature.capture.data.local.*
import com.kipu.app.feature.capture.domain.*
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.movements.data.OfflineFirstMovementRepository
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.movements.domain.*
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import io.mockk.mockk
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real Room + RegisterTransaction + local ledger/outbox/receipt, including nested transactions. */
class CaptureAtomicResolutionTest {
    private val owner = UUID.randomUUID().toString()
    private lateinit var db: KipuDatabase
    private lateinit var store: RoomCaptureCandidateStore
    private lateinit var register: RegisterTransaction
    private lateinit var receipts: RoomCaptureRegistrationReceipts
    private val runner = object : DatabaseTransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = db.withTransaction { block() }
    }
    private val allowed = CaptureAccessDecision.Allowed(EffectiveEntitlement(true, null))
    private var beforeAllow: suspend () -> Unit = {}
    private val gate = object : CaptureAccessGate {
        override fun currentOwnerId() = owner
        override suspend fun evaluateCurrent() = evaluate(owner)
        override suspend fun evaluate(ownerId: String): CaptureAccessDecision { beforeAllow(); return allowed }
    }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).build()
        store = RoomCaptureCandidateStore(db, db.captureCandidateDao())
        receipts = RoomCaptureRegistrationReceipts(db)
        val projection = BalanceProjectionStore(db.movementDao())
        val repo = OfflineFirstMovementRepository(
            MovementLocalDataSource(db, db.movementDao(), projection), projection,
            TransactionRequestHasher(), MovementRevisionRequestHasher(), mockk<MovementSyncScheduler>(relaxed = true),
            db.accountDao(), db.cardDao(), db.categoryDao(), db.merchantCatalogDao(),
        )
        register = RegisterTransaction(repo, RegisterTransactionValidator())
        db.accountDao().insert(AccountEntity("bank", owner, "bank-op", "Cuenta", "SAVINGS", "PEN", null, null, null,
            0, 1, createdAt = 1, updatedAt = 1))
        db.categoryDao().insertCategory(CategoryEntity("category", null, null, "SYSTEM", createdAt = 1, updatedAt = 1))
    }
    @After fun close() = db.close()

    private fun candidate(id: String = "candidate") = CaptureCandidateEntity(
        id, owner, "com.bcp.bank.bcp", "BCP", "fingerprint-$id", "PENDING", "EXPENSE", 1000, "PEN", 1,
        false, "Comercio", null, "category", null, "DEBIT_ACCOUNT", "bank", null, false, "HIGH", "[]",
        null, null, null, null, "evidence", 10, "[]", 1, 1, null,
    ).toDomain()

    private fun confirm(candidateStore: CaptureCandidateStore = store) = ConfirmCaptureCandidate(
        candidateStore, gate, register, mockk<ConfirmCreditPurchase>(), { _, _, _ -> null }, Clock.systemUTC(), emptySet(),
        runner, receipts, CaptureResolutionFollowups(store, emptySet(), emptySet()),
    )
    private fun discard() = DiscardCaptureCandidate(store, Clock.systemUTC(), emptySet(), runner, receipts,
        CaptureResolutionFollowups(store, emptySet(), emptySet()))

    @Test fun confirmationAndAutomaticRegistrationSerializeWithDiscard() = runBlocking {
        for (mode in listOf(CaptureConfirmationMode.INBOX, CaptureConfirmationMode.AUTOMATIC)) {
            val candidate = candidate(mode.name).copy(amountMinor = if (mode == CaptureConfirmationMode.INBOX) 1000 else 2000)
            store.insertIfAbsent(candidate)
            val enteredGate = CompletableDeferred<Unit>()
            val allowCommit = CompletableDeferred<Unit>()
            beforeAllow = { enteredGate.complete(Unit); allowCommit.await() }
            val confirmation = async(Dispatchers.Default) { confirm()(ConfirmCaptureCommand.fromCandidate(candidate)!!, mode) }
            withTimeout(10_000) { enteredGate.await() }
            val discarding = async(Dispatchers.Default) { discard()(owner, candidate.id) }
            allowCommit.complete(Unit)
            assertTrue(withTimeout(10_000) { confirmation.await() } is CaptureConfirmationResult.Registered)
            assertEquals(CaptureDiscardResult.AlreadyRegistered, withTimeout(10_000) { discarding.await() })
            assertTrue(store.get(owner, candidate.id)!!.status in setOf(CaptureCandidateStatus.CONFIRMED, CaptureCandidateStatus.AUTO_REGISTERED))
        }
        assertEquals(-3000L, db.movementDao().calculateLedgerSumForAccount(owner, "bank"))
    }

    @Test fun discardWinningTheTransactionPreventsAnyFinancialWrite() = runBlocking {
        val candidate = candidate()
        store.insertIfAbsent(candidate)
        assertTrue(discard()(owner, candidate.id) is CaptureDiscardResult.Discarded)
        assertEquals(CaptureConfirmationResult.Discarded, confirm()(ConfirmCaptureCommand.fromCandidate(candidate)!!))
        assertNull(db.movementDao().getReceipt(owner, CaptureResolutionKey.of(owner, candidate.id)))
        assertEquals(0L, db.movementDao().calculateLedgerSumForAccount(owner, "bank") ?: 0L)
    }

    @Test fun failureBetweenLedgerAndResolutionRollsBackAllEffectsThenRetryRegistersOnce() = runBlocking {
        val candidate = candidate()
        store.insertIfAbsent(candidate)
        val failing = object : CaptureCandidateStore by store {
            override suspend fun resolve(record: CaptureCandidateRecord, status: CaptureCandidateStatus, transactionId: String, resolutionKey: String, now: Long): Boolean {
                throw CancellationException("Simulated process termination before resolution")
            }
        }
        try { confirm(failing)(ConfirmCaptureCommand.fromCandidate(candidate)!!); fail("expected cancellation") }
        catch (_: CancellationException) { }
        val key = CaptureResolutionKey.of(owner, candidate.id)
        assertNull(db.movementDao().getReceipt(owner, key))
        assertEquals(0L, db.movementDao().calculateLedgerSumForAccount(owner, "bank") ?: 0L)
        assertEquals(CaptureCandidateStatus.PENDING, store.get(owner, candidate.id)!!.status)
        val result = confirm()(ConfirmCaptureCommand.fromCandidate(candidate)!!) as CaptureConfirmationResult.Registered
        assertEquals(-1000L, db.movementDao().calculateLedgerSumForAccount(owner, "bank"))
        assertEquals(result.transactionId, db.movementDao().getReceipt(owner, key)!!.transactionId)
        assertEquals(CaptureFollowup.RESOLVED, store.get(owner, candidate.id)!!.followupsPending)
    }

    @Test fun legacyCommittedReceiptIsRecoveredWithoutSimilarityAgainstItself() = runBlocking {
        val candidate = candidate()
        store.insertIfAbsent(candidate)
        val key = CaptureResolutionKey.of(owner, candidate.id)
        val applied = register(RegisterTransactionCommand(key, owner, MovementType.EXPENSE, 1000, "PEN",
            sourceAccountId = "bank", categoryId = "category", occurredAt = 1)) as RegisterTransactionResult.Success
        val recovered = confirm()(ConfirmCaptureCommand.fromCandidate(candidate)!!)
        assertEquals(CaptureConfirmationResult.Registered(applied.transaction.id, true), recovered)
        assertEquals(-1000L, db.movementDao().calculateLedgerSumForAccount(owner, "bank"))
        assertEquals(CaptureCandidateStatus.CONFIRMED, store.get(owner, candidate.id)!!.status)
    }

    @Test fun failedTerminalEventInsertRollsBackCandidateAndFingerprintTogether() = runBlocking {
        val events = RoomCaptureSourceEventStore(db.captureSourceEventDao())
        val failingEvents = object : CaptureSourceEventStore by events {
            override suspend fun recordIfAbsent(event: CaptureSourceEvent): Boolean = error("Interrupted terminal write")
        }
        fun pipeline(eventStore: CaptureSourceEventStore) = ExtractCaptureCandidate(
            CaptureSourceCatalog(false), gate, eventStore, store,
            CaptureInstrumentResolver { _, _, _ -> CaptureInstrumentResolution(CaptureInstrumentKind.DEBIT_ACCOUNT, "bank") },
            mockk(), com.kipu.app.feature.categories.domain.usecase.EvaluateMerchantAlias(), Clock.systemUTC(), emptySet(),
            runner, CaptureResolutionFollowups(store, emptySet(), emptySet()),
        )
        val signal = CaptureSignal(CaptureProvider.BCP, "com.bcp.bank.bcp", "key", 1, "BCP", "Consumo de S/ 10.00")
        val fingerprint = ExtractCaptureCandidate.fingerprint(signal)
        try { pipeline(failingEvents)(owner, signal); fail("expected interrupted write") } catch (_: IllegalStateException) { }
        assertNull(store.findBySourceFingerprint(owner, fingerprint))
        assertNull(events.find(owner, fingerprint))
        assertTrue(pipeline(events)(owner, signal) is CaptureExtractionResult.Created)
        assertEquals(CaptureSourceOutcome.CANDIDATE_CREATED, events.find(owner, fingerprint)!!.outcome)
        assertNotNull(store.findBySourceFingerprint(owner, fingerprint))
    }

    @Test fun followupsSurviveClosingRoomAndEachAcknowledgedEffectStaysDone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "capture-followups-${UUID.randomUUID()}"
        var durable = Room.databaseBuilder(context, KipuDatabase::class.java, name).build()
        try {
            val resolved = candidate().copy(status = CaptureCandidateStatus.CONFIRMED, resolvedTransactionId = "tx",
                followupsPending = CaptureFollowup.RESOLVED)
            durable.captureCandidateDao().insertIfAbsent(resolved.toEntity())
            durable.captureCandidateDao().completeFollowup(owner, resolved.id, CaptureFollowup.LEARN)
            durable.close()
            durable = Room.databaseBuilder(context, KipuDatabase::class.java, name).build()
            val restarted = RoomCaptureCandidateStore(durable, durable.captureCandidateDao())
            val pending = restarted.pendingFollowups(owner).single()
            assertEquals(CaptureFollowup.EXPECTED_PAYMENT or CaptureFollowup.REVIEW_NOTICE, pending.followupsPending)
            assertTrue(restarted.pendingFollowups("other-owner").isEmpty())
            val calls = mutableListOf<Int>()
            val listeners = listOf(CaptureFollowup.LEARN, CaptureFollowup.EXPECTED_PAYMENT, CaptureFollowup.REVIEW_NOTICE).map { flag ->
                object : CaptureCandidateResolvedListener {
                    override val followupFlag = flag
                    override suspend fun onResolved(candidate: CaptureCandidateRecord, transactionId: String) { calls += flag }
                }
            }.toSet()
            CaptureResolutionFollowups(restarted, listeners, emptySet()).process(owner, resolved.id)
            assertEquals(setOf(CaptureFollowup.EXPECTED_PAYMENT, CaptureFollowup.REVIEW_NOTICE), calls.toSet())
            assertTrue(restarted.pendingFollowups(owner).isEmpty())
        } finally { durable.close(); context.deleteDatabase(name) }
    }
}
