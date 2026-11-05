package com.kipu.app.feature.debts.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.data.sync.DebtSyncScheduler
import com.kipu.app.feature.debts.domain.DebtCommandHasher
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.debts.domain.model.SettleDebtCommand
import com.kipu.app.feature.movements.data.local.BalanceProjectionStore
import com.kipu.app.feature.movements.data.local.MovementLocalDataSource
import com.kipu.app.feature.movements.domain.model.MovementMutationResult
import com.kipu.app.feature.movements.domain.model.MovementRevisionCommand
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtSettlementSyncTest {
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstDebtRepository
    private lateinit var scheduler: RecordingDebtScheduler

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KipuDatabase::class.java,
        ).allowMainThreadQueries().build()
        val localDataSource = DebtLocalDataSource(
            database = database,
            debtDao = database.debtDao(),
            debtOutboxDao = database.debtOutboxDao(),
            movementDao = database.movementDao(),
            accountDao = database.accountDao(),
            financialMovementDao = database.financialMovementDao(),
            categoryDao = database.categoryDao(),
        )
        scheduler = RecordingDebtScheduler()
        repository = OfflineFirstDebtRepository(database.debtDao(), localDataSource, scheduler)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun retryCreatesOnePrincipalEventAndAtMostOneInterestMovement() = runBlocking {
        database.accountDao().insert(account())
        database.categoryDao().insertCategory(
            CategoryEntity(
                id = CATEGORY_ID,
                userId = OWNER_ID,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                createdAt = 1L,
                updatedAt = 1L,
                categoryType = "EXPENSE",
            ),
        )
        assertTrue(repository.openDebt(OWNER_ID, openCommand(), hasPremiumAccess = true) is DebtCommandResult.Applied)
        scheduler.requestedOwners.clear()

        val command = SettleDebtCommand(
            identity = DebtCommandIdentity(OPERATION_ID, DebtCommandHasher.sha256("settle-once:$OPERATION_ID")),
            debtId = DEBT_ID,
            expectedRevision = 1L,
            accountId = ACCOUNT_ID,
            principalMinor = 2_000L,
            interestMinor = 100L,
            interestCategoryId = CATEGORY_ID,
            occurredAt = 1_791_000_000_000L,
        )

        assertTrue(repository.settleDebt(OWNER_ID, command) is DebtCommandResult.Applied)
        assertTrue(repository.settleDebt(OWNER_ID, command) is DebtCommandResult.Duplicate)
        val historyRow = database.debtDao().observeSettlementActivities(OWNER_ID, DEBT_ID).first().single()
        assertEquals(OPERATION_ID, historyRow.transactionId)
        assertEquals(2_000L, historyRow.principalMinor)
        assertEquals(100L, historyRow.interestMinor)
        val databaseSql = database.openHelper.writableDatabase
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM debt_events WHERE event_type='PAYMENT' AND debt_id='$DEBT_ID'"))
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM transactions WHERE id='$OPERATION_ID' AND operation_kind='DEBT_PAYMENT'"))
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM transactions WHERE operation_kind='DEBT_AMORTIZATION'"))
        assertEquals(2L, databaseSql.scalarLong("SELECT COUNT(*) FROM financial_movements WHERE operation_id='$OPERATION_ID'"))
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM debt_command_outbox WHERE command_type='SETTLE_DEBT'"))
        assertEquals(1L, databaseSql.scalarLong("SELECT COUNT(*) FROM local_command_receipts WHERE command_type='SETTLE_DEBT'"))
        assertEquals(-2_100L, databaseSql.scalarLong("SELECT SUM(signed_amount_minor) FROM ledger_entries WHERE user_id='$OWNER_ID' AND account_id='$ACCOUNT_ID'"))
        // A duplicate receipt still wakes the unique per-user sync worker so a
        // retry can recover if the original enqueue was interrupted.
        assertEquals(listOf(OWNER_ID, OWNER_ID), scheduler.requestedOwners)
        assertEquals(8_000L, repository.observeDebt(OWNER_ID, DEBT_ID).first()?.remainingPrincipalMinor)
        val activity = database.debtDao().observeSettlementActivities(OWNER_ID, DEBT_ID).first().single()
        assertEquals(2_000L, activity.principalMinor)
        assertEquals(100L, activity.interestMinor)
        assertEquals(false, activity.isVoided)
    }

    @Test
    fun voidingInterestReversesPrincipalAndInterestTogether() = runBlocking { assertSettlementVoid(selectInterest = true) }

    @Test
    fun voidingPrincipalReversesPrincipalAndInterestTogether() = runBlocking { assertSettlementVoid(selectInterest = false) }

    private suspend fun assertSettlementVoid(selectInterest: Boolean) {
        database.accountDao().insert(account())
        database.categoryDao().insertCategory(
            CategoryEntity(
                id = CATEGORY_ID,
                userId = OWNER_ID,
                parentId = null,
                origin = "CUSTOM",
                isActive = true,
                createdAt = 1L,
                updatedAt = 1L,
                categoryType = "EXPENSE",
            ),
        )
        assertTrue(repository.openDebt(OWNER_ID, openCommand(), hasPremiumAccess = true) is DebtCommandResult.Applied)
        val settlement = SettleDebtCommand(
            identity = DebtCommandIdentity(OPERATION_ID, DebtCommandHasher.sha256("settle-group:$OPERATION_ID")),
            debtId = DEBT_ID,
            expectedRevision = 1L,
            accountId = ACCOUNT_ID,
            principalMinor = 2_000L,
            interestMinor = 100L,
            interestCategoryId = CATEGORY_ID,
            occurredAt = 1_791_000_000_000L,
        )
        assertTrue(repository.settleDebt(OWNER_ID, settlement) is DebtCommandResult.Applied)
        val movementDataSource = MovementLocalDataSource(
            database,
            database.movementDao(),
            BalanceProjectionStore(database.movementDao()),
        )
        val interestTransactionId = database.openHelper.writableDatabase.scalarString(
            "SELECT id FROM transactions WHERE operation_kind='DEBT_AMORTIZATION' LIMIT 1",
        )
        val voidCommand = MovementRevisionCommand.Void(
            idempotencyKey = "87000000-0000-4000-8000-000000000099",
            transactionId = if (selectInterest) interestTransactionId else OPERATION_ID,
            expectedRevision = 1L,
            reason = "correction",
        )

        assertTrue(
            movementDataSource.commitVoidAtomic(OWNER_ID, voidCommand, "void-group-hash") is MovementMutationResult.Success,
        )

        assertEquals("VOIDED", database.movementDao().getTransactionById(OWNER_ID, OPERATION_ID)?.status)
        assertEquals("VOIDED", database.movementDao().getTransactionById(OWNER_ID, interestTransactionId)?.status)
        val activity = database.debtDao().observeSettlementActivities(OWNER_ID, DEBT_ID).first().single()
        assertEquals(true, activity.isVoided)
        assertEquals(0L, database.financialMovementDao().getAccountBalance(OWNER_ID, ACCOUNT_ID))
        assertEquals(10_000L, repository.observeDebt(OWNER_ID, DEBT_ID).first()?.remainingPrincipalMinor)
        assertEquals(4L, database.openHelper.readableDatabase.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
        val replay = movementDataSource.commitVoidAtomic(OWNER_ID, voidCommand, "void-group-hash") as MovementMutationResult.Success
        assertTrue(replay.isDuplicate)
        assertEquals(4L, database.openHelper.readableDatabase.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
        assertEquals(1L, database.openHelper.readableDatabase.scalarLong("SELECT COUNT(*) FROM movement_outbox WHERE command_type='VOID_TRANSACTION'"))
    }

    @Test
    fun voidPrincipalReopensSettledDebtRecalculatesInstallmentAndReplayAddsNothing() = runBlocking {
        database.accountDao().insert(account())
        assertTrue(repository.openDebt(OWNER_ID, openCommand(), true) is DebtCommandResult.Applied)
        val installmentId = "87000000-0000-4000-8000-000000000088"
        database.debtDao().insertInstallments(listOf(com.kipu.app.feature.debts.data.local.DebtInstallmentEntity(
            id = installmentId, userId = OWNER_ID, debtId = DEBT_ID, installmentNumber = 1,
            dueDate = "2026-11-10", amountMinor = 10000L, status = "PENDING")))
        assertTrue(repository.settleDebt(OWNER_ID, SettleDebtCommand(
            identity = DebtCommandIdentity(OPERATION_ID, DebtCommandHasher.sha256("full-payment")), debtId = DEBT_ID,
            expectedRevision = 1L, accountId = ACCOUNT_ID, principalMinor = 10000L,
            installmentId = installmentId, occurredAt = 1791000000000L)) is DebtCommandResult.Applied)
        assertEquals("SETTLED", database.debtDao().getDebt(OWNER_ID, DEBT_ID)?.status)
        assertEquals("PAID", database.debtDao().getInstallment(OWNER_ID, DEBT_ID, installmentId)?.status)
        val local = MovementLocalDataSource(database, database.movementDao(), BalanceProjectionStore(database.movementDao()))
        val command = MovementRevisionCommand.Void("87000000-0000-4000-8000-000000000089", OPERATION_ID, 1L)
        assertTrue(local.commitVoidAtomic(OWNER_ID, command, "void-full") is MovementMutationResult.Success)
        assertEquals("ACTIVE", database.debtDao().getDebt(OWNER_ID, DEBT_ID)?.status)
        assertEquals(10000L, repository.observeDebt(OWNER_ID, DEBT_ID).first()?.remainingPrincipalMinor)
        assertEquals("PENDING", database.debtDao().getInstallment(OWNER_ID, DEBT_ID, installmentId)?.status)
        assertEquals(10000L, database.debtDao().getInstallmentRemaining(OWNER_ID, DEBT_ID, installmentId))
        assertEquals(0L, database.movementDao().getBalanceProjection(OWNER_ID, ACCOUNT_ID)?.balanceMinor)
        val before = database.openHelper.readableDatabase.scalarLong("SELECT COUNT(*) FROM ledger_entries")
        val replay = local.commitVoidAtomic(OWNER_ID, command, "void-full") as MovementMutationResult.Success
        assertTrue(replay.isDuplicate)
        assertEquals(before, database.openHelper.readableDatabase.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
        assertEquals(1L, database.openHelper.readableDatabase.scalarLong("SELECT COUNT(*) FROM movement_outbox WHERE command_type='VOID_TRANSACTION'"))
    }

    @Test
    fun alreadyVoidedInterestRejectsPrincipalVoidBeforeAnyPartialWrite() = runBlocking {
        database.accountDao().insert(account())
        database.categoryDao().insertCategory(CategoryEntity(CATEGORY_ID, OWNER_ID, null, "CUSTOM",
            categoryType = "EXPENSE", createdAt = 1L, updatedAt = 1L))
        assertTrue(repository.openDebt(OWNER_ID, openCommand(), true) is DebtCommandResult.Applied)
        assertTrue(repository.settleDebt(OWNER_ID, SettleDebtCommand(
            identity = DebtCommandIdentity(OPERATION_ID, DebtCommandHasher.sha256("partial-group")), debtId = DEBT_ID, expectedRevision = 1L,
            accountId = ACCOUNT_ID, principalMinor = 2000L, interestMinor = 100L, interestCategoryId = CATEGORY_ID,
            occurredAt = 1791000000000L)) is DebtCommandResult.Applied)
        val sql = database.openHelper.writableDatabase
        sql.execSQL("UPDATE transactions SET status='VOIDED' WHERE operation_kind='DEBT_AMORTIZATION'")
        val ledgerCount = sql.scalarLong("SELECT COUNT(*) FROM ledger_entries")
        val receipts = sql.scalarLong("SELECT COUNT(*) FROM local_command_receipts")
        val local = MovementLocalDataSource(database, database.movementDao(), BalanceProjectionStore(database.movementDao()))
        val result = local.commitVoidAtomic(OWNER_ID,
            MovementRevisionCommand.Void("87000000-0000-4000-8000-000000000090", OPERATION_ID, 1L), "invalid-group")
        assertEquals(MovementMutationResult.Rejected("SETTLEMENT_NOT_ACTIVE"), result)
        assertEquals("ACTIVE", database.movementDao().getTransactionById(OWNER_ID, OPERATION_ID)?.status)
        assertEquals(ledgerCount, sql.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
        assertEquals(receipts, sql.scalarLong("SELECT COUNT(*) FROM local_command_receipts"))
        assertEquals(0L, sql.scalarLong("SELECT COUNT(*) FROM transaction_revisions"))
        assertEquals(0L, sql.scalarLong("SELECT COUNT(*) FROM movement_outbox"))
        assertEquals(-2100L, database.movementDao().getBalanceProjection(OWNER_ID, ACCOUNT_ID)?.balanceMinor)
        assertEquals(8000L, repository.observeDebt(OWNER_ID, DEBT_ID).first()?.remainingPrincipalMinor)
    }

    @Test fun payablePrincipalReducesCashAndDebtWithoutOperatingExpense() = runBlocking {
        assertPrincipalAccounting(DebtObligationType.PAYABLE, 3000L, -3000L, 7000L)
    }

    @Test fun receivablePrincipalIncreasesCashAndReducesLoanWithoutOperatingIncome() = runBlocking {
        assertPrincipalAccounting(DebtObligationType.RECEIVABLE, 2000L, 2000L, 8000L)
    }

    private suspend fun assertPrincipalAccounting(type: DebtObligationType, amount: Long, cash: Long, remaining: Long) {
        database.accountDao().insert(account())
        assertTrue(repository.openDebt(OWNER_ID, openCommand().copy(obligationType = type), true) is DebtCommandResult.Applied)
        val command = SettleDebtCommand(DebtCommandIdentity(OPERATION_ID, DebtCommandHasher.sha256("principal-$type")), DEBT_ID, 1L,
            ACCOUNT_ID, amount, occurredAt = 1791000000000L)
        assertTrue(repository.settleDebt(OWNER_ID, command) is DebtCommandResult.Applied)
        assertEquals(remaining, repository.observeDebt(OWNER_ID, DEBT_ID).first()?.remainingPrincipalMinor)
        assertEquals(cash, database.financialMovementDao().getAccountBalance(OWNER_ID, ACCOUNT_ID))
        assertEquals(cash, database.movementDao().getBalanceProjection(OWNER_ID, ACCOUNT_ID)?.balanceMinor)
        val net = database.movementDao().queryHistoryNetFlows(com.kipu.app.feature.movements.data.local.MovementQuerySqlBuilder.buildNetFlow(
            OWNER_ID, com.kipu.app.feature.movements.domain.model.MovementHistoryQuery()))
        assertTrue("Principal must not enter operating net income or expense", net.isEmpty())
        assertEquals(1L, database.openHelper.readableDatabase.scalarLong("SELECT COUNT(*) FROM transactions"))
        assertEquals(1L, database.openHelper.readableDatabase.scalarLong("SELECT COUNT(*) FROM ledger_entries"))
    }

    private fun openCommand() = OpenDebtCommand(
        identity = DebtCommandIdentity(
            "87000000-0000-4000-8000-000000000010",
            DebtCommandHasher.sha256("open:$DEBT_ID"),
        ),
        debtId = DEBT_ID,
        obligationType = DebtObligationType.PAYABLE,
        counterpartyName = "Proveedor",
        principalMinor = 10_000L,
        currencyCode = "PEN",
        openedOn = LocalDate.parse("2026-10-08"),
        openingMode = DebtOpeningMode.HISTORICAL,
    )

    private fun account() = AccountEntity(
        id = ACCOUNT_ID,
        userId = OWNER_ID,
        creationOperationId = "87000000-0000-4000-8000-000000000011",
        alias = "Ahorros",
        type = "SAVINGS",
        currency = "PEN",
        presetId = null,
        color = null,
        icon = null,
        initialBalanceMinorUnits = 0L,
        openedAt = 1L,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private class RecordingDebtScheduler : DebtSyncScheduler {
        val requestedOwners = mutableListOf<String>()
        override fun schedule(userId: String) { requestedOwners += userId }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.scalarString(sql: String): String =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private companion object {
        const val OWNER_ID = "87000000-0000-4000-8000-000000000001"
        const val ACCOUNT_ID = "87000000-0000-4000-8000-000000000002"
        const val DEBT_ID = "87000000-0000-4000-8000-000000000003"
        const val CATEGORY_ID = "87000000-0000-4000-8000-000000000004"
        const val OPERATION_ID = "87000000-0000-4000-8000-000000000005"
    }
}
