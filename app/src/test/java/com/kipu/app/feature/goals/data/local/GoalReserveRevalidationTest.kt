package com.kipu.app.feature.goals.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.goals.domain.model.GoalCommandEnvelope
import com.kipu.app.feature.goals.domain.model.GoalCommandIdentity
import com.kipu.app.feature.goals.domain.model.GoalCommandResult
import com.kipu.app.feature.goals.domain.model.GoalCommandType
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoalReserveRevalidationTest {
    private val userId = "reserve-revalidation-user"
    private lateinit var database: KipuDatabase
    private lateinit var local: GoalLocalDataSource

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
        local = GoalLocalDataSource(database, database.goalDao(), database.goalOutboxDao(), database.movementDao())
        listOf("goal-a", "goal-b").forEach { id ->
            database.goalDao().insertGoal(
                GoalEntity(id, userId, id, 20_000L, "PEN", status = "ACTIVE", revision = 1, syncState = "SYNCED", createdAt = 1, updatedAt = 1),
            )
        }
        database.accountDao().insert(
            AccountEntity(
                id = ACCOUNT, userId = userId, creationOperationId = "create-account", alias = "Ahorros",
                type = "SAVINGS", currency = "PEN", presetId = null, color = null, icon = null,
                initialBalanceMinorUnits = 10_000L, openedAt = 1, createdAt = 1, updatedAt = 1,
            ),
        )
        database.movementDao().insertTransaction(
            TransactionEntity(
                id = "opening", userId = userId, type = "INCOME", amountMinor = 10_000L, currencyCode = "PEN",
                sourceAccountId = ACCOUNT, operationKind = "STANDARD", occurredAt = 1, createdAt = 1, updatedAt = 1,
            ),
        )
        database.movementDao().insertLedgerEntries(
            listOf(
                LedgerEntryEntity(
                    id = "entry-opening", userId = userId, transactionId = "opening", accountId = ACCOUNT,
                    role = "DESTINATION", signedAmountMinor = 10_000L, currencyCode = "PEN", createdAt = 1,
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun secondReserveAgainstTheSameBalanceIsRejectedWithoutPartialRows() = runBlocking {
        // Ambas reservas planificaron con 10.000 libres; solo la primera cabe en la transaccion.
        assertTrue(reserve("goal-a", "op-a", 7_000L) is GoalCommandResult.Applied)

        val second = reserve("goal-b", "op-b", 7_000L)

        assertEquals(GoalCommandResult.Rejected("INSUFFICIENT_BACKING"), second)
        assertEquals(listOf("op-a"), database.goalOutboxDao().getPendingForUser(userId).map { it.operationId })
        assertEquals(0, database.goalDao().getEvents(userId, "goal-b").size)
        assertEquals(1, database.goalDao().getEvents(userId, "goal-a").size)
        assertEquals(null, database.movementDao().getReceipt(userId, "op-b"))
        assertEquals(1L, database.goalDao().getGoalIncludingArchived(userId, "goal-b")!!.revision)
        assertEquals(3_000L, database.goalDao().getBackingAccountPosition(userId, ACCOUNT)!!.availableMinor)
    }

    private suspend fun reserve(goalId: String, operationId: String, amountMinor: Long): GoalCommandResult {
        val goal = database.goalDao().getGoalIncludingArchived(userId, goalId)!!.toDomain()
        val updated = goal.copy(revision = goal.revision + 1, updatedAt = Instant.ofEpochMilli(2))
        val event = GoalEvent(
            id = "event-$operationId",
            userId = userId,
            goalId = goalId,
            type = GoalEventType.ALLOCATION,
            delta = Money(amountMinor, Currency.PEN),
            backingAccountId = ACCOUNT,
            operationId = operationId,
            occurredAt = Instant.ofEpochMilli(2),
        )
        val envelope = GoalCommandEnvelope(
            identity = GoalCommandIdentity(operationId, "a".repeat(64)),
            type = GoalCommandType.RESERVE_GOAL,
            goalId = goalId,
            expectedRevision = goal.revision,
            payload = buildJsonObject { },
        )
        return local.stageCommand(userId, envelope, listOf(updated), mapOf(goalId to goal.revision), listOf(event))
    }

    private companion object {
        const val ACCOUNT = "reserve-account"
    }
}
