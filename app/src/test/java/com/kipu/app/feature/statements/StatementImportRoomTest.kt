package com.kipu.app.feature.statements

import android.content.Context
import androidx.room.Room
import androidx.room.util.TableInfo
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.database.MIGRATION_29_30
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.statements.data.local.*
import com.kipu.app.feature.statements.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatementImportRoomTest {
    private lateinit var database: KipuDatabase
    private lateinit var store: RoomStatementImportStore
    private val owner = UUID.randomUUID().toString()
    private val other = UUID.randomUUID().toString()
    private val now = 1000L

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java)
            .allowMainThreadQueries().build()
        store = RoomStatementImportStore(database, database.statementImportDao(), Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))
    }
    @After fun close() = database.close()

    private suspend fun create(fingerprint: String = "statement", lines: List<StatementLine> = listOf(importLine())) =
        store.create(owner, parsedStatement(lines, fingerprint), null, lines.map { StatementImportCandidate(it, true, false) }).statement

    @Test fun `create is atomic deduplicates per owner retains ordered lines and bounded descriptions`() = runBlocking {
        val first = create(lines = listOf(importLine("second").copy(description = "x".repeat(200)), importLine("first")))
        val replay = store.create(owner, parsedStatement(), null, emptyList())
        assertFalse(replay.created)
        assertEquals(first.id, replay.statement.id)
        assertEquals(now + RoomStatementImportStore.RETENTION_MILLIS, first.expiresAt)
        val snapshot = store.observe(owner, first.id).first()!!
        assertEquals(listOf("second", "first"), snapshot.lines.map { it.lineFingerprint })
        assertEquals(120, snapshot.lines.first().description.length)
        assertTrue(store.create(other, parsedStatement(), null, emptyList()).created)
        try {
            create("rollback", listOf(importLine("duplicate"), importLine("duplicate")))
            fail("unique line fingerprint must abort")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertNull(store.findByFingerprint(owner, "rollback"))
    }

    @Test fun `owner scopes reads edits selection registration discard complete and purge`() = runBlocking {
        val statement = create()
        val line = store.observe(owner, statement.id).first()!!.lines.single()
        assertNull(store.observe(other, statement.id).first())
        assertNull(store.findByFingerprint(other, statement.statementFingerprint))
        assertTrue(store.observePending(other).first().isEmpty())
        assertFalse(store.setCard(other, statement.id, null))
        assertFalse(store.setLineSelected(other, line.id, false))
        assertFalse(store.editLine(other, line.id, edit()))
        store.setAllSelected(other, statement.id, false)
        assertFalse(store.markLineRegistered(other, line.id, "movement"))
        assertFalse(store.discard(other, statement.id))
        assertFalse(store.complete(other, statement.id))
        assertEquals(0, store.purgeExpired(other, Long.MAX_VALUE / 2))
        assertTrue(store.observe(owner, statement.id).first()!!.lines.single().selected)
    }

    @Test fun `edits restrict structural kinds and completed rows cannot mutate`() = runBlocking {
        val statement = create()
        val line = store.observe(owner, statement.id).first()!!.lines.single()
        assertFalse(store.editLine(owner, line.id, edit(StatementLineKind.CARD_PAYMENT)))
        assertTrue(store.editLine(owner, line.id, edit(StatementLineKind.REFUND)))
        assertFalse(store.complete(owner, statement.id))
        assertTrue(store.markLineRegistered(owner, line.id, "movement"))
        assertTrue(store.complete(owner, statement.id))
        assertEquals(StatementImportLineStatus.REGISTERED, store.observe(owner, statement.id).first()!!.lines.single().status)
        assertFalse(store.setCard(owner, statement.id, null))
        assertFalse(store.setLineSelected(owner, line.id, true))
        assertFalse(store.editLine(owner, line.id, edit()))
        assertFalse(store.markLineRegistered(owner, line.id, "another"))
    }

    @Test fun `select all only affects pending lines and completion excludes unselected`() = runBlocking {
        val statement = create(lines = listOf(importLine("one"), importLine("two")))
        val lines = store.observe(owner, statement.id).first()!!.lines
        store.markLineRegistered(owner, lines.first().id, "movement")
        store.setAllSelected(owner, statement.id, true)
        assertFalse(store.observe(owner, statement.id).first()!!.lines.first().selected)
        store.setAllSelected(owner, statement.id, false)
        assertTrue(store.complete(owner, statement.id))
        assertEquals(listOf(StatementImportLineStatus.REGISTERED, StatementImportLineStatus.EXCLUDED),
            store.observe(owner, statement.id).first()!!.lines.map { it.status })
        assertTrue(store.observePending(owner).first().isEmpty())
    }

    @Test fun `purge expires pending completed and discarded with cascade and preserves recent other owner`() = runBlocking {
        val pending = create("pending")
        val discarded = create("discarded")
        val completed = create("completed")
        assertTrue(store.discard(owner, discarded.id))
        store.setAllSelected(owner, completed.id, false)
        store.complete(owner, completed.id)
        val foreign = store.create(other, parsedStatement(), null, emptyList()).statement
        assertEquals(0, store.purgeExpired(owner, pending.expiresAt - 1))
        assertEquals(3, store.purgeExpired(owner, pending.expiresAt))
        assertNull(store.observe(owner, pending.id).first())
        assertTrue(database.statementImportDao().observeLines(owner, discarded.id).first().isEmpty())
        assertNotNull(store.findByFingerprint(other, foreign.statementFingerprint))
    }

    @Test fun `matching filters archived debit and foreign cards and validates chosen card`() = runBlocking {
        for (card in listOf(card("credit"), card("archived").copy(isArchived = true), card("debit").copy(type = "DEBIT"), card("foreign").copy(userId = other))) {
            database.cardDao().insert(card)
        }
        val matching = RoomStatementImportMatching(database.cardDao(), database.statementImportMatchingDao())
        assertEquals(listOf("credit"), matching.activeCreditCards(owner).map { it.id })
        val statement = create()
        assertFalse(store.setCard(owner, statement.id, "archived"))
        assertFalse(store.setCard(owner, statement.id, "foreign"))
        assertFalse(store.setCard(owner, statement.id, "debit"))
        assertTrue(store.setCard(owner, statement.id, "credit"))
    }

    @Test fun `duplicates include both endpoints of two days and exclude third day foreign cards currencies inactive`() = runBlocking {
        val matching = RoomStatementImportMatching(database.cardDao(), database.statementImportMatchingDao())
        for (offset in listOf(-3L, -2L, 0L, 2L, 3L)) {
            val at = statementDate.plusDays(offset).atTime(23, 59).atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli()
            database.movementDao().insertTransactions(listOf(transaction("tx$offset", at, amount = 1000 + offset)))
            assertEquals(offset in -2L..2L, matching.hasDuplicate(owner, "card", 1000 + offset, Currency.PEN, statementDate))
        }
        val at = statementDate.atStartOfDay(ZoneId.of("America/Lima")).toInstant().toEpochMilli()
        database.movementDao().insertTransactions(listOf(
            transaction("other-owner", at, 2000).copy(userId = other),
            transaction("other-card", at, 2001).copy(cardId = "different"),
            transaction("inactive", at, 2002).copy(status = "DELETED"),
            transaction("usd", at, 2003).copy(currencyCode = "USD"),
        ))
        for (amount in 2000L..2003L) assertFalse(matching.hasDuplicate(owner, "card", amount, Currency.PEN, statementDate))
        assertTrue(matching.hasDuplicate(owner, "card", 2003, Currency.USD, statementDate))
        database.financialMovementDao().insertRaw(FinancialMovementEntity("legacy", "op", 0, owner, "CREDIT_PURCHASE", 3000,
            "USD", cardId = "card", effectiveAt = at * 1000, createdAt = at * 1000))
        assertTrue(matching.hasDuplicate(owner, "card", 3000, Currency.USD, statementDate))
    }

    @Test fun `line edits reject invalid description or amount`() {
        for (block in listOf<() -> Unit>(
            { edit().copy(description = "x".repeat(121)) }, { edit().copy(description = " ") }, { edit().copy(amountMinor = 0) },
            { edit().copy(kind = StatementLineKind.INSTALLMENT_MONTHLY_CHARGE) },
        )) {
            try { block(); fail("invalid edit must fail") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `migration SQL matches generated Room schema and preserves financial history`() = runBlocking {
        database.movementDao().insertTransactions(listOf(transaction("existing", 1000, 1000)))
        val sql = database.openHelper.writableDatabase
        val tables = listOf("statement_imports", "statement_import_lines")
        val expected = tables.associateWith { TableInfo.read(sql, it) }
        sql.execSQL("DROP TABLE statement_import_lines")
        sql.execSQL("DROP TABLE statement_imports")
        MIGRATION_29_30.migrate(sql)
        for (table in tables) assertEquals(expected.getValue(table), TableInfo.read(sql, table))
        sql.query("SELECT COUNT(*) FROM transactions WHERE id='existing'").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
    }

    private fun transaction(id: String, at: Long, amount: Long) = TransactionEntity(id, owner, "EXPENSE", amount,
        "PEN", occurredAt = at, cardId = "card", operationKind = "CARD_PURCHASE")
    private fun card(id: String) = CardEntity(id, owner, "op-$id", type = "CREDIT", currency = "PEN", network = "VISA",
        issuer = "BCP", lastFourDigits = "1234", createdAt = now, updatedAt = now)
    private fun edit(kind: StatementLineKind = StatementLineKind.PURCHASE) = StatementImportLineEdit("Edited", 1234, statementDate, "category", kind)
}
