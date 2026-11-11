package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.movements.domain.RegisterRefundTransaction
import com.kipu.app.feature.movements.domain.model.ExpenseConsumptionQuery
import com.kipu.app.feature.movements.domain.model.RefundTransactionCommand
import com.kipu.app.feature.movements.domain.model.RefundTransactionResult
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class MovementTerminalRejectionRoomTest {
    private lateinit var database: KipuDatabase
    private lateinit var dao: MovementDao
    private lateinit var source: MovementLocalDataSource
    private val userId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.movementDao()
        source = MovementLocalDataSource(database, dao, BalanceProjectionStore(dao))
        runBlocking {
            database.accountDao().insert(
                AccountEntity(
                    id = ACCOUNT_ID,
                    userId = userId,
                    creationOperationId = UUID.randomUUID().toString(),
                    alias = "Efectivo",
                    type = "CASH",
                    currency = "PEN",
                    presetId = null,
                    color = null,
                    icon = null,
                    initialBalanceMinorUnits = 0L,
                    openedAt = 1L,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
        }
    }

    @After
    fun tearDown() = database.close()

    @Test fun terminalOutboxOverridesStalePendingAndDiscardRemovesUnacceptedMovement() = runBlocking {
        val tx = transaction()
        dao.insertTransaction(tx)
        dao.insertLedgerEntries(listOf(LedgerEntryEntity("entry", userId, tx.id, ACCOUNT_ID, "PRIMARY", -100, "PEN")))
        dao.insertOutbox(outbox(tx.id))
        assertEquals("FAILED_PERMANENT", source.getTransactionById(userId, tx.id)?.syncStatus)
        assertEquals(true, source.discardRejectedMovement(userId, tx.id))
        assertEquals("DISCARDED", dao.getAllOutboxForTransaction(userId, tx.id).single().state)
        assertEquals(0L, dao.getLedgerEntriesForTransaction(userId, tx.id).sumOf { it.signedAmountMinor })
        assertEquals(0L, dao.getBalanceProjection(userId, ACCOUNT_ID)?.balanceMinor)
        assertEquals(emptyList<TransactionEntity>(), source.observeTransactions(userId).first())
        assertEquals(0, com.kipu.app.feature.portability.PendingLocalChanges(database)(userId).total)
        assertEquals(false, source.discardRejectedMovement(userId, tx.id))
    }

    @Test fun pendingAndRetryCannotBeDiscarded() = runBlocking {
        for (state in listOf("PENDING", "RETRY")) {
            val tx = transaction(state)
            dao.insertTransaction(tx)
            dao.insertOutbox(outbox(tx.id).copy(id=state, idempotencyKey=state,state=state))
            assertEquals("PENDING", source.getTransactionById(userId, tx.id)?.syncStatus)
            assertEquals(false, source.discardRejectedMovement(userId, tx.id))
            assertEquals(state, dao.getAllOutboxForTransaction(userId, tx.id).single().state)
        }
    }

    @Test fun discardRevisionRestoresConfirmedHeadAndBalance() = runBlocking {
        val original = transaction().copy(syncStatus="SYNCED", acknowledgedRevision=1L, currentRevisionId="official")
        val revised = original.copy(amountMinor=150, revision=2L, currentRevisionId="local", syncStatus="PENDING")
        dao.insertTransaction(revised)
        dao.insertRevision(TransactionRevisionEntity(userId,"official",original.id,"accepted","REGISTER",0,1,null,MovementRevisionSnapshotCodec.encode(original),null,"REMOTE",1))
        dao.insertRevision(TransactionRevisionEntity(userId,"local",original.id,"command","REVISE",1,2,MovementRevisionSnapshotCodec.encode(original),MovementRevisionSnapshotCodec.encode(revised),null,"LOCAL",2))
        dao.insertLedgerEntries(listOf(LedgerEntryEntity("base",userId,original.id,ACCOUNT_ID,"PRIMARY",-100,"PEN"), LedgerEntryEntity("delta",userId,original.id,ACCOUNT_ID,"PRIMARY",-50,"PEN")))
        dao.insertLedgerEffect(MovementLedgerEffectEntity(userId,"command",0,original.id,"local","delta",null,null))
        dao.insertOutbox(outbox(original.id).copy(commandType="REVISE_TRANSACTION", revisionId="local"))
        assertEquals(true, source.discardRejectedMovement(userId,original.id))
        val restored=dao.getTransactionById(userId,original.id)!!
        assertEquals(100L,restored.amountMinor)
        assertEquals(1L,restored.acknowledgedRevision)
        assertEquals("SYNCED",restored.syncStatus)
        assertEquals(-100L,dao.getLedgerEntriesForTransaction(userId,original.id).sumOf { it.signedAmountMinor })
        assertEquals(-100L,dao.getBalanceProjection(userId,ACCOUNT_ID)?.balanceMinor)
    }

    private fun transaction(id: String="tx") = TransactionEntity(id,userId,"EXPENSE",100,"PEN",sourceAccountId=ACCOUNT_ID,occurredAt=1,operationKind="STANDARD")
    private fun outbox(tx: String) = MovementOutboxEntity(id="outbox",userId=userId,idempotencyKey="command",aggregateId=tx,payload="{}",state="FAILED_PERMANENT",lastErrorCode="REQUEST_HASH_MISMATCH")
    companion object { const val ACCOUNT_ID="terminal-test-account" }
}
