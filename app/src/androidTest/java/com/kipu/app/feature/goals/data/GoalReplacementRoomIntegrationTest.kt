package com.kipu.app.feature.goals.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.goals.data.local.GoalEntity
import com.kipu.app.feature.goals.data.local.GoalEventEntity
import com.kipu.app.feature.goals.data.local.GoalLocalDataSource
import com.kipu.app.feature.goals.data.local.GoalOutboxEntity
import com.kipu.app.feature.goals.data.local.toDomain
import com.kipu.app.feature.goals.data.remote.GoalCommandGoalRemoteResult
import com.kipu.app.feature.goals.data.sync.GoalChangeFeedApplier
import com.kipu.app.feature.goals.data.sync.GoalChangeApplyResult
import com.kipu.app.feature.goals.data.sync.GoalsSyncScheduler
import com.kipu.app.feature.goals.domain.model.GoalCommandEnvelope
import com.kipu.app.feature.goals.domain.model.GoalCommandIdentity
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.model.GoalCommandType
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.contentOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalReplacementRoomIntegrationTest {
    private lateinit var database: KipuDatabase
    private lateinit var local: GoalLocalDataSource
    private val userId = "replacement-test-user"

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        local = GoalLocalDataSource(
            database,
            database.goalDao(),
            database.goalOutboxDao(),
            database.movementDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacementStagesBothProjectionsAndOneOutboxAndReceiptAtomically() = runBlocking {
        seedGoal("source", "COMPLETED", revision = 4)
        seedGoal("replacement", "ACTIVE", revision = 8)
        seedHistory("source")
        seedHistory("replacement")

        val result = stageReplacement()

        assertTrue(result is GoalCommandResult.Applied)
        assertGoal("source", "ACTIVE", revision = 5, syncState = "PENDING")
        assertGoal("replacement", "LOCKED_BY_PLAN", revision = 9, syncState = "PENDING")
        val commands = database.goalOutboxDao().getPendingForUser(userId)
        assertEquals(1, commands.size)
        assertEquals("UPDATE_GOAL", commands.single().commandType)
        assertEquals(4L, commands.single().expectedRevision)
        assertEquals(4L, payloadLong(commands.single(), "expected_revision"))
        assertEquals(8L, payloadLong(commands.single(), "replacement_expected_revision"))
        assertEquals("replacement", payloadString(commands.single(), "replacement_goal_id"))
        val receipt = database.movementDao().getReceipt(userId, OPERATION_ID)
        assertEquals("PENDING", receipt?.status)
        assertEquals(1, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_command_receipts").use { it.moveToFirst(); it.getInt(0) })
        assertHistoryAndFinanceUnchanged()
    }

    @Test
    fun stagingWriteFailureRollsBackBothGoalsOutboxAndReceipt() = runBlocking {
        seedGoal("source", "COMPLETED", revision = 4)
        seedGoal("replacement", "ACTIVE", revision = 8)
        seedHistory("source")
        seedHistory("replacement")
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_goal_outbox BEFORE INSERT ON goal_command_outbox BEGIN SELECT RAISE(ABORT, 'forced outbox failure'); END",
        )

        val failure = runCatching { stageReplacement() }

        assertTrue("expected the trigger to fail command staging", failure.isFailure)
        assertGoal("source", "COMPLETED", revision = 4, syncState = "SYNCED")
        assertGoal("replacement", "ACTIVE", revision = 8, syncState = "SYNCED")
        assertTrue(database.goalOutboxDao().getPendingForUser(userId).isEmpty())
        assertNull(database.movementDao().getReceipt(userId, OPERATION_ID))
        assertHistoryAndFinanceUnchanged()
    }

    @Test
    fun releaseAtFreeQuotaStagesReleaseReplacementAndReceiptTogether() = runBlocking {
        seedGoal("release-source", "COMPLETED", revision = 4, targetMinor = 1_000)
        seedGoal("release-replacement", "ACTIVE", revision = 8, targetMinor = 1_000)
        seedGoal("other-active", "ACTIVE", revision = 2, targetMinor = 1_000)
        seedBackingAccountAndAllocation("release-source", 1_000)

        val repository = OfflineFirstGoalRepository(local, mockk<GoalsSyncScheduler>(relaxed = true))
        val result = repository.releaseGoalReserve(
            userId = userId,
            goalId = "release-source",
            backingAccountId = "backing-account",
            amount = Money(100, Currency.PEN),
            replacementGoalId = "release-replacement",
            replacementExpectedRevision = 8,
            hasPremiumAccess = false,
        )

        assertTrue("Unexpected result: $result", result is GoalCommandResult.Applied)
        assertGoal("release-source", "ACTIVE", revision = 5, syncState = "PENDING")
        assertGoal("release-replacement", "LOCKED_BY_PLAN", revision = 9, syncState = "PENDING")
        val releaseEvent = database.openHelper.readableDatabase.query(
            "SELECT event_type, delta_minor FROM goal_events WHERE user_id='$userId' AND goal_id='release-source' ORDER BY occurred_at DESC LIMIT 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0) to cursor.getLong(1)
        }
        assertEquals("RELEASE" to -100L, releaseEvent)
        assertEquals(2, database.goalDao().countEvents(userId, "release-source"))

        val commands = database.goalOutboxDao().getPendingForUser(userId)
        assertEquals(1, commands.size)
        assertEquals("RELEASE_GOAL", commands.single().commandType)
        assertEquals(4L, commands.single().expectedRevision)
        assertEquals(4L, payloadLong(commands.single(), "expected_revision"))
        assertEquals(8L, payloadLong(commands.single(), "replacement_expected_revision"))
        assertEquals("release-replacement", payloadString(commands.single(), "replacement_goal_id"))
        val receipt = database.movementDao().getReceipt(userId, commands.single().operationId)
        assertEquals("PENDING", receipt?.status)
        assertEquals("RELEASE_GOAL", receipt?.commandType)
        assertEquals(1, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_command_receipts").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transactions WHERE user_id='$userId'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ledger_entries WHERE user_id='$userId'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM movement_outbox WHERE user_id='$userId'").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun invalidReleaseReplacementDoesNotPersistGoalEventOutboxOrReceipt() = runBlocking {
        seedGoal("release-source", "COMPLETED", revision = 4, targetMinor = 1_000)
        seedGoal("release-replacement", "LOCKED_BY_PLAN", revision = 8, targetMinor = 1_000)
        seedGoal("other-active", "ACTIVE", revision = 2, targetMinor = 1_000)
        seedGoal("second-active", "ACTIVE", revision = 3, targetMinor = 1_000)
        seedBackingAccountAndAllocation("release-source", 1_000)
        val repository = OfflineFirstGoalRepository(local, mockk<GoalsSyncScheduler>(relaxed = true))

        val result = repository.releaseGoalReserve(
            userId = userId,
            goalId = "release-source",
            backingAccountId = "backing-account",
            amount = Money(100, Currency.PEN),
            replacementGoalId = "release-replacement",
            replacementExpectedRevision = 8,
            hasPremiumAccess = false,
        )

        assertEquals(GoalCommandResult.Rejected("REPLACEMENT_NOT_ACTIVE"), result)
        assertGoal("release-source", "COMPLETED", revision = 4, syncState = "SYNCED")
        assertGoal("release-replacement", "LOCKED_BY_PLAN", revision = 8, syncState = "SYNCED")
        assertEquals(1, database.goalDao().countEvents(userId, "release-source"))
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM goal_events WHERE user_id='$userId' AND event_type='RELEASE'").use { it.moveToFirst(); it.getInt(0) })
        assertTrue(database.goalOutboxDao().getPendingForUser(userId).isEmpty())
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_command_receipts").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun staleReleaseReplacementDoesNotPersistGoalEventOutboxOrReceipt() = runBlocking {
        seedGoal("release-source", "COMPLETED", revision = 4, targetMinor = 1_000)
        seedGoal("release-replacement", "ACTIVE", revision = 8, targetMinor = 1_000)
        seedGoal("other-active", "ACTIVE", revision = 2, targetMinor = 1_000)
        seedBackingAccountAndAllocation("release-source", 1_000)
        val repository = OfflineFirstGoalRepository(local, mockk<GoalsSyncScheduler>(relaxed = true))

        val result = repository.releaseGoalReserve(
            userId = userId,
            goalId = "release-source",
            backingAccountId = "backing-account",
            amount = Money(100, Currency.PEN),
            replacementGoalId = "release-replacement",
            replacementExpectedRevision = 7,
            hasPremiumAccess = false,
        )

        assertEquals(GoalCommandResult.Conflict("REVISION_MISMATCH", 8), result)
        assertGoal("release-source", "COMPLETED", revision = 4, syncState = "SYNCED")
        assertGoal("release-replacement", "ACTIVE", revision = 8, syncState = "SYNCED")
        assertEquals(1, database.goalDao().countEvents(userId, "release-source"))
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM goal_events WHERE user_id='$userId' AND event_type='RELEASE'").use { it.moveToFirst(); it.getInt(0) })
        assertTrue(database.goalOutboxDao().getPendingForUser(userId).isEmpty())
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_command_receipts").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun appliedAndDuplicateCompositeReceiptsReconcileBothGoalsAndRetireOneCommand() = runBlocking {
        listOf("APPLIED", "DUPLICATE").forEachIndexed { index, state ->
            val operationId = "receipt-operation-$index"
            seedGoal("source-$index", "COMPLETED", revision = 4)
            seedGoal("replacement-$index", "ACTIVE", revision = 8)
            seedHistory("source-$index")
            seedHistory("replacement-$index")
            val command = stageReplacement("source-$index", "replacement-$index", operationId)
            assertTrue(command is GoalCommandResult.Applied)
            val outbox = database.goalOutboxDao().get(userId, operationId)!!

            assertTrue(
                local.finishRemoteCommand(
                    outbox,
                    state,
                    null,
                    null,
                    listOf(
                        GoalCommandGoalRemoteResult("source-$index", "ACTIVE", 5, 20),
                        GoalCommandGoalRemoteResult("replacement-$index", "LOCKED_BY_PLAN", 9, 21),
                    ),
                ),
            )

            assertGoal("source-$index", "ACTIVE", revision = 5, syncState = "SYNCED")
            assertGoal("replacement-$index", "LOCKED_BY_PLAN", revision = 9, syncState = "SYNCED")
            assertNull(database.goalOutboxDao().get(userId, operationId))
            assertEquals("SYNCED", database.movementDao().getReceipt(userId, operationId)?.status)
        }
        assertHistoryAndFinanceUnchanged(expectedHistoryCount = 4)
    }

    @Test
    fun incompleteOrInvalidCompositeReceiptDoesNotPartiallyReconcile() = runBlocking {
        listOf(
            listOf(GoalCommandGoalRemoteResult("source", "ACTIVE", 5, 20)),
            listOf(
                GoalCommandGoalRemoteResult("source", "ACTIVE", 5, 20),
                GoalCommandGoalRemoteResult("wrong-id", "LOCKED_BY_PLAN", 9, 21),
            ),
            listOf(
                GoalCommandGoalRemoteResult("source", "ACTIVE", 5, 20),
                GoalCommandGoalRemoteResult("replacement", "NOT_A_STATUS", 9, 21),
            ),
        ).forEachIndexed { index, receiptGoals ->
            val operationId = "invalid-receipt-$index"
            val sourceId = "source-$index"
            val replacementId = "replacement-$index"
            seedGoal(sourceId, "COMPLETED", revision = 4)
            seedGoal(replacementId, "ACTIVE", revision = 8)
            seedHistory(sourceId)
            seedHistory(replacementId)
            val staged = stageReplacement(sourceId, replacementId, operationId)
            assertTrue(staged is GoalCommandResult.Applied)
            val command = database.goalOutboxDao().get(userId, operationId)!!

            val result = runCatching {
                local.finishRemoteCommand(command, "APPLIED", null, null, receiptGoals)
            }.getOrDefault(false)

            assertFalse(result)
            assertGoal(sourceId, "ACTIVE", revision = 5, syncState = "PENDING")
            assertGoal(replacementId, "LOCKED_BY_PLAN", revision = 9, syncState = "PENDING")
            assertEquals("PENDING", database.goalOutboxDao().get(userId, operationId)?.state)
            assertEquals("PENDING", database.movementDao().getReceipt(userId, operationId)?.status)
        }
        assertHistoryAndFinanceUnchanged(expectedHistoryCount = 6)
    }

    @Test
    fun staleReplacementFeedChangeConflictsBothAndRollsBackWholePageAndCheckpoint() = runBlocking {
        seedGoal("unaffected", "ACTIVE", revision = 1)
        seedGoal("source", "COMPLETED", revision = 4)
        seedGoal("replacement", "ACTIVE", revision = 8)
        seedHistory("source")
        seedHistory("replacement")
        assertTrue(stageReplacement() is GoalCommandResult.Applied)
        val applier = GoalChangeFeedApplier(
            database,
            database.goalDao(),
            database.goalOutboxDao(),
            database.accountDao(),
            database.movementDao(),
        )
        val page = PullChangesResponseDto(
            changes = listOf(
                goalChange("unaffected", sequence = 1, revision = 2, name = "Remote unaffected"),
                goalChange("replacement", sequence = 2, revision = 8, name = "Stale replacement"),
            ),
            nextSequence = 2,
        )

        val result = applier.applyPage(userId, page)

        assertTrue(result is GoalChangeApplyResult.Conflict)
        assertGoal("source", "ACTIVE", revision = 5, syncState = "CONFLICT")
        assertGoal("replacement", "LOCKED_BY_PLAN", revision = 9, syncState = "CONFLICT")
        assertNull(database.goalDao().getGoalIncludingArchived(userId, "unaffected")?.let { it.takeIf { row -> row.name == "Remote unaffected" } })
        assertEquals(null, database.goalDao().getSyncCheckpoint(userId))
        assertHistoryAndFinanceUnchanged()
    }

    @Test
    fun staleSourceFeedChangeAlsoConflictsBothAndRollsBackWholePage() = runBlocking {
        seedGoal("unaffected", "ACTIVE", revision = 1)
        seedGoal("source", "COMPLETED", revision = 4)
        seedGoal("replacement", "ACTIVE", revision = 8)
        seedHistory("source")
        seedHistory("replacement")
        assertTrue(stageReplacement() is GoalCommandResult.Applied)
        val applier = GoalChangeFeedApplier(database, database.goalDao(), database.goalOutboxDao(), database.accountDao(), database.movementDao())
        val result = applier.applyPage(
            userId,
            PullChangesResponseDto(
                changes = listOf(
                    goalChange("unaffected", sequence = 1, revision = 2, name = "Remote unaffected"),
                    goalChange("source", sequence = 2, revision = 4, name = "Stale source"),
                ),
                nextSequence = 2,
            ),
        )

        assertTrue(result is GoalChangeApplyResult.Conflict)
        assertGoal("source", "ACTIVE", revision = 5, syncState = "CONFLICT")
        assertGoal("replacement", "LOCKED_BY_PLAN", revision = 9, syncState = "CONFLICT")
        assertNull(database.goalDao().getGoalIncludingArchived(userId, "unaffected")?.takeIf { it.name == "Remote unaffected" })
        assertEquals(null, database.goalDao().getSyncCheckpoint(userId))
        assertHistoryAndFinanceUnchanged()
    }

    private suspend fun stageReplacement(
        sourceId: String = "source",
        replacementId: String = "replacement",
        operationId: String = OPERATION_ID,
    ): GoalCommandResult {
        val source = database.goalDao().getGoalIncludingArchived(userId, sourceId)!!
        val replacement = database.goalDao().getGoalIncludingArchived(userId, replacementId)!!
        val updatedSource = source.copy(name = "Reactivated goal", status = "ACTIVE", revision = source.revision + 1)
        val updatedReplacement = replacement.copy(status = "LOCKED_BY_PLAN", revision = replacement.revision + 1)
        val envelope = GoalCommandEnvelope(
            identity = GoalCommandIdentity(operationId, "a".repeat(64)),
            type = GoalCommandType.UPDATE_GOAL,
            goalId = sourceId,
            expectedRevision = source.revision,
            payload = buildJsonObject {
                put("name", updatedSource.name)
                put("replacement_goal_id", replacementId)
                put("replacement_expected_revision", replacement.revision)
            },
        )
        return local.stageCommand(
            userId,
            envelope,
            listOf(updatedSource.toDomain(), updatedReplacement.toDomain()),
            mapOf(sourceId to source.revision, replacementId to replacement.revision),
        )
    }

    private suspend fun seedGoal(id: String, status: String, revision: Long, targetMinor: Long = 10_000) {
        database.goalDao().insertGoal(
            GoalEntity(id, userId, id, targetMinor, "PEN", status = status, revision = revision, syncState = "SYNCED", createdAt = 1, updatedAt = 1),
        )
    }

    private suspend fun seedBackingAccountAndAllocation(goalId: String, amountMinor: Long) {
        database.accountDao().insert(
            AccountEntity(
                id = "backing-account",
                userId = userId,
                creationOperationId = "backing-account-create",
                alias = "Backing account",
                type = "SAVINGS",
                currency = "PEN",
                presetId = null,
                color = null,
                icon = null,
                initialBalanceMinorUnits = 10_000,
                openedAt = 1,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        database.goalDao().insertEvent(
            GoalEventEntity(
                id = "allocation-$goalId",
                userId = userId,
                goalId = goalId,
                eventType = "ALLOCATION",
                deltaMinor = amountMinor,
                currencyCode = "PEN",
                backingAccountId = "backing-account",
                operationId = "allocation-operation",
                occurredAt = 10,
                createdAt = 10,
            ),
        )
    }

    private suspend fun seedHistory(goalId: String) {
        database.goalDao().insertEvent(
            GoalEventEntity("event-$goalId", userId, goalId, eventType = "ALLOCATION", deltaMinor = 500, currencyCode = "PEN", occurredAt = 10, createdAt = 10),
        )
    }

    private suspend fun assertGoal(id: String, status: String, revision: Long, syncState: String) {
        val goal = database.goalDao().getGoalIncludingArchived(userId, id)!!
        assertEquals(status, goal.status)
        assertEquals(revision, goal.revision)
        assertEquals(syncState, goal.syncState)
    }

    private suspend fun assertHistoryAndFinanceUnchanged(expectedHistoryCount: Int = 2) {
        assertEquals(expectedHistoryCount, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM goal_events WHERE user_id='$userId'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM goal_events WHERE user_id='$userId' AND (event_type != 'ALLOCATION' OR delta_minor != 500 OR currency_code != 'PEN' OR occurred_at != 10)").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transactions WHERE user_id='$userId'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ledger_entries WHERE user_id='$userId'").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM movement_outbox WHERE user_id='$userId'").use { it.moveToFirst(); it.getInt(0) })
    }

    private fun goalChange(id: String, sequence: Long, revision: Long, name: String): SyncChangeItemDto =
        SyncChangeItemDto(
            sequence = sequence,
            entityType = "GOAL",
            entityId = id,
            revision = revision,
            operation = "UPSERT",
            payload = buildJsonObject {
                put("id", id)
                put("user_id", userId)
                put("name", name)
                put("target_minor", 10_000)
                put("currency_code", "PEN")
                put("status", "ACTIVE")
                put("revision", revision)
            },
        )

    private fun payloadLong(command: GoalOutboxEntity, field: String): Long =
        kotlinx.serialization.json.Json.parseToJsonElement(command.payload)
            .jsonObject.let { root ->
                (root[field] ?: root.getValue("payload").jsonObject.getValue(field)).jsonPrimitive.long
            }

    private fun payloadString(command: GoalOutboxEntity, field: String): String =
        kotlinx.serialization.json.Json.parseToJsonElement(command.payload)
            .jsonObject.getValue("payload").jsonObject.getValue(field).jsonPrimitive.contentOrNull!!

    private companion object {
        const val OPERATION_ID = "atomic-replacement-operation"
    }
}
