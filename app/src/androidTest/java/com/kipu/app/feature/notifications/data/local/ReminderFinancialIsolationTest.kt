package com.kipu.app.feature.notifications.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.LocalOwner
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.capture.domain.ExpectedPaymentStore
import com.kipu.app.feature.debts.data.sync.DebtNotificationPermissionChecker
import com.kipu.app.feature.debts.data.sync.DebtReminderWorker
import com.kipu.app.feature.notifications.data.FinancialAlertSystemNotifier
import com.kipu.app.feature.notifications.data.OfflineFirstNotificationsRepository
import com.kipu.app.feature.notifications.data.RegisteredNotificationDestinationResolver
import com.kipu.app.feature.recurrences.data.local.RecurrenceEntity
import com.kipu.app.feature.recurrences.data.sync.RecurrenceReminderWorker
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReminderFinancialIsolationTest {
    @Test
    fun receivingReadingAndResolvingReminderPreservesPaymentsMovementsAndBalances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        val owner = "reminder-finance-owner"
        val recurrenceId = "recurrence"
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("""INSERT INTO accounts (id,user_id,creation_operation_id,alias,type,currency,
                initial_balance_minor_units,opened_at,is_archived,remote_revision,created_at,updated_at)
                VALUES ('account','$owner','opening','Ahorros','SAVINGS','PEN',0,1,0,1,1,1)""")
            sql.execSQL("""INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,source_account_id,
                operation_kind,occurred_at,status,sync_status,created_at,updated_at)
                VALUES ('expense','$owner','EXPENSE',1200,'PEN','account','STANDARD',1,'CONFIRMED','SYNCED',1,1)""")
            sql.execSQL("""INSERT INTO ledger_entries (id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at)
                VALUES ('entry','$owner','expense','account','SOURCE',-1200,'PEN',1)""")
            database.recurrenceDao().upsert(RecurrenceEntity(recurrenceId, owner, """{"amount_minor":1200,"currency_code":"PEN"}""",
                "FIXED", "MONTHLY", 1, null, "2026-12-10", null, "2026-12-10", 2,
                "ACTIVE", true, 1, syncState = "SYNCED", createdAt = 1, updatedAt = 1))
            database.expectedPaymentDao().insertIfAbsent(com.kipu.app.feature.recurrences.data.local.ExpectedPaymentEntity(
                "expected", owner, recurrenceId, "2026-12-10", 1200, "PEN", "EXPECTED", null, null, 1, 1))
            database.debtDao().insertDebt(com.kipu.app.feature.debts.data.local.DebtEntity(
                "debt", owner, "PAYABLE", "Proveedor", 10_000, "PEN", "2026-11-01", reminderLeadDays = 2, syncState = "SYNCED"))
            database.debtDao().insertInstallments(listOf(com.kipu.app.feature.debts.data.local.DebtInstallmentEntity(
                "installment", owner, "debt", 1, "2026-12-10", 10_000, "PARTIAL")))
            database.debtDao().insertEvent(com.kipu.app.feature.debts.data.local.DebtEventEntity(
                id = "existing-payment", userId = owner, debtId = "debt", transactionId = "expense",
                installmentId = "installment", eventType = "PAYMENT", amountMinor = 1200,
                principalDeltaMinor = -1200, occurredAt = 1, createdAt = 1))
            fun snapshot(): List<List<List<String?>>> = listOf("transactions", "ledger_entries", "accounts", "recurrence_expected_payments",
                "debts", "debt_installments", "debt_events").map { table ->
                sql.query("SELECT * FROM $table ORDER BY id").use { cursor ->
                    buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
                }
            }
            val before = snapshot()
            val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(owner) }
            val payments = com.kipu.app.feature.capture.data.local.RoomExpectedPaymentStore(database.expectedPaymentDao())
            val permission = object : DebtNotificationPermissionChecker { override fun canPostDebtReminder() = false }
            val factory = object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    if (workerClassName == DebtReminderWorker::class.java.name) DebtReminderWorker(appContext, workerParameters,
                        database.debtDao(), session, permission, database.reminderScheduleDao(), database.localReminderEventDao(), ReminderReconciliationCoordinator())
                    else RecurrenceReminderWorker(appContext, workerParameters, database.recurrenceDao(), session, permission,
                        database.reminderScheduleDao(), database.localReminderEventDao(), ReminderReconciliationCoordinator(), payments)
            }
            val repository = OfflineFirstNotificationsRepository(database.appNotificationDao(), database.localReminderEventDao(),
                database.notificationSyncOutboxDao(), mockk(), object : DatabaseTransactionRunner {
                    override suspend fun <R> invoke(block: suspend () -> R): R = database.withTransaction { block() }
                }, mockk(relaxed = true), Clock.systemUTC(), FinancialAlertSystemNotifier.None)
            repeat(2) {
                val worker = TestListenableWorkerBuilder<RecurrenceReminderWorker>(context).setWorkerFactory(factory)
                    .setInputData(workDataOf(RecurrenceReminderWorker.KEY_USER_ID to owner,
                        RecurrenceReminderWorker.KEY_RECURRENCE_ID to recurrenceId,
                        RecurrenceReminderWorker.KEY_DUE_DATE to "2026-12-10", RecurrenceReminderWorker.KEY_LEAD_DAYS to 2)).build()
                assertTrue(worker.doWork() is ListenableWorker.Result.Success)
                val debtWorker = TestListenableWorkerBuilder<DebtReminderWorker>(context).setWorkerFactory(factory)
                    .setInputData(workDataOf(DebtReminderWorker.KEY_USER_ID to owner, DebtReminderWorker.KEY_DEBT_ID to "debt",
                        DebtReminderWorker.KEY_INSTALLMENT_ID to "installment", DebtReminderWorker.KEY_DUE_DATE to "2026-12-10",
                        DebtReminderWorker.KEY_LEAD_DAYS to 2)).build()
                assertTrue(debtWorker.doWork() is ListenableWorker.Result.Success)
            }
            assertEquals(before, snapshot())
            val notices = repository.observeActive(owner).first()
            assertEquals(2, notices.size)
            val resolver = RegisteredNotificationDestinationResolver(database.cardDao(), database.budgetDao(), mockk(), database.debtDao(), database.goalDao())
            notices.forEach { notice ->
                resolver.resolve(owner, notice)
                assertTrue(repository.markRead(owner, notice.id).isSuccess)
            }
            assertTrue(repository.observeActive(owner).first().all { it.isRead })
            assertEquals(before, snapshot())
            assertEquals(-1200L, database.movementDao().calculateLedgerSumForAccount(owner, "account"))
            assertEquals("EXPECTED", database.expectedPaymentDao().get(owner, "expected")!!.status)
            assertEquals(8_800L, database.debtDao().getInstallmentRemaining(owner, "debt", "installment"))
        } finally { database.close() }
    }
}
