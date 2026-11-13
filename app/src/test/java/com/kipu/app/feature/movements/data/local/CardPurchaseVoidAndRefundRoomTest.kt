package com.kipu.app.feature.movements.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.local.*
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.movements.data.sync.MovementHydration
import com.kipu.app.feature.movements.domain.*
import com.kipu.app.feature.movements.domain.model.*
import io.mockk.*
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class CardPurchaseVoidAndRefundRoomTest {
    private lateinit var db: KipuDatabase
    private lateinit var local: MovementLocalDataSource
    private val owner = UUID.randomUUID().toString()
    private val purchase = UUID.randomUUID().toString()
    private val api = mockk<FinancialInstrumentsApi>()
    private val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(owner) }
    @Before fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).allowMainThreadQueries().build()
        local = MovementLocalDataSource(db, db.movementDao(), BalanceProjectionStore(db.movementDao()))
        db.accountDao().insert(AccountEntity(id="liability", userId=owner, creationOperationId="op", alias="Visa", type="CREDIT_LIABILITY",
            currency="PEN", presetId=null, color=null, icon=null, initialBalanceMinorUnits=0, openedAt=1, createdAt=1, updatedAt=1))
        db.cardDao().insert(CardEntity(id="card", userId=owner, creationOperationId="card-op", accountId="liability", type="CREDIT", currency="PEN",
            network="VISA", issuer="BCP", lastFourDigits="1234", createdAt=1, updatedAt=1))
    }
    @After fun close() { db.close() }
    private suspend fun insertPurchase() {
        db.movementDao().insertTransaction(TransactionEntity(id=purchase,userId=owner,type="EXPENSE",amountMinor=10000,currencyCode="PEN",
            occurredAt=1,status="CONFIRMED",syncStatus="SYNCED",cardId="card",operationKind="CARD_PURCHASE",installmentCount=1,revision=1,categoryId="cat-1"))
        db.movementDao().insertLedgerEntries(listOf(LedgerEntryEntity("ledger",owner,purchase,"liability","LIABILITY",-10000,"PEN",1)))
        db.creditDao().insertInstallments(listOf(CreditInstallmentEntity("installment",owner,purchase,1,1,10000,0,"PENDING",1,1,1)))
    }
    private suspend fun void(id: String): MovementMutationResult {
        val head = requireNotNull(local.getRevisionHead(owner,id))
        val cmd = MovementRevisionCommand.Void(UUID.randomUUID().toString(),id,head.commandBaseRevision)
        return local.commitVoidAtomic(owner,cmd,MovementRevisionRequestHasher().computeHash(cmd))
    }
    @Test fun unpaidPurchaseVoidIsAtomicAndIdempotent() = runTest {
        insertPurchase()
        assertTrue(void(purchase) is MovementMutationResult.Success)
        assertEquals("VOIDED",db.creditDao().getInstallmentsForTransaction(owner,purchase).single().status)
        assertEquals(0L,db.creditDao().getOutstandingPrincipalForCard(owner,"card"))
        assertEquals(0L,db.movementDao().getBalanceProjection(owner,"liability")?.balanceMinor)
        assertTrue(void(purchase) is MovementMutationResult.Success)
        assertEquals(0L,db.movementDao().calculateLedgerSumForAccount(owner,"liability"))
    }
    @Test fun appliedPaymentBlocksVoid() = runTest {
        insertPurchase()
        db.creditDao().insertAllocations(listOf(CreditPaymentAllocationEntity("allocation",owner,purchase,"installment",100,1)))
        assertEquals(MovementMutationResult.Rejected("CARD_PURCHASE_HAS_PAYMENTS"),void(purchase))
        assertEquals("CONFIRMED",db.movementDao().getTransactionById(owner,purchase)?.status)
    }
    @Test fun removingRefundRestoresPurchaseAndAllowsItsVoid() = runTest {
        insertPurchase()
        val refund = RegisterRefundTransaction(local)(RefundTransactionCommand(UUID.randomUUID().toString(),owner,purchase,
            UUID.randomUUID().toString(),4000,"PEN",1000)) as RefundTransactionResult.Success
        assertEquals(6000L,db.creditDao().getOutstandingPrincipalForCard(owner,"card"))
        assertEquals(MovementMutationResult.Rejected("PURCHASE_HAS_ACTIVE_REFUNDS"),void(purchase))
        assertTrue(void(refund.transaction.id) is MovementMutationResult.Success)
        assertEquals(10000L,db.creditDao().getOutstandingPrincipalForCard(owner,"card"))
        assertTrue(void(purchase) is MovementMutationResult.Success)
    }
    private fun feed(id:String, payload:String, revision:Long=1) {
        coEvery { api.pullChanges(any(),any()) } answers {
            val cursor=firstArg<PullChangesRequestDto>().afterSequence
            FinancialApiResponse.Success(PullChangesResponseDto(if(cursor<2) listOf(SyncChangeItemDto(2,"TRANSACTION",id,revision,"UPSERT",Json.parseToJsonElement(payload))) else emptyList(),2))
        }
    }
    private fun hydration() = MovementHydration(db.movementDao(),api,db.accountDao(),db.cardDao(),db,session)
    @Test fun canonicalRefundPullSucceedsOnOriginAndFreshDevice() = runTest {
        for(origin in listOf(true,false)) {
            val id=UUID.randomUUID().toString()
            if(origin) db.movementDao().insertTransaction(TransactionEntity(id=id,userId=owner,type="INCOME",amountMinor=4000,currencyCode="PEN",
                occurredAt=1,status="CONFIRMED",syncStatus="SYNCED",cardId="card",operationKind="REFUND",revision=1))
            db.movementDao().saveSyncCheckpoint(MovementSyncCheckpointEntity(owner,0))
            feed(id,"""{"id":"$id","type":"INCOME","operation_kind":"REFUND","amount_minor":4000,"currency_code":"PEN","source_account_id":"liability","occurred_at":"2026-10-01T12:00:00Z","status":"CONFIRMED","ledger_entries":[{"account_id":"liability","role":"LIABILITY","signed_amount_minor":4000,"currency_code":"PEN"}]}""")
            assertTrue(hydration().hydrateFully(owner))
            assertEquals(2L,db.movementDao().getSyncCheckpoint(owner)?.sequence)
        }
    }
    @Test fun voidedPurchasePullAcceptsZeroNetLedgerAndVoidsInstallment() = runTest {
        insertPurchase()
        feed(purchase,"""{"id":"$purchase","type":"EXPENSE","operation_kind":"CARD_PURCHASE","card_id":"card","category_id":"food","installment_count":1,"amount_minor":10000,"currency_code":"PEN","occurred_at":"2026-10-01T12:00:00Z","status":"VOIDED","ledger_entries":[{"account_id":"liability","role":"LIABILITY","signed_amount_minor":-10000,"currency_code":"PEN"},{"account_id":"liability","role":"REVERSAL","signed_amount_minor":10000,"currency_code":"PEN"}],"installments":[{"id":"remote-installment","installment_number":1,"due_date":"2026-11-01","principal_minor":10000,"interest_minor":0}]}""",2)
        assertTrue(hydration().hydrateFully(owner))
        assertEquals(2L,db.movementDao().getSyncCheckpoint(owner)?.sequence)
        assertEquals("VOIDED",db.creditDao().getInstallmentsForTransaction(owner,purchase).single().status)
        assertEquals(0L,db.movementDao().calculateLedgerSumForAccount(owner,"liability"))
    }
    @Test fun hydratedProposalsCanBeDiscardedAndRedone() = runTest {
        for (discard in listOf(true,false)) {
            val id=UUID.randomUUID().toString()
            db.movementDao().insertTransaction(TransactionEntity(id=id,userId=owner,type="INCOME",amountMinor=100,currencyCode="PEN",
                sourceAccountId="liability",occurredAt=1,status="CONFIRMED",syncStatus="SYNCED",revision=1))
            db.movementDao().insertLedgerEntries(listOf(LedgerEntryEntity(UUID.randomUUID().toString(),owner,id,"liability","DESTINATION",100,"PEN",1)))
            val payload=requireNotNull(local.getRevisionHead(owner,id)).payload.copy(amountMinor=200)
            val cmd=MovementRevisionCommand.Revise(UUID.randomUUID().toString(),id,1,payload=payload)
            assertTrue(local.commitRevisionAtomic(owner,cmd,MovementRevisionRequestHasher().computeHash(cmd)) is MovementMutationResult.Success)
            val pending=db.movementDao().getPendingOutboxForTransaction(owner,id).single()
            db.movementDao().saveSyncCheckpoint(MovementSyncCheckpointEntity(owner,0))
            feed(id,"""{"id":"$id","type":"INCOME","operation_kind":"STANDARD","amount_minor":300,"currency_code":"PEN","source_account_id":"liability","occurred_at":"2026-10-01T12:00:00Z","status":"REVISED","ledger_entries":[{"account_id":"liability","role":"PRIMARY","signed_amount_minor":300,"currency_code":"PEN"}]}""",3)
            assertTrue(hydration().hydrateFully(owner))
            val proposal=db.movementDao().getConflictProposals(owner,id).single()
            assertEquals(pending.idempotencyKey,proposal.commandId)
            assertEquals(pending.payload,proposal.proposedSnapshot)
            if(discard) {
                assertTrue(local.discardProposal(owner,proposal.proposalId))
                assertTrue(db.movementDao().getPendingOutboxForTransaction(owner,id).isEmpty())
            } else assertTrue(local.redoProposal(owner,proposal.proposalId,UUID.randomUUID().toString()) is MovementMutationResult.Success)
        }
    }

}
