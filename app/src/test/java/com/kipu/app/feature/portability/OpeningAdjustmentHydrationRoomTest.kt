package com.kipu.app.feature.portability

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.*
import com.kipu.app.core.session.*
import com.kipu.app.feature.accounts.data.remote.*
import com.kipu.app.feature.movements.data.sync.*
import com.kipu.app.feature.goals.data.sync.*
import com.kipu.app.feature.debts.data.sync.*
import com.kipu.app.feature.budgets.data.sync.*
import com.kipu.app.feature.budgets.data.remote.*
import com.kipu.app.feature.recurrences.data.sync.*
import com.kipu.app.feature.recurrences.data.remote.*
import com.kipu.app.feature.recurrences.data.repository.OfflineFirstRecurrenceRepository
import com.kipu.app.feature.categories.data.sync.*
import com.kipu.app.feature.categories.data.remote.*
import io.mockk.*
import java.time.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class OpeningAdjustmentHydrationRoomTest {
    private val owner = "5d88af4d-6bf2-4b75-8a43-ac7db0182fca"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KipuDatabase
    private val session = mockk<SessionCoordinator> { every { currentOwner } returns LocalOwner(owner) }
    private val api = mockk<FinancialInstrumentsApi>()
    private val clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC)
    private val runner = object : DatabaseTransactionRunner {
        override suspend fun <R> invoke(block: suspend () -> R): R = db.withTransaction { block() }
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }
    private fun change(type: String, id: String, payload: String, sequence: Long = 1) =
        SyncChangeItemDto(sequence, type, id, 1L, "UPSERT", Json.parseToJsonElement(payload))
    private fun feed(vararg changes: SyncChangeItemDto) {
        coEvery { api.pullChanges(any(), any()) } answers {
            val cursor = firstArg<PullChangesRequestDto>().afterSequence
            FinancialApiResponse.Success(PullChangesResponseDto(changes.filter { it.sequence > cursor }, maxOf(cursor, changes.last().sequence)))
        }
    }
    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun snapshot(): String {
        val amounts = listOf(10000L, -10000L, 20000L, -20000L, 30000L)
        val facts = amounts.mapIndexed { index, amount ->
            val kind = if (index == 0) "OPENING" else if (index % 2 == 1) "REVERSAL" else "ADJUSTMENT"
            """{"id":"fact$index","operation_id":"op${(index+1)/2}","operation_sequence":${if (index == 0 || index % 2 == 1) 0 else 1},"kind":"$kind","amount_minor_units":$amount,"currency":"PEN","account_id":"cash","opening_account_id":${if (index == 0) "\"cash\"" else "null"},"effective_at":"2026-11-01T00:00:00Z","status":"POSTED","reverses_movement_id":${if (index % 2 == 1) "\"fact${index-1}\"" else "null"},"adjusts_movement_id":${if (kind == "ADJUSTMENT") "\"fact0\"" else "null"},"created_at":"2026-11-01T00:00:00Z"}"""
        }
        return """{"id":"cash","alias":"Caja","type":"CASH","currency":"PEN","initial_balance_minor_units":30000,"effective_opening_balance_minor_units":30000,"opened_at":"2026-11-01T00:00:00Z","opening_movements":[${facts.joinToString(",") }]}"""
    }
    private fun hydration() = MovementHydration(db.movementDao(), api, db.accountDao(), db.cardDao(), db, session)
    private suspend fun assertFinalState() {
        assertEquals(5, count("financial_movements"))
        assertEquals(5, count("ledger_entries"))
        assertEquals(30000L, db.movementDao().calculateLedgerSumForAccount(owner, "cash"))
        assertEquals(30000L, db.movementDao().getBalanceProjection(owner, "cash")?.balanceMinor)
        assertEquals("fact2", db.financialMovementDao().getById(owner, "fact3")?.reversesMovementId)
        assertEquals("fact0", db.financialMovementDao().getById(owner, "fact4")?.adjustsMovementId)
    }
    @Test fun restoresTwoCorrectionsAndReplayDoesNotDuplicate() = runTest {
        feed(change("ACCOUNT", "cash", snapshot()).copy(revision = 3))
        repeat(2) {
            assertTrue(hydration().hydrateFully(owner))
            assertFinalState()
        }
    }
    @Test fun locallyRecordedFactsConvergeUnderSameIds() = runTest {
        feed(change("ACCOUNT", "cash", """{"id":"cash","alias":"Caja","type":"CASH","currency":"PEN","initial_balance_minor_units":0,"opened_at":"2026-11-01T00:00:00Z"}"""))
        assertTrue(hydration().hydrateFully(owner))
        val payload = Json.decodeFromString<SyncAccountPayload>(snapshot())
        for (fact in payload.openingMovements.orEmpty()) {
            db.financialMovementDao().insert(com.kipu.app.feature.accounts.data.local.FinancialMovementEntity(
                id = fact.id, userId = owner, operationId = fact.operationId, operationSequence = fact.operationSequence,
                kind = fact.kind, amountMinorUnits = fact.amountMinorUnits, currency = fact.currency,
                accountId = "cash", openingAccountId = fact.openingAccountId, effectiveAt = 1000,
                reversesMovementId = fact.reversesMovementId, adjustsMovementId = fact.adjustsMovementId, createdAt = 1000,
            ))
        }
        feed(change("ACCOUNT", "cash", snapshot(), 2).copy(revision = 3))
        repeat(2) { assertTrue(hydration().hydrateFully(owner)); assertFinalState() }
    }
    @Test fun replacesLegacySyntheticOpeningOnRestore() = runTest {
        feed(change("ACCOUNT", "cash", """{"id":"cash","alias":"Caja","type":"CASH","currency":"PEN","initial_balance_minor_units":10000,"opened_at":"2026-11-01T00:00:00Z"}"""))
        assertTrue(hydration().hydrateFully(owner))
        feed(change("ACCOUNT", "cash", snapshot(), 2).copy(revision = 3))
        assertTrue(hydration().hydrateFully(owner))
        assertFinalState()
    }
}
