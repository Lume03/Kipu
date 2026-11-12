package com.kipu.app.core.sync

import androidx.room.InvalidationTracker
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.movements.domain.MovementMaintenanceRepository
import com.kipu.app.feature.budgets.domain.BudgetRepository
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import com.kipu.app.feature.settings.domain.ProfilePreferencesRepository
import com.kipu.app.feature.goals.data.sync.GoalConflictResolver
import com.kipu.app.feature.goals.data.sync.GoalChangeApplyResult
import com.kipu.app.feature.goals.data.sync.GoalsSyncScheduler
import androidx.room.withTransaction
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.core.session.LocalAccess
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

enum class SyncModule { ACCOUNT, CARD, MOVEMENT, DEBT, GOAL, BUDGET, RECURRENCE, CATEGORY, PROFILE }
data class SyncIssue(val module: SyncModule, val entityId: String, val commandId: String,
    val retryable: Boolean, val rejectedCreation: Boolean, val title: String)

/** Reads local intent evidence; recovery is dispatched through the module repository ports. */
class SyncRecoveryRepository @Inject constructor(
    private val database: KipuDatabase,
    private val session: SessionCoordinator,
    private val instruments: FinancialInstrumentsRepository,
    private val movements: MovementMaintenanceRepository,
    private val debts: DebtRepository,
    private val budgets: BudgetRepository,
    private val categories: CategoriesRepository,
    private val recurrences: RecurrenceRepository,
    private val profile: ProfilePreferencesRepository,
    private val goalResolver: GoalConflictResolver,
    private val goalScheduler: GoalsSyncScheduler,
) {
    private data class Source(val module: SyncModule, val table: String, val entity: String,
        val command: String = "operation_id", val state: String = "state", val filter: String = "")
    private val sources = listOf(
        Source(SyncModule.ACCOUNT, "instrument_sync_outbox", "aggregate_id", filter = " AND aggregate_type='ACCOUNT'"),
        Source(SyncModule.CARD, "instrument_sync_outbox", "aggregate_id", filter = " AND aggregate_type='CARD'"),
        Source(SyncModule.MOVEMENT, "movement_outbox", "aggregate_id", "id"),
        Source(SyncModule.DEBT, "debt_command_outbox", "debt_id", filter = " AND command_type!='REMOTE_SNAPSHOT'"),
        Source(SyncModule.GOAL, "goal_command_outbox", "goal_id"),
        Source(SyncModule.BUDGET, "budget_sync_outbox", "aggregate_id"),
        Source(SyncModule.RECURRENCE, "recurrence_command_outbox", "recurrence_id"),
        Source(SyncModule.CATEGORY, "category_sync_outbox", "aggregate_id"),
        Source(SyncModule.PROFILE, "profile_preference_outbox", "user_id", state = "status"),
    )
    fun observe(userId: String): Flow<List<SyncIssue>> = callbackFlow {
        val observer = object : InvalidationTracker.Observer(sources.map { it.table }.distinct().toTypedArray()) {
            override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
        }
        database.invalidationTracker.addObserver(observer)
        trySend(Unit)
        awaitClose { database.invalidationTracker.removeObserver(observer) }
    }.conflate().map { readIssues(userId) }.flowOn(Dispatchers.IO)

    private fun readIssues(owner: String): List<SyncIssue> = sources.flatMap { source ->
        val result = mutableListOf<SyncIssue>()
        val type = when (source.module) {
            SyncModule.PROFILE -> "''"
            SyncModule.BUDGET -> "CASE WHEN command_type='UPSERT_BUDGET' AND expected_revision IS NULL THEN 'CREATE_BUDGET' ELSE command_type END"
            else -> "command_type"
        }
        val relatedPayload = if (source.module == SyncModule.GOAL) "payload" else "NULL"
        database.openHelper.readableDatabase.query(
            "SELECT ${source.entity}, ${source.command}, ${source.state}, $type, $relatedPayload FROM ${source.table} WHERE user_id=? AND ${source.state} IN ('CONFLICT','FAILED_PERMANENT','BLOCKED','FAILED','REJECTED','ERROR','RETRY','RETRYABLE','WAITING_FOR_AUTH')${source.filter} ORDER BY created_at, rowid",
            arrayOf(owner),
        ).use { cursor -> while (cursor.moveToNext()) {
            val state = cursor.getString(2)
            val creation = cursor.getString(3) in setOf("CREATE_ACCOUNT","REGISTER_CARD","REGISTER","CREATE_GOAL","CREATE_RECURRENCE","CREATE_BUDGET","CREATE_DEBT","OPEN_DEBT","CREATE_CATEGORY")
            result += SyncIssue(source.module, cursor.getString(0), cursor.getString(1),
                OutboxPolicy.canRetry(if (state == "RETRYABLE") "RETRY" else state),
                creation && state in setOf("CONFLICT","FAILED_PERMANENT","FAILED","REJECTED"), entityTitle(owner, source.module, cursor.getString(0)))
            if (source.module == SyncModule.GOAL) {
                val body = runCatching { Json.parseToJsonElement(cursor.getString(4)).jsonObject["payload"]?.jsonObject }.getOrNull()
                listOf("replacement_goal_id", "destination_goal_id").mapNotNull { body?.get(it)?.jsonPrimitive?.contentOrNull }.forEach { related ->
                    result += result.last().copy(entityId = related, rejectedCreation = false, title = entityTitle(owner, source.module, related))
                }
            }
        } }
        result.distinctBy { it.entityId }
    }

    private fun entityTitle(owner: String, module: SyncModule, id: String): String {
        val (table, column) = when (module) {
            SyncModule.ACCOUNT -> "accounts" to "alias"
            SyncModule.CARD -> "cards" to "COALESCE(alias, issuer)"
            SyncModule.GOAL -> "goals" to "name"
            SyncModule.DEBT -> "debts" to "counterparty_name"
            SyncModule.BUDGET -> "budgets" to "name"
            else -> return "Elemento pendiente de revisión"
        }
        return database.openHelper.readableDatabase.query("SELECT $column FROM $table WHERE user_id=? AND id=?", arrayOf(owner,id)).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else "Elemento pendiente de revisión"
        }
    }

    suspend fun recover(owner: String, issue: SyncIssue, discard: Boolean): Boolean = withContext(Dispatchers.IO) {
        if ((session.localAccess.value as? LocalAccess.Available)?.userId != owner) return@withContext false
        val fresh = readIssues(owner).firstOrNull { it.module == issue.module && it.commandId == issue.commandId } ?: return@withContext false
        if (discard && fresh.rejectedCreation) return@withContext database.withTransaction {
            if ((session.localAccess.value as? LocalAccess.Available)?.userId != owner) return@withTransaction false
            val current = readIssues(owner).firstOrNull { it.module == fresh.module && it.commandId == fresh.commandId && it.rejectedCreation } ?: return@withTransaction false
            val changed = deleteRejectedCreation(owner, current)
            check((session.localAccess.value as? LocalAccess.Available)?.userId == owner)
            changed
        }
        recoverCommand(owner, fresh, discard)
    }

    private suspend fun recoverCommand(owner: String, issue: SyncIssue, discard: Boolean): Boolean = when (issue.module) {
        SyncModule.ACCOUNT, SyncModule.CARD -> if (discard) instruments.discardLocal(owner, issue.commandId) else instruments.retryFailed(owner, issue.commandId)
        SyncModule.MOVEMENT -> if (discard) movements.discardLocal(owner, issue.commandId) else movements.retryFailed(owner, issue.commandId)
        SyncModule.DEBT -> if (discard) debts.discardLocal(owner, issue.commandId) else debts.retryFailed(owner, issue.commandId)
        SyncModule.BUDGET -> if (discard) budgets.discardLocal(owner, issue.commandId) else budgets.retryFailed(owner, issue.commandId)
        SyncModule.RECURRENCE -> if (discard) recurrences.discardLocal(owner, issue.commandId) else recurrences.retryFailed(owner, issue.commandId)
        SyncModule.CATEGORY -> if (discard) categories.discardLocal(owner, issue.commandId) else categories.retryFailed(owner, issue.commandId)
        SyncModule.PROFILE -> if (discard) profile.discardLocal(UUID.fromString(owner), UUID.fromString(issue.commandId)) else profile.retryFailed(UUID.fromString(owner), UUID.fromString(issue.commandId))
        SyncModule.GOAL -> {
            if (discard) {
                val goal = database.goalDao().getGoal(owner, issue.entityId)
                goal != null && goalResolver.discard(owner, issue.entityId, goal.revision) is GoalChangeApplyResult.Applied
            } else if (issue.retryable) {
                database.withTransaction {
                    val cmd = database.goalOutboxDao().get(owner, issue.commandId) ?: return@withTransaction false
                    if (cmd.state != "RETRYABLE") return@withTransaction false
                    database.goalOutboxDao().updateResult(owner, issue.commandId, "PENDING", null, null, cmd.resultPayload, System.currentTimeMillis())
                    true
                }.also { if (it) goalScheduler.schedule(owner) }
            } else false
        }
    }
    private suspend fun deleteRejectedCreation(owner: String, issue: SyncIssue): Boolean {
        val sql = database.openHelper.writableDatabase
        val args = arrayOf(owner, issue.entityId)
        val compensator = LocalRejectionCompensator(database)
        when (issue.module) {
            SyncModule.ACCOUNT, SyncModule.CARD -> return instruments.discardLocal(owner, issue.commandId)
            SyncModule.MOVEMENT -> return movements.discardLocal(owner, issue.commandId)
            SyncModule.GOAL -> {
                database.goalOutboxDao().getUnresolvedForUser(owner).filter { it.goalId == issue.entityId }.forEach {
                    compensator.neutralize(owner, it.operationId, "USER_DELETED_REJECTED_GOAL")
                    database.goalOutboxDao().updateResult(owner, it.operationId, "DISCARDED", null, "USER_DELETED_REJECTED_GOAL", it.resultPayload, System.currentTimeMillis())
                }
                sql.execSQL("UPDATE goals SET status='ARCHIVED', sync_state='SYNCED' WHERE user_id=? AND id=?", args)
                goalScheduler.schedule(owner)
            }
            SyncModule.DEBT -> {
                if (!debts.discardLocal(owner, issue.commandId)) return false
                val ids = mutableListOf<String>()
                sql.query("SELECT transaction_id FROM debt_events WHERE user_id=? AND debt_id=? UNION SELECT interest_transaction_id FROM debt_events WHERE user_id=? AND debt_id=?", arrayOf(owner, issue.entityId, owner, issue.entityId)).use {
                    while(it.moveToNext()) if (!it.isNull(0)) ids += it.getString(0)
                }
                ids.forEach { compensator.neutralize(owner, it, "USER_DELETED_REJECTED_DEBT") }
                sql.execSQL("UPDATE debt_command_outbox SET state='DISCARDED' WHERE user_id=? AND debt_id=? AND command_type!='REMOTE_SNAPSHOT'", args)
                sql.execSQL("UPDATE debts SET deleted_at=? WHERE user_id=? AND id=?", arrayOf<Any>(System.currentTimeMillis(), owner, issue.entityId))
            }
            SyncModule.BUDGET -> {
                if (!budgets.discardLocal(owner, issue.commandId)) return false
                sql.execSQL("UPDATE budget_sync_outbox SET state='DISCARDED' WHERE user_id=? AND aggregate_id=?", args)
                sql.execSQL("DELETE FROM budget_alert_ledger WHERE user_id=? AND budget_id=?", args)
                sql.execSQL("DELETE FROM budget_periods WHERE user_id=? AND budget_id=?", args)
                sql.execSQL("DELETE FROM budgets WHERE user_id=? AND id=?", args)
            }
            SyncModule.RECURRENCE -> {
                if (!recurrences.discardLocal(owner, issue.commandId)) return false
                sql.execSQL("UPDATE recurrence_command_outbox SET state='DISCARDED' WHERE user_id=? AND recurrence_id=?", args)
                sql.execSQL("UPDATE recurrences SET deleted_at=?, is_active=0, status='CANCELLED' WHERE user_id=? AND id=?", arrayOf<Any>(System.currentTimeMillis(),owner,issue.entityId))
            }
            SyncModule.CATEGORY -> {
                if (!categories.discardLocal(owner, issue.commandId)) return false
                sql.execSQL("UPDATE category_sync_outbox SET state='DISCARDED' WHERE user_id=? AND aggregate_id=?", args)
                sql.execSQL("DELETE FROM category_presentations WHERE user_id=? AND category_id=?", args)
                sql.execSQL("DELETE FROM categories WHERE user_id=? AND id=?", args)
            }
            SyncModule.PROFILE -> return false
        }
        return true
    }

}
