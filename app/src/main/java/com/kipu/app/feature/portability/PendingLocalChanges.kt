package com.kipu.app.feature.portability

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.kipu.app.core.database.KipuDatabase
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingLocalSummary(val byModule: Map<String, Int>) {
    val total: Int get() = byModule.values.sum()
}
class PendingLocalChanges @Inject constructor(private val database: KipuDatabase) {
    suspend operator fun invoke(owner: String): PendingLocalSummary = withContext(Dispatchers.IO) {
        require(owner.isNotBlank())
        database.withTransaction {
            PendingLocalSummary(queues.mapValues { (table, state) ->
                val filter = state?.let { " AND $it NOT IN ('APPLIED','DUPLICATE','COMPLETED','SYNCED','SUCCEEDED')" }.orEmpty()
                database.openHelper.readableDatabase.query(SimpleSQLiteQuery(
                    "SELECT COUNT(*) FROM $table WHERE user_id = ?$filter", arrayOf(owner)
                )).use { it.moveToFirst(); it.getInt(0) }
            })
        }
    }
    companion object {
        val queues: Map<String, String?> = linkedMapOf(
        "sync_outbox" to "status",
        "profile_preference_outbox" to "status",
        "instrument_sync_outbox" to "state",
        "category_sync_outbox" to "state",
        "debt_command_outbox" to "state",
        "goal_command_outbox" to "state",
        "movement_outbox" to "state",
        "notification_sync_outbox" to null,
        "recurrence_command_outbox" to "state",
        "budget_sync_outbox" to "state",
        )
    }
}
