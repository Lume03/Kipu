package com.kipu.app.feature.recurrences.data.sync

import javax.inject.Inject

import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.accounts.data.remote.parseFeedInstant
import com.kipu.app.feature.recurrences.data.local.RecurrenceDao
import com.kipu.app.feature.recurrences.data.remote.RecurrencesApi
import com.kipu.app.feature.recurrences.data.remote.RecurrencesApiResponse
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import java.time.LocalDate
import kotlinx.serialization.json.*

class RecurrenceHydration @Inject constructor(private val recurrenceDao: RecurrenceDao, private val repository: RecurrenceRepository, private val api: RecurrencesApi, private val sessionCoordinator: SessionCoordinator) {
    suspend fun hydrateFully(userId: String): Boolean {
    if (!isCurrentOwner(userId)) return false
        recurrenceDao.saveCheckpoint(com.kipu.app.feature.recurrences.data.local.RecurrenceSyncCheckpointEntity(userId, 0L, 0L))
        return pullChanges(userId)
    }
    suspend fun pullChanges(userId: String): Boolean {
        repeat(10000) {
            if (!isCurrentOwner(userId)) return false
            val checkpoint = recurrenceDao.checkpoint(userId)?.sequence ?: 0L
            when (val response = api.pullChanges(userId, checkpoint)) {
                is RecurrencesApiResponse.Success -> {
                    if (!isCurrentOwner(userId)) return false
                    val page = response.data
                    require(page.nextSequence >= checkpoint)
                    require(page.changes.all { it.sequence > checkpoint && it.sequence <= page.nextSequence })
                    val rules = page.changes.filter { it.entityType == "RECURRENCE" }.map { it.toRule(userId) }
                    repository.applyChangeFeed(userId, page.nextSequence, rules)
                    if (!page.hasMore) return true
                    require(page.nextSequence > checkpoint)
                }
                is RecurrencesApiResponse.Error, is RecurrencesApiResponse.NetworkFailure -> return false
            }
        }
        return false
    }

    private fun SyncChangeItemDto.toRule(userId: String): RecurrenceRule {
        require(operation == "UPSERT") { "Unsupported recurrence feed operation" }
        val envelope = requireNotNull(payload).jsonObject
        val rule = requireNotNull(envelope["rule"]).jsonObject
        fun text(key: String): String = requireNotNull(rule[key]).jsonPrimitive.content
        fun optional(key: String): String? = rule[key]?.jsonPrimitive?.contentOrNull
        require(optional("user_id") == null || optional("user_id") == userId) { "Feed owner mismatch" }
        require(optional("id") == null || optional("id") == entityId)
        require(envelope["recurrence_id"]?.jsonPrimitive?.content == entityId)
        require(revision > 0 && envelope["revision"]?.jsonPrimitive?.long == revision)
        val updated = parseFeedInstant(optional("updated_at") ?: requireNotNull(envelope["updated_at"]).jsonPrimitive.content)
        return RecurrenceRule(
            id = entityId, userId = userId, templateTransaction = requireNotNull(rule["template_transaction"]).jsonObject,
            amountMode = text("amount_mode"), frequency = text("frequency"), intervalCount = requireNotNull(rule["interval_count"]).jsonPrimitive.long,
            customIntervalUnit = optional("custom_interval_unit"), startDate = LocalDate.parse(text("start_date")),
            endDate = optional("end_date")?.let(LocalDate::parse), nextDueDate = LocalDate.parse(text("next_due_date")),
            reminderLeadDays = requireNotNull(rule["reminder_lead_days"]).jsonPrimitive.int,
            status = RecurrenceStatus.valueOf(optional("status") ?: envelope["state"]?.jsonPrimitive?.content ?: "ACTIVE"),
            revision = revision, syncState = RecurrenceSyncState.SYNCED,
            createdAt = optional("created_at")?.let(::parseFeedInstant) ?: updated, updatedAt = updated,
        )
    }

    private fun isCurrentOwner(userId: String) = sessionCoordinator.currentOwner?.verifiedUserId == userId
}
