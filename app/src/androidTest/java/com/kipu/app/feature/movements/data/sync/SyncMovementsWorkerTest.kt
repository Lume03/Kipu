package com.kipu.app.feature.movements.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.movements.data.remote.MovementApi
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.data.remote.MovementApiResponse
import com.kipu.app.feature.movements.data.remote.RegisterTransactionResponseDto
import com.kipu.app.feature.movements.domain.model.MovementMutationResult
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand
import com.kipu.app.feature.movements.domain.model.RegisterTransactionResult
import org.junit.Assert.assertFalse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncMovementsWorkerTest {
    private val userId = UUID.randomUUID().toString()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: KipuDatabase
    private lateinit var movementDao: MovementDao
    private lateinit var accountDao: AccountDao
    private val movementApi: MovementApi = mockk(relaxed = true)
    private val financialApi: FinancialInstrumentsApi = mockk()
    private val session: SessionCoordinator = mockk(relaxed = true)
    private val params: WorkerParameters = mockk(relaxed = true)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        movementDao = database.movementDao()
        accountDao = database.accountDao()
        every { params.inputData } returns workDataOf(SyncMovementsWorker.KEY_USER_ID to userId)
        every { session.currentOwner } returns LocalOwner(userId)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun debtServerEchoWithDifferentEntryIdsDoesNotDuplicateOfflineMoney() = runTest {
        val accountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        accountDao.insert(AccountEntity(id = accountId, userId = userId, creationOperationId = UUID.randomUUID().toString(),
            alias = "QA", type = "CASH", currency = "PEN", presetId = null, color = null, icon = null,
            initialBalanceMinorUnits = 0, openedAt = 1, createdAt = 1, updatedAt = 1))
        movementDao.insertTransaction(TransactionEntity(id = transactionId, userId = userId, type = "EXPENSE", amountMinor = 100,
            currencyCode = "PEN", sourceAccountId = accountId, occurredAt = 1, status = "ACTIVE", syncStatus = "PENDING",
            operationKind = "DEBT_PAYMENT", createdAt = 1, updatedAt = 1))
        movementDao.insertLedgerEntries(listOf(LedgerEntryEntity(id = "offline-entry", userId = userId, transactionId = transactionId,
            accountId = accountId, role = "PRIMARY", signedAmountMinor = -100, currencyCode = "PEN", createdAt = 1)))
        fun page(sequence: Long, revision: Long, status: String = "ACTIVE", amount: Long = 100) = PullChangesResponseDto(listOf(
            SyncChangeItemDto(sequence, "TRANSACTION", transactionId, revision, "UPSERT", Json.parseToJsonElement(
                """{"id":"$transactionId","type":"EXPENSE","operation_kind":"DEBT_PAYMENT","amount_minor":$amount,"currency_code":"PEN","source_account_id":"$accountId","occurred_at":"2026-10-25T17:00:00Z","status":"$status","ledger_entries":${if (status == "VOIDED") "[]" else """[{"id":"server-entry","account_id":"$accountId","role":"PRIMARY","signed_amount_minor":${-amount},"currency_code":"PEN"}]"""}}"""
            )),
        ), sequence, false)
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(page(1,1))
        assertTrue(worker().doWork().javaClass.simpleName.contains("Success"))
        assertEquals(-100L, movementDao.calculateLedgerSumForAccount(userId, accountId))
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(page(2,2,amount = 80))
        assertTrue(worker().doWork().javaClass.simpleName.contains("Success"))
        assertEquals(-80L, movementDao.calculateLedgerSumForAccount(userId, accountId))
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(page(3,3,status = "VOIDED",amount = 80))
        assertTrue(worker().doWork().javaClass.simpleName.contains("Success"))
        assertEquals(0L, movementDao.calculateLedgerSumForAccount(userId, accountId))
        assertTrue(movementDao.getLedgerEntriesForTransaction(userId, transactionId).any { it.id == "offline-entry" && it.signedAmountMinor == -100L })
    }

    @Test
    fun pullsAccountThenTransactionAndBuildsOneLedgerProjection() = runTest {
        val accountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val entryId = UUID.randomUUID().toString()
        val accountChange = SyncChangeItemDto(
            sequence = 1, entityType = "ACCOUNT", entityId = accountId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$accountId","alias":"Efectivo","type":"CASH","currency":"PEN","initial_balance_minor_units":1000,"opened_at":"2026-09-23T10:00:00Z"}"""
            ),
        )
        val transactionChange = SyncChangeItemDto(
            sequence = 2, entityType = "TRANSACTION", entityId = transactionId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$transactionId","type":"EXPENSE","amount_minor":50,"currency_code":"PEN","source_account_id":"$accountId","category_id":null,"merchant_id":null,"merchant_provisional_text":"Bodega","occurred_at":"2026-09-23T11:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"$entryId","account_id":"$accountId","role":"SOURCE","signed_amount_minor":-50,"currency_code":"PEN","created_at":"2026-09-23T11:00:00Z"}]}"""
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returnsMany listOf(
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(accountChange, transactionChange), 2, true)),
            FinancialApiResponse.Success(PullChangesResponseDto(emptyList(), 2, false)),
        )
        val worker = worker()

        assertTrue(worker.doWork().javaClass.simpleName.contains("Success"))
        assertEquals("Efectivo", accountDao.getById(userId, accountId)?.alias)
        assertEquals("Bodega", movementDao.getTransactionById(userId, transactionId)?.merchantProvisionalText)
        assertEquals(950L, movementDao.calculateLedgerSumForAccount(userId, accountId))
        assertEquals(950L, movementDao.getBalanceProjection(userId, accountId)?.balanceMinor)
        assertEquals(2L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun pullsCardPurchaseMetadataAndInstallmentsWithLiabilityLedger() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()
        val installmentId = UUID.randomUUID().toString()
        val cardChange = SyncChangeItemDto(
            sequence = 1, entityType = "CARD", entityId = cardId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$cardId","account_id":"$liabilityAccountId","alias":"BCP Visa","type":"CREDIT","currency":"PEN","network":"VISA","issuer":"BCP","last_four_digits":"7548","credit_limit_minor_units":100000,"billing_day":15,"due_day":5,"is_archived":false}""",
            ),
        )
        val purchaseChange = SyncChangeItemDto(
            sequence = 2, entityType = "TRANSACTION", entityId = transactionId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$transactionId","type":"EXPENSE","operation_kind":"CARD_PURCHASE","card_id":"$cardId","installment_count":1,"amount_minor":12500,"currency_code":"PEN","category_id":"food","occurred_at":"2026-09-23T11:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"liability-$transactionId","account_id":"$liabilityAccountId","role":"LIABILITY","signed_amount_minor":-12500,"currency_code":"PEN"}],"installments":[{"id":"$installmentId","installment_number":1,"due_date":"2026-10-05","principal_minor":12500,"interest_minor":0}]}""",
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returnsMany listOf(
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(cardChange, purchaseChange), 2, true)),
            FinancialApiResponse.Success(PullChangesResponseDto(emptyList(), 2, false)),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Success"))
        assertEquals(cardId, database.cardDao().getById(userId, cardId)?.id)
        assertEquals("CREDIT_LIABILITY", accountDao.getById(userId, liabilityAccountId)?.type)
        val stored = movementDao.getTransactionById(userId, transactionId)
        assertEquals(cardId, stored?.cardId)
        assertEquals("CARD_PURCHASE", stored?.operationKind)
        assertEquals(1, stored?.installmentCount)
        assertEquals(12_500L, database.creditDao().getLedgerOutstandingPrincipalForCard(userId, cardId))
        assertEquals(-12_500L, movementDao.calculateLedgerSumForAccount(userId, liabilityAccountId))
        assertEquals("LIABILITY", movementDao.getLedgerEntriesForTransaction(userId, transactionId).single().role)
        assertEquals(2L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun freshDeviceAppliesArchivedCardSnapshotBeforeArchiveEvent() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val creationChange = SyncChangeItemDto(
            sequence = 1, entityType = "CARD", entityId = cardId, revision = 2,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$cardId","account_id":"$liabilityAccountId","alias":"BCP Visa","type":"CREDIT","currency":"PEN","network":"VISA","issuer":"BCP","last_four_digits":"7548","credit_limit_minor_units":100000,"billing_day":15,"due_day":5,"is_archived":true}""",
            ),
        )
        val archiveChange = SyncChangeItemDto(
            sequence = 2, entityType = "CARD", entityId = cardId, revision = 2,
            operation = "ARCHIVE", payload = creationChange.payload,
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOf(creationChange, archiveChange), 2, false),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Success"))
        assertEquals(true, database.cardDao().getById(userId, cardId)?.isArchived)
        assertEquals("CREDIT_LIABILITY", accountDao.getById(userId, liabilityAccountId)?.type)
        assertEquals(2L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun pullsCardPaymentAsOneCashEntryAndAppliesInstallmentAllocation() = runTest {
        val cardId = UUID.randomUUID().toString()
        val liabilityAccountId = UUID.randomUUID().toString()
        val accountId = UUID.randomUUID().toString()
        val purchaseId = UUID.randomUUID().toString()
        val installmentId = UUID.randomUUID().toString()
        val paymentId = UUID.randomUUID().toString()
        accountDao.insert(
            AccountEntity(
                id = accountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Cuenta principal",
                type = "SAVINGS",
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
        accountDao.insert(
            AccountEntity(
                id = liabilityAccountId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                alias = "Internal card liability",
                type = "CREDIT_LIABILITY",
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
        database.cardDao().insert(
            CardEntity(
                id = cardId,
                userId = userId,
                creationOperationId = UUID.randomUUID().toString(),
                accountId = liabilityAccountId,
                alias = "Visa principal",
                type = "CREDIT",
                currency = "PEN",
                network = "VISA",
                issuer = "Banco",
                lastFourDigits = "4242",
                creditLimitMinorUnits = 100_000L,
                billingDay = 10,
                dueDay = 20,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        movementDao.insertTransaction(
            TransactionEntity(
                id = purchaseId,
                userId = userId,
                type = "EXPENSE",
                amountMinor = 10_000L,
                currencyCode = "PEN",
                categoryId = "food",
                occurredAt = 1_000L,
                status = "ACTIVE",
                syncStatus = "SYNCED",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                cardId = cardId,
                operationKind = "CARD_PURCHASE",
                installmentCount = 1,
            ),
        )
        movementDao.insertLedgerEntries(
            listOf(
                LedgerEntryEntity(
                    id = "purchase-liability-$purchaseId",
                    userId = userId,
                    transactionId = purchaseId,
                    accountId = liabilityAccountId,
                    role = "LIABILITY",
                    signedAmountMinor = -10_000L,
                    currencyCode = "PEN",
                    createdAt = 1_000L,
                ),
            ),
        )
        database.creditDao().insertInstallment(
            CreditInstallmentEntity(
                id = installmentId,
                userId = userId,
                transactionId = purchaseId,
                installmentNumber = 1,
                dueDate = LocalDate.parse("2026-10-20").toEpochDay(),
                principalMinor = 10_000L,
                interestMinor = 0L,
                status = "PENDING",
                revision = 1L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        val paymentChange = SyncChangeItemDto(
            sequence = 1, entityType = "TRANSACTION", entityId = paymentId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$paymentId","type":"TRANSFER","operation_kind":"CARD_PAYMENT","card_id":"$cardId","amount_minor":4000,"currency_code":"PEN","source_account_id":"$accountId","occurred_at":"2026-09-23T12:00:00Z","status":"ACTIVE","ledger_entries":[{"id":"cash-$paymentId","account_id":"$accountId","role":"SOURCE","signed_amount_minor":-4000,"currency_code":"PEN"},{"id":"liability-$paymentId","account_id":"$liabilityAccountId","role":"LIABILITY","signed_amount_minor":4000,"currency_code":"PEN"}],"allocations":[{"installment_id":"$installmentId","amount_minor":4000}]}""",
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOf(paymentChange), 1, false),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Success"))
        assertEquals(2, movementDao.getLedgerEntriesForTransaction(userId, paymentId).size)
        assertEquals(-4_000L, movementDao.getLedgerEntriesForTransaction(userId, paymentId).single { it.role == "SOURCE" }.signedAmountMinor)
        assertEquals(4_000L, movementDao.getLedgerEntriesForTransaction(userId, paymentId).single { it.role == "LIABILITY" }.signedAmountMinor)
        assertEquals(-6_000L, movementDao.calculateLedgerSumForAccount(userId, liabilityAccountId))
        assertEquals(4_000L, database.creditDao().getAllocationsForPayment(userId, paymentId).single().allocatedMinor)
        assertEquals(6_000L, database.creditDao().getLedgerOutstandingPrincipalForCard(userId, cardId))
        assertEquals(1L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun unknownChangeRollsBackWholePageAndDoesNotAdvanceCursor() = runTest {
        val accountId = UUID.randomUUID().toString()
        val accountChange = SyncChangeItemDto(
            sequence = 1, entityType = "ACCOUNT", entityId = accountId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$accountId","alias":"Efectivo","type":"CASH","currency":"PEN","initial_balance_minor_units":0,"opened_at":"2026-09-23T10:00:00Z"}""",
            ),
        )
        val unsupported = SyncChangeItemDto(
            sequence = 2, entityType = "FUTURE_ENTITY", entityId = "entity", revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement("{}"),
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOf(accountChange, unsupported), 2, false),
        )

        val result = worker().doWork()

        assertTrue(result.javaClass.simpleName.contains("Retry"))
        assertEquals(null, accountDao.getById(userId, accountId))
        assertEquals(null, movementDao.getSyncCheckpoint(userId))
    }

    @Test
    fun incorrectPageEndRollsBackOpeningLedgerAndProjection() = runTest {
        assertRejectedPageLeavesNoOpeningEffects(nextSequence = 2L)
    }

    @Test
    fun invalidSnapshotAfterOpeningAccountRollsBackWholePage() = runTest {
        val malformed = SyncChangeItemDto(
            sequence = 2, entityType = "TRANSACTION", entityId = UUID.randomUUID().toString(),
            revision = 1, operation = "UPSERT", payload = Json.parseToJsonElement("{}"),
        )
        assertRejectedPageLeavesNoOpeningEffects(nextSequence = 2L, secondChange = malformed)
    }

    @Test
    fun nonIncreasingSequenceAfterOpeningAccountRollsBackWholePage() = runTest {
        val repeated = SyncChangeItemDto(
            sequence = 1, entityType = "ACCOUNT", entityId = UUID.randomUUID().toString(),
            revision = 1, operation = "UPSERT", payload = Json.parseToJsonElement("{}"),
        )
        assertRejectedPageLeavesNoOpeningEffects(nextSequence = 1L, secondChange = repeated)
    }

    /**
     * Reinstall restore: the shared feed has global gaps, foreign CATEGORY/BUDGET changes and
     * row-shaped ACCOUNT snapshots. All money data must be restored and the cursor advanced.
     */
    @Test
    fun restoreAfterReinstallAppliesSharedFeedWithGapsForeignTypesAndRowShapedAccounts() = runTest {
        val bankId = UUID.randomUUID().toString()
        val cashId = UUID.randomUUID().toString()
        val expenseId = UUID.randomUUID().toString()
        val changes = listOf(
            SyncChangeItemDto(
                sequence = 1, entityType = "CATEGORY", entityId = UUID.randomUUID().toString(), revision = 1,
                operation = "UPSERT", payload = Json.parseToJsonElement("""{"name":"Comida"}"""),
            ),
            SyncChangeItemDto(
                sequence = 23, entityType = "ACCOUNT", entityId = bankId, revision = 1, operation = "UPSERT",
                payload = Json.parseToJsonElement(
                    """{"id":"$bankId","name":"Cuenta BCP","user_id":"$userId","account_type":"SAVINGS","currency_code":"PEN",
                       "initial_balance_minor_units":100000,"opened_at":"2026-10-21T20:02:03.686+00:00","is_archived":false,
                       "preset_id":null,"linked_account_id":null,"creation_operation_id":"${UUID.randomUUID()}","revision":1}""",
                ),
            ),
            SyncChangeItemDto(
                sequence = 30, entityType = "BUDGET", entityId = UUID.randomUUID().toString(), revision = 1,
                operation = "UPSERT", payload = Json.parseToJsonElement("{}"),
            ),
            SyncChangeItemDto(31, "SAVINGS_GOAL", UUID.randomUUID().toString(), 1, "UPSERT", Json.parseToJsonElement("{}")),
            SyncChangeItemDto(32, "OBLIGATION", UUID.randomUUID().toString(), 1, "UPSERT", Json.parseToJsonElement("{}")),
            SyncChangeItemDto(
                sequence = 41, entityType = "ACCOUNT", entityId = cashId, revision = 1, operation = "UPSERT",
                payload = Json.parseToJsonElement(
                    """{"id":"$cashId","alias":"Efectivo","type":"CASH","currency":"PEN","initial_balance_minor_units":5000,
                       "opened_at":"2026-10-25T17:03:52.104+00:00"}""",
                ),
            ),
            SyncChangeItemDto(
                sequence = 64, entityType = "TRANSACTION", entityId = expenseId, revision = 1, operation = "UPSERT",
                payload = Json.parseToJsonElement(
                    """{"id":"$expenseId","type":"EXPENSE","status":"ACTIVE","amount_minor":150,"currency_code":"PEN",
                       "source_account_id":"$bankId","destination_account_id":null,"occurred_at":"2026-10-23T17:16:00+00:00",
                       "ledger_entries":[{"id":"${UUID.randomUUID()}","role":"PRIMARY","account_id":"$bankId",
                       "currency_code":"PEN","signed_amount_minor":-150,"created_at":"2026-10-24T01:10:51.521+00:00"}]}""",
                ),
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(changes, nextSequence = 64, hasMore = false),
        )

        assertTrue(worker().doWork().javaClass.simpleName.contains("Success"))
        assertEquals("Cuenta BCP", accountDao.getById(userId, bankId)?.alias)
        assertEquals("Efectivo", accountDao.getById(userId, cashId)?.alias)
        assertEquals(expenseId, movementDao.getTransactionById(userId, expenseId)?.id)
        assertEquals(99_850L, movementDao.getBalanceProjection(userId, bankId)?.balanceMinor)
        assertEquals(64L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    /** Server voids arrive as STANDARD + ledger_effects; the restored balance must undo the expense once. */
    @Test
    fun remoteVoidOfPulledExpenseRestoresBalanceOnceAndKeepsTheHistory() = runTest {
        val accountId = UUID.randomUUID().toString()
        val expenseId = UUID.randomUUID().toString()
        val account = SyncChangeItemDto(
            sequence = 238, entityType = "ACCOUNT", entityId = accountId, revision = 1, operation = "UPSERT",
            payload = Json.parseToJsonElement(
                """{"id":"$accountId","alias":"Yape","type":"CASH","currency":"PEN","initial_balance_minor_units":5000,
                   "opened_at":"2026-10-21T19:00:00+00:00"}""",
            ),
        )
        val expense = SyncChangeItemDto(
            sequence = 239, entityType = "TRANSACTION", entityId = expenseId, revision = 1, operation = "UPSERT",
            payload = Json.parseToJsonElement(
                """{"id":"$expenseId","type":"EXPENSE","status":"ACTIVE","amount_minor":1000,"currency_code":"PEN",
                   "source_account_id":"$accountId","occurred_at":"2026-10-21T19:25:55.361+00:00",
                   "ledger_entries":[{"id":"${UUID.randomUUID()}","role":"PRIMARY","account_id":"$accountId",
                   "currency_code":"PEN","signed_amount_minor":-1000,"created_at":"2026-10-21T19:25:56+00:00"}]}""",
            ),
        )
        fun void(sequence: Long) = SyncChangeItemDto(
            sequence = sequence, entityType = "TRANSACTION", entityId = expenseId, revision = 2, operation = "UPSERT",
            payload = Json.parseToJsonElement(
                """{"id":"$expenseId","type":"EXPENSE","status":"VOIDED","operation_kind":"STANDARD","amount_minor":1000,
                   "currency_code":"PEN","source_account_id":"$accountId","occurred_at":"2026-10-21T19:25:55.361+00:00",
                   "ledger_effects":[{"reverses":["${UUID.randomUUID()}","0"],"effect_ordinal":0,"ledger_entry_id":"${UUID.randomUUID()}"}]}""",
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returnsMany listOf(
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(account, expense, void(248)), 248, false)),
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(void(249)), 249, false)),
        )

        assertTrue(worker().doWork().javaClass.simpleName.contains("Success"))
        assertEquals("VOIDED", movementDao.getTransactionById(userId, expenseId)?.status)
        assertEquals(5_000L, movementDao.calculateLedgerSumForAccount(userId, accountId))

        assertTrue(worker().doWork().javaClass.simpleName.contains("Success"))
        assertEquals(5_000L, movementDao.calculateLedgerSumForAccount(userId, accountId))
        assertEquals(5_000L, movementDao.getBalanceProjection(userId, accountId)?.balanceMinor)
        assertEquals(249L, movementDao.getSyncCheckpoint(userId)?.sequence)
    }

    @Test
    fun replaysExpiredCommandAfterRemoteCommitWithoutRepeatingLocalIncome() = runTest {
        val accountId = UUID.randomUUID().toString()
        accountDao.insert(AccountEntity(
            id = accountId, userId = userId, creationOperationId = UUID.randomUUID().toString(),
            alias = "Ingreso", type = "CASH", currency = "PEN", presetId = null,
            color = null, icon = null, initialBalanceMinorUnits = 0L, openedAt = 1L,
            createdAt = 1L, updatedAt = 1L,
        ))
        val key = UUID.randomUUID().toString()
        val local = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
        val result = local.commitTransactionAtomic(RegisterTransactionCommand(
            idempotencyKey = key, userId = userId, type = MovementType.INCOME,
            amountMinor = 800L, currency = "PEN", sourceAccountId = accountId, occurredAt = 1_000L,
        ), "original-hash") as RegisterTransactionResult.Success
        val outbox = movementDao.findClaimableOutbox(userId, System.currentTimeMillis(), 10).single()
        // The remote receipt survived; the process died before recording its response locally.
        coEvery { movementApi.registerTransaction(any()) } returns MovementApiResponse.Success(
            RegisterTransactionResponseDto(status = "DUPLICATE", transactionId = result.transaction.id),
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(emptyList(), 0, false),
        )
        repeat(2) {
            movementDao.setOutboxLease(userId, outbox.id, "IN_FLIGHT", 1L)
            assertTrue(worker().doWork().javaClass.simpleName.contains("Success"))
            assertEquals("SYNCED", movementDao.getOutboxById(userId, outbox.id)?.state)
            assertEquals(outbox.payload, movementDao.getOutboxById(userId, outbox.id)?.payload)
            assertEquals(800L, movementDao.calculateLedgerSumForAccount(userId, accountId))
            assertEquals(1, movementDao.getLedgerEntriesForTransaction(userId, result.transaction.id).size)
        }
        coVerify(exactly = 2) { movementApi.registerTransaction(match {
            it.idempotencyKey == key && it.requestHash == "original-hash" &&
                it.transaction.id == result.transaction.id
        }) }
    }

    @Test
    fun cancellationLeavesClaimRecoverableWithoutFalseRetryOrPull() = runTest {
        val outbox = seedPendingIncome()
        coEvery { movementApi.registerTransaction(any()) } throws CancellationException("cancelled")
        var cancelled = false
        try { worker().doWork() } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        val stored = requireNotNull(movementDao.getOutboxById(userId, outbox.id))
        assertEquals("IN_FLIGHT", stored.state)
        assertEquals(0, stored.attemptCount)
        assertEquals(null, stored.lastErrorCode)
        assertEquals("PENDING", movementDao.getTransactionById(userId, outbox.aggregateId)?.syncStatus)
        coVerify(exactly = 0) { financialApi.pullChanges(any()) }
        assertEquals(outbox.payload, movementDao.claimPendingOutbox(userId, requireNotNull(stored.leaseUntil)).single().payload)
    }

    @Test
    fun failedSyncStatusWriteRollsBackOutboxConfirmation() = runTest {
        val outbox = seedPendingIncome()
        val claimed = movementDao.claimPendingOutbox(userId, System.currentTimeMillis()).single()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_sync_status BEFORE UPDATE OF sync_status ON transactions " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END",
        )
        var failed = false
        try {
            movementDao.completeClaimedOutbox(claimed, "SYNCED", null, null, "SYNCED")
        } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(claimed, movementDao.getOutboxById(userId, outbox.id))
        assertEquals("PENDING", movementDao.getTransactionById(userId, outbox.aggregateId)?.syncStatus)
    }

    @Test
    fun conflictResponseCreatesConflictProposalAndStopsRetry() = runTest {
        val accountId = UUID.randomUUID().toString()
        accountDao.insert(AccountEntity(
            id = accountId, userId = userId, creationOperationId = UUID.randomUUID().toString(),
            alias = "Gasto", type = "CASH", currency = "PEN", presetId = null,
            color = null, icon = null, initialBalanceMinorUnits = 0L, openedAt = 1L,
            createdAt = 1L, updatedAt = 1L,
        ))
        database.categoryDao().insertCategory(
            com.kipu.app.feature.categories.data.local.CategoryEntity(
                id = "cat-1", userId = null, parentId = null, origin = "SYSTEM",
                categoryType = "EXPENSE", createdAt = 1L, updatedAt = 1L,
            )
        )
        val local = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
        val regCmd = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(), userId = userId, type = MovementType.EXPENSE,
            amountMinor = 1000L, currency = "PEN", sourceAccountId = accountId, categoryId = "cat-1", occurredAt = 1_000L,
        )
        val regRes = local.commitTransactionAtomic(regCmd, "reg-hash") as RegisterTransactionResult.Success
        val txId = regRes.transaction.id

        val regOutbox = movementDao.claimPendingOutbox(userId, System.currentTimeMillis(), 10).single()
        movementDao.completeClaimedOutbox(regOutbox, "SYNCED", null, null, "SYNCED")

        movementDao.insertOrUpdateReceipt(requireNotNull(movementDao.getReceipt(userId, regCmd.idempotencyKey)).copy(status = "APPLIED"))

        val reviseCmd = com.kipu.app.feature.movements.domain.model.MovementRevisionCommand.Revise(
            idempotencyKey = UUID.randomUUID().toString(),
            transactionId = txId,
            expectedRevision = 1L,
            payload = com.kipu.app.feature.movements.domain.model.MovementRevisionPayload(
                type = MovementType.EXPENSE, operationKind = "STANDARD", amountMinor = 800L,
                currency = "PEN", sourceAccountId = accountId, categoryId = "cat-1", occurredAt = 1000L,
            ),
        )
        val revisionResult = local.commitRevisionAtomic(userId, reviseCmd, "revise-hash")
        assertTrue(revisionResult.toString(), revisionResult is com.kipu.app.feature.movements.domain.model.MovementMutationResult.Success)

        coEvery { movementApi.reviseOrVoidTransaction(any()) } returns MovementApiResponse.Success(
            com.kipu.app.feature.movements.data.remote.ReviseOrVoidTransactionResponseDto(
                status = "CONFLICT",
                currentRevision = 3L,
            )
        )

        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(PullChangesResponseDto(emptyList(), 0, false))
        val result = worker().doWork()
        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
        assertEquals(androidx.work.ListenableWorker.Result.success(), worker().doWork())

        val proposals = local.getConflictProposals(userId, txId)
        assertEquals(1, proposals.size)
        assertEquals("UNRESOLVED", proposals[0].resolution)
        assertEquals("3", proposals[0].remoteRevisionId)

        val discarded = local.discardProposal(userId, proposals[0].proposalId)
        assertTrue(discarded)
        val remaining = local.getConflictProposals(userId, txId)
        assertEquals(0, remaining.size)
    }

    @Test
    fun voidTransactionReplay100TimesProducesZeroDuplicateLedgerEntries() = runTest {
        val accountId = UUID.randomUUID().toString()
        accountDao.insert(AccountEntity(
            id = accountId, userId = userId, creationOperationId = UUID.randomUUID().toString(),
            alias = "Ahorros", type = "CASH", currency = "PEN", presetId = null,
            color = null, icon = null, initialBalanceMinorUnits = 0L, openedAt = 1L,
            createdAt = 1L, updatedAt = 1L,
        ))
        val local = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
        val regCmd = RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(),
            userId = userId,
            type = MovementType.EXPENSE,
            amountMinor = 2500L,
            currency = "PEN",
            sourceAccountId = accountId,
            occurredAt = 1_000L,
            categoryId = "cat-1",
        )
        database.categoryDao().insertCategory(
            com.kipu.app.feature.categories.data.local.CategoryEntity(id = "cat-1", userId = null, parentId = null,
                origin = "SYSTEM", categoryType = "EXPENSE", createdAt = 1, updatedAt = 1))
        val registered = local.commitTransactionAtomic(regCmd, "orig-hash") as RegisterTransactionResult.Success
        val tx = registered.transaction

        val voidCmd = com.kipu.app.feature.movements.domain.model.MovementRevisionCommand.Void(
            idempotencyKey = UUID.randomUUID().toString(),
            transactionId = tx.id,
            expectedRevision = 1L,
        )

        val firstResult = local.commitVoidAtomic(userId, voidCmd, "void-hash")
        assertTrue(firstResult is MovementMutationResult.Success)
        assertFalse((firstResult as MovementMutationResult.Success).isDuplicate)

        val initialEntriesCount = movementDao.getLedgerEntriesForTransaction(userId, tx.id).size
        assertEquals(2, initialEntriesCount)

        repeat(100) {
            val replayResult = local.commitVoidAtomic(userId, voidCmd, "void-hash")
            assertTrue(replayResult is MovementMutationResult.Success)
            assertTrue((replayResult as MovementMutationResult.Success).isDuplicate)
        }

        assertEquals(initialEntriesCount, movementDao.getLedgerEntriesForTransaction(userId, tx.id).size)

        val editCmd = com.kipu.app.feature.movements.domain.model.MovementRevisionCommand.Revise(
            idempotencyKey = UUID.randomUUID().toString(),
            transactionId = tx.id,
            expectedRevision = 2L,
            payload = com.kipu.app.feature.movements.domain.model.MovementRevisionPayload(
                type = MovementType.EXPENSE,
                operationKind = "STANDARD",
                amountMinor = 1000L,
                currency = "PEN",
                sourceAccountId = accountId,
                categoryId = "cat-1",
                occurredAt = 1000L,
            )
        )
        val editResult = local.commitRevisionAtomic(userId, editCmd, "edit-after-void-hash")
        assertTrue(editResult is MovementMutationResult.Conflict || editResult is MovementMutationResult.Rejected)
    }

    private suspend fun seedPendingIncome(): com.kipu.app.feature.movements.data.local.MovementOutboxEntity {
        val accountId = UUID.randomUUID().toString()
        accountDao.insert(AccountEntity(
            id = accountId, userId = userId, creationOperationId = UUID.randomUUID().toString(),
            alias = "Ingreso", type = "CASH", currency = "PEN", presetId = null,
            color = null, icon = null, initialBalanceMinorUnits = 0L, openedAt = 1L,
            createdAt = 1L, updatedAt = 1L,
        ))
        val local = MovementLocalDataSource(database, movementDao, BalanceProjectionStore(movementDao))
        local.commitTransactionAtomic(RegisterTransactionCommand(
            idempotencyKey = UUID.randomUUID().toString(), userId = userId, type = MovementType.INCOME,
            amountMinor = 800L, currency = "PEN", sourceAccountId = accountId, occurredAt = 1_000L,
        ), "original-hash")
        return movementDao.findClaimableOutbox(userId, System.currentTimeMillis(), 10).single()
    }

    private suspend fun assertRejectedPageLeavesNoOpeningEffects(
        nextSequence: Long,
        secondChange: SyncChangeItemDto? = null,
    ) {
        val accountId = UUID.randomUUID().toString()
        val opening = SyncChangeItemDto(
            sequence = 1, entityType = "ACCOUNT", entityId = accountId, revision = 1,
            operation = "UPSERT", payload = Json.parseToJsonElement(
                """{"id":"$accountId","alias":"Efectivo","type":"CASH","currency":"PEN","initial_balance_minor_units":500,"opened_at":"2026-09-23T10:00:00Z"}""",
            ),
        )
        coEvery { financialApi.pullChanges(any()) } returns FinancialApiResponse.Success(
            PullChangesResponseDto(listOfNotNull(opening, secondChange), nextSequence, false),
        )

        assertTrue(worker().doWork().javaClass.simpleName.contains("Retry"))
        assertEquals(null, accountDao.getById(userId, accountId))
        assertEquals(null, movementDao.calculateLedgerSumForAccount(userId, accountId))
        assertEquals(null, movementDao.getBalanceProjection(userId, accountId))
        assertEquals(null, movementDao.getSyncCheckpoint(userId))
    }

    private fun worker() = SyncMovementsWorker(
        appContext = context,
        workerParams = params,
        movementDao = movementDao,
        api = movementApi,
        financialApi = financialApi,
        accountDao = accountDao,
        cardDao = database.cardDao(),
        database = database,
        sessionCoordinator = session,
    )
}
