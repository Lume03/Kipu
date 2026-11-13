package com.kipu.app.core.sync

import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.movements.data.sync.*
import com.kipu.app.feature.debts.data.sync.*
import com.kipu.app.feature.goals.data.sync.*
import com.kipu.app.feature.recurrences.data.sync.RecurrenceHydration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** One download, independent durable module checkpoints (including checkpoints from schema 41).
 * Pages are never buffered beyond the current page. After cancellation, restart at the minimum
 * committed cursor and skip the suffix already committed by each successful module.
 */
@Singleton
class SharedChangeFeed @Inject constructor(
    private val api: FinancialInstrumentsApi,
    private val database: KipuDatabase,
    private val session: SessionCoordinator,
    private val movements: MovementHydration,
    private val debts: DebtChangeFeedApplier,
    private val goals: GoalChangeFeedApplier,
    private val recurrences: RecurrenceHydration,
) {
    private val mutex = Mutex()

    suspend fun pullChanges(userId: String): Boolean = mutex.withLock {
        SharedFeedReader().pull(
            fetch = { cursor -> ownerRequest(session, userId) { api.pullChanges(PullChangesRequestDto(afterSequence = cursor), userId) } },
            modules = listOf(
                FeedModule({ database.movementDao().getSyncCheckpoint(userId)?.sequence ?: 0L }) { movements.applyPage(userId, it) },
                FeedModule({ database.debtDao().getSyncCheckpoint(userId)?.sequence ?: 0L }) { debts.applyPage(userId, it) is DebtChangeApplyResult.Applied },
                FeedModule({ database.goalDao().getSyncCheckpoint(userId)?.sequence ?: 0L }) { goals.applyPage(userId, it) is GoalChangeApplyResult.Applied },
            ).map { module -> module.copy(apply = { page ->
                ownerTransaction(database, session, userId) { module.apply(page) }
            }) } + FeedModule({ database.recurrenceDao().checkpoint(userId)?.sequence ?: 0L }) { page ->
                // Repository takes its reconciliation mutex before entering its own Room transaction.
                // Do not invert that lock order by holding a Room transaction here.
                requireSyncOwner(session, userId)
                recurrences.applyPage(userId, page).also { requireSyncOwner(session, userId) }
            },
        )
    }
}

internal data class FeedModule(
    val checkpoint: suspend () -> Long,
    val apply: suspend (PullChangesResponseDto) -> Boolean,
)

/** Network traversal separated from Room adapters so interruption and cursor skew are testable. */
internal class SharedFeedReader {
    suspend fun pull(fetch: suspend (Long) -> FinancialApiResponse<PullChangesResponseDto>, modules: List<FeedModule>): Boolean {
        require(modules.isNotEmpty())
        var cursor = modules.minOf { it.checkpoint() }
        val failed = mutableSetOf<Int>()
        repeat(10000) {
            val page = when (val response = fetch(cursor)) {
                is FinancialApiResponse.Success -> response.data
                else -> return false
            }
            // Validate before any writes: an unknown future type rolls back the whole page.
            if (!SyncFeedCursor.isValidPage(cursor, page) || page.changes.any { it.entityType.uppercase() !in KNOWN_TYPES }) return false
            if (page.changes.isEmpty()) return !page.hasMore && failed.isEmpty()
            modules.forEachIndexed { index, module ->
                if (index !in failed) {
                    val checkpoint = module.checkpoint()
                    if (checkpoint < page.nextSequence) {
                        val suffix = page.copy(changes = page.changes.filter { it.sequence > checkpoint })
                        val applied = try { module.apply(suffix) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { false }
                        if (!applied) failed += index
                    }
                }
            }
            if (!page.hasMore) return failed.isEmpty()
            if (failed.size == modules.size) return false
            cursor = page.nextSequence
        }
        return false
    }

    private companion object {
        // Snapshot-only modules keep their existing hydration endpoints; their feed markers are known.
        val KNOWN_TYPES = setOf("ACCOUNT", "CARD", "TRANSACTION", "MOVEMENT", "DEBT", "DEBT_EVENT",
            "DEBT_INSTALLMENT", "GOAL", "GOAL_EVENT", "RECURRENCE", "CATEGORY", "BUDGET", "SAVINGS_GOAL",
            "OBLIGATION", "MERCHANT_ALIAS_RULE", "MERCHANT_CATEGORY_PREFERENCE")
    }
}
