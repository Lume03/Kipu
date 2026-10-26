package com.kipu.app.navigation

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.kipu.app.KipuApplication
import com.kipu.app.MainActivity
import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.PullChangesRequestDto
import com.kipu.app.feature.budgets.data.sync.SyncBudgetCommandsWorker
import com.kipu.app.feature.budgets.data.remote.BudgetApiResponse
import com.kipu.app.feature.budgets.data.remote.BudgetRemoteDto
import com.kipu.app.feature.budgets.data.remote.BudgetPeriodRemoteDto
import com.kipu.app.feature.debts.data.sync.DebtChangeApplyResult
import com.kipu.app.feature.debts.data.sync.DebtChangeFeedApplier
import com.kipu.app.feature.debts.data.sync.SyncDebtChangesWorker
import com.kipu.app.feature.debts.data.remote.DebtApiResponse
import com.kipu.app.feature.goals.data.sync.GoalChangeApplyResult
import com.kipu.app.feature.goals.data.sync.GoalChangeFeedApplier
import com.kipu.app.feature.goals.data.sync.SyncGoalsWorker
import com.kipu.app.feature.movements.data.sync.SyncMovementsWorker
import com.kipu.app.feature.movements.data.local.toDomain as movementToDomain
import com.kipu.app.qa.PlanningSyncVerificationAccess
import dagger.hilt.android.EntryPointAccessors
import java.security.MessageDigest
import java.time.Clock
import java.time.ZoneId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Debug-only, opt-in: uses the existing session and pending commands, never clears app data. */
@RunWith(AndroidJUnit4::class)
class PlanningRemoteAcceptanceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as KipuApplication

    @Test
    fun connectedAccountHistoryLoadsThroughThePresentationUseCase() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("planningRemoteAcceptance") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            val access = EntryPointAccessors.fromApplication(app, PlanningSyncVerificationAccess::class.java)
            val owner = withTimeout(30_000) {
                while (access.session().currentOwner?.verifiedUserId == null) delay(100)
                requireNotNull(access.session().currentOwner?.verifiedUserId)
            }
            val query = com.kipu.app.feature.movements.domain.model.MovementHistoryQuery()
            val page = access.historyQuery()(owner, query)
            val summary = access.historyQuery().getHistorySummary(owner, query)
            assertTrue("History should include the synced account's movements", page.items.isNotEmpty())
            assertTrue("History summary failed", summary != null && summary.totalCount >= page.items.size)
            assertTrue("History should include synced debt principal", page.items.any { it.transaction.operationKind == "DEBT_PAYMENT" })
        }
    }

    @Test
    fun demoDebtMetadataEditIsIdempotentAndHistoryDeletionIsDenied() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("planningRemoteAcceptance") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            val access = EntryPointAccessors.fromApplication(app, PlanningSyncVerificationAccess::class.java)
            val owner = withTimeout(30_000) {
                while (access.session().currentOwner?.verifiedUserId == null) delay(100)
                requireNotNull(access.session().currentOwner?.verifiedUserId)
            }
            liveWorker<SyncDebtChangesWorker>(owner).doWork()
            val original = access.database().debtDao().observeDebts(owner).first().firstOrNull {
                it.counterpartyName.contains("demo", ignoreCase = true)
            }
            assumeTrue("Requires an identifiable demo debt", original != null)
            val debt = requireNotNull(original)
            val api = access.debtApi()
            fun request(revision: Long, name: String) = signedDebt(buildJsonObject {
                put("contract_version",1); put("operation_id",UUID.randomUUID().toString()); put("debt_id",debt.id)
                put("expected_revision",revision); put("counterparty_name",name)
                put("due_date",debt.dueDate?.let(::JsonPrimitive) ?: JsonNull)
                put("notes",debt.notes?.let(::JsonPrimitive) ?: JsonNull)
                put("reminder_lead_days",debt.reminderLeadDays?.let(::JsonPrimitive) ?: JsonNull)
            })
            val edited = request(debt.revision, "QA Sync demo")
            val result = api.execute("EDIT_DEBT_DETAILS",edited)
            assertTrue("Demo edit failed",result is DebtApiResponse.Success && result.data.status == "APPLIED")
            try {
                val duplicate = api.execute("EDIT_DEBT_DETAILS",edited)
                assertTrue("Debt retry duplicated the edit",duplicate is DebtApiResponse.Success && duplicate.data.status == "DUPLICATE")
                val deletion = api.execute("DELETE_DEBT_IF_UNREFERENCED",signedDebt(buildJsonObject {
                    put("contract_version",1); put("operation_id",UUID.randomUUID().toString()); put("debt_id",debt.id)
                }))
                assertTrue("Debt history was not protected",deletion is DebtApiResponse.Error && deletion.message.orEmpty().contains("HISTORY_PRESERVED"))
            } finally {
                val restored = api.execute("EDIT_DEBT_DETAILS",request(debt.revision+1,debt.counterpartyName))
                assertTrue("Demo metadata cleanup failed",restored is DebtApiResponse.Success && restored.data.status == "APPLIED")
            }
            liveWorker<SyncDebtChangesWorker>(owner).doWork()
            val restored = access.database().debtDao().getDebt(owner,debt.id)
            assertTrue("Demo metadata did not restore",restored?.counterpartyName == debt.counterpartyName &&
                restored.totalMinor == debt.totalMinor && restored.status == debt.status && restored.notes == debt.notes)
        }
    }

    private fun signedDebt(unsigned: JsonObject): JsonObject = JsonObject(unsigned.toMutableMap().apply {
        put("request_hash",JsonPrimitive(com.kipu.app.feature.goals.domain.GoalCommandHasher.sha256(unsigned)))
    })

    @Test
    fun remoteBudgetCrudChecksRevisionConflictAndDeletionTombstone() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("planningRemoteAcceptance") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            val access = EntryPointAccessors.fromApplication(app, PlanningSyncVerificationAccess::class.java)
            val owner = withTimeout(30_000) {
                while (access.session().currentOwner?.verifiedUserId == null) delay(100)
                requireNotNull(access.session().currentOwner?.verifiedUserId)
            }
            val api = access.budgetApi()
            val id = UUID.randomUUID().toString()
            val now = Instant.now().toString()
            // A finished test budget does not displace either of the owner's two active Free budgets.
            val row = BudgetRemoteDto(id, owner, "QA Sync remoto", null, 1725, "PEN", "MONTHLY", null, null,
                listOf(5000,8000,10000), "FINISHED", false, 1, now, now)
            try {
                assertTrue("Budget creation failed", api.upsertBudget(owner, row, null) is BudgetApiResponse.Success)
                assertTrue("Period creation failed", api.upsertPeriod(owner, BudgetPeriodRemoteDto(UUID.randomUUID().toString(),
                    owner, id, "2026-10-01", "2026-11-01", 1725, createdAt = now)) is BudgetApiResponse.Success)
                val edited = row.copy(name = "QA Sync remoto editado", limitMinor = 1925, revision = 2)
                assertTrue("Budget edit failed", api.upsertBudget(owner, edited, 1) is BudgetApiResponse.Success)
                val stale = api.upsertBudget(owner, edited.copy(limitMinor = 9999, revision = 3), 1)
                assertTrue("Stale edit request failed unexpectedly", stale is BudgetApiResponse.Success)
                assertTrue("Stale revision overwrote the budget", (stale as BudgetApiResponse.Success).data.isEmpty())
                val actual = api.fetchBudget(owner, id)
                assertTrue("Budget confirmation failed", actual is BudgetApiResponse.Success)
                assertTrue("Remote budget state differs", (actual as BudgetApiResponse.Success).data.single().let { it.revision == 2L && it.limitMinor == 1925L })
            } finally {
                assertTrue("QA budget cleanup failed", api.deleteBudget(owner, id) is BudgetApiResponse.Success)
            }
            val remaining = api.fetchBudget(owner, id)
            assertTrue("Deleted budget still exists", remaining is BudgetApiResponse.Success && remaining.data.isEmpty())
            val tombstones = api.fetchTombstones(owner)
            assertTrue("Deletion tombstone missing", tombstones is BudgetApiResponse.Success && tombstones.data.any { it.budgetId == id })
            assertTrue("Repeated deletion is not idempotent", api.deleteBudget(owner, id) is BudgetApiResponse.Success)
        }
    }

    @Test
    fun pendingCommandsReachServerAndPlanningRestoresIntoAnEmptyDatabase() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("planningRemoteAcceptance") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            val access = EntryPointAccessors.fromApplication(app, PlanningSyncVerificationAccess::class.java)
            val owner = withTimeout(30_000) {
                while (access.session().currentOwner?.verifiedUserId == null) delay(100)
                requireNotNull(access.session().currentOwner?.verifiedUserId)
            }
            // Debt commands create referenced movements; pull those before applying linked history.
            liveWorker<SyncDebtChangesWorker>(owner).doWork()
            liveWorker<SyncMovementsWorker>(owner).doWork()
            liveWorker<SyncDebtChangesWorker>(owner).doWork()
            liveWorker<SyncGoalsWorker>(owner).doWork()
            liveWorker<SyncMovementsWorker>(owner).doWork()
            liveWorker<SyncGoalsWorker>(owner).doWork()
            val original = access.database()
            assertTrue("Debt commands still unresolved", scalar(original, "SELECT COUNT(*) FROM debt_command_outbox WHERE user_id=? AND state<>'SYNCED'", owner) == 0L)
            assertTrue("Goal commands still unresolved", scalar(original, "SELECT COUNT(*) FROM goal_command_outbox WHERE user_id=?", owner) == 0L)
            assertTrue("Goal feed has not applied", original.goalDao().getSyncCheckpoint(owner) != null)
            assertTrue("Debt feed has not applied", original.debtDao().getSyncCheckpoint(owner) != null)

            val restored = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
            try {
                val factory = object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker? = when (workerClassName) {
                        SyncMovementsWorker::class.java.name -> SyncMovementsWorker(appContext, params, restored.movementDao(),
                            access.movementApi(), access.financialApi(), restored.accountDao(), restored.cardDao(), restored, access.session())
                        SyncBudgetCommandsWorker::class.java.name -> SyncBudgetCommandsWorker(appContext, params,
                            restored.budgetDao(), restored.budgetAlertLedgerDao(), object : DatabaseTransactionRunner {
                                override suspend fun <R> invoke(block: suspend () -> R): R = restored.withTransaction { block() }
                            }, access.budgetApi(), access.session(), Clock.systemUTC(), ZoneId.of("America/Lima"))
                        else -> null
                    }
                }
                assertTrue("Financial feed restore failed", liveWorker<SyncMovementsWorker>(owner, factory).doWork() is ListenableWorker.Result.Success)
                val debts = DebtChangeFeedApplier(restored, restored.debtDao(), restored.movementDao())
                val goals = GoalChangeFeedApplier(restored, restored.goalDao(), restored.goalOutboxDao(), restored.accountDao(), restored.movementDao())
                var sequence = 0L
                var pages = 0
                do {
                    val response = access.financialApi().pullChanges(PullChangesRequestDto(afterSequence = sequence))
                    assertTrue("Feed request failed", response is FinancialApiResponse.Success)
                    val page = (response as FinancialApiResponse.Success).data
                    val debtResult = debts.applyPage(owner, page)
                    val goalResult = goals.applyPage(owner, page)
                    assertTrue("Debt restore rejected: $debtResult", debtResult is DebtChangeApplyResult.Applied)
                    assertTrue("Goal restore rejected: $goalResult", goalResult is GoalChangeApplyResult.Applied)
                    sequence = page.nextSequence
                    pages++
                    assertTrue("Feed did not converge", pages <= 20)
                } while (page.hasMore)
                assertTrue("Budget restore failed", liveWorker<SyncBudgetCommandsWorker>(owner, factory).doWork() is ListenableWorker.Result.Success)
                val restoredRows = restored.movementDao().queryTransactions(com.kipu.app.feature.movements.data.local.MovementQuerySqlBuilder.build(
                    owner, com.kipu.app.feature.movements.domain.model.MovementHistoryQuery(),
                ))
                assertTrue("Restored history is empty", restoredRows.isNotEmpty())
                restoredRows.forEach { it.movementToDomain() }
                val queries = mapOf(
                    "goals" to "SELECT id,name,target_minor,currency_code,status,revision,linked_account_id FROM goals WHERE user_id=? ORDER BY id",
                    "goal events" to "SELECT id,goal_id,delta_minor,currency_code,backing_account_id,operation_id,transaction_id FROM goal_events WHERE user_id=? ORDER BY id",
                    "debts" to "SELECT id,obligation_type,counterparty_name,total_minor,currency_code,status,revision,opened_on,opening_mode,due_date,reminder_lead_days,notes FROM debts WHERE user_id=? ORDER BY id",
                    "debt events" to "SELECT id,debt_id,transaction_id,interest_transaction_id,installment_id,event_type,amount_minor,principal_delta_minor FROM debt_events WHERE user_id=? ORDER BY id",
                    "installments" to "SELECT id,debt_id,installment_number,due_date,amount_minor,status,revision,deleted_at FROM debt_installments WHERE user_id=? ORDER BY id",
                    "budgets" to "SELECT id,name,category_id,limit_minor,currency_code,period_type,status,revision,alert_thresholds_bps FROM budgets WHERE user_id=? ORDER BY id",
                    "budget periods" to "SELECT id,budget_id,starts_on,ends_on,limit_minor,scope_category_id FROM budget_periods WHERE user_id=? ORDER BY id",
                    "account balances" to "SELECT a.id,a.currency,COALESCE((SELECT SUM(l.signed_amount_minor) FROM ledger_entries l WHERE l.user_id=a.user_id AND l.account_id=a.id),0) FROM accounts a WHERE a.user_id=? AND a.is_archived=0 AND a.linked_account_id IS NULL ORDER BY a.id",
                )
                for ((label, query) in queries) {
                    assertTrue("Restored $label differ from the original database", fingerprint(original, query, owner) == fingerprint(restored, query, owner))
                }
            } finally { restored.close() }
        }
    }

    private inline fun <reified W : ListenableWorker> liveWorker(owner: String, factory: WorkerFactory = app.workerFactory): W =
        TestListenableWorkerBuilder<W>(context).setWorkerFactory(factory).setInputData(workDataOf(
            SyncDebtChangesWorker.KEY_USER_ID to owner, SyncGoalsWorker.KEY_USER_ID to owner,
            SyncMovementsWorker.KEY_USER_ID to owner, SyncBudgetCommandsWorker.KEY_USER_ID to owner,
        )).build()

    private fun scalar(database: KipuDatabase, sql: String, owner: String): Long =
        database.openHelper.readableDatabase.query(sql, arrayOf(owner)).use { it.moveToFirst(); it.getLong(0) }

    private fun fingerprint(database: KipuDatabase, sql: String, owner: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        database.openHelper.readableDatabase.query(sql, arrayOf(owner)).use { cursor ->
            while (cursor.moveToNext()) for (column in 0 until cursor.columnCount) {
                val value = if (cursor.isNull(column)) "<null>" else cursor.getString(column)
                digest.update("${value.length}:$value;".toByteArray())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
