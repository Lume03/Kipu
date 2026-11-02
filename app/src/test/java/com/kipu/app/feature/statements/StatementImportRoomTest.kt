package com.kipu.app.feature.statements

import android.content.Context
import androidx.room.Room
import androidx.room.util.TableInfo
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.database.MIGRATION_32_33
import io.mockk.*
import com.kipu.app.core.database.MIGRATION_29_30
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.core.finance.domain.model.*
import kotlinx.coroutines.flow.flowOf
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
    private val instruments = mockk<com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments>()
    private val debitMatching = mockk<StatementImportMatching>()

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java)
            .allowMainThreadQueries().build()
        store = RoomStatementImportStore(database, database.statementImportDao(), Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC), debitMatching)
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

    @Test fun `discarded fingerprints create new imports and cascade old lines for both statement sources`() = runBlocking {
        for (source in StatementSource.entries) {
            val parsed = parsedStatement(fingerprint = "discard-$source").copy(source = source)
            val candidates = parsed.lines.map { StatementImportCandidate(it, true, false) }
            val old = store.create(owner, parsed, null, candidates).statement
            assertTrue(store.discard(owner, old.id))
            assertNull(store.findByFingerprint(owner, parsed.fingerprint))
            val new = store.create(owner, parsed, null, candidates)
            assertTrue(new.created)
            assertNotEquals(old.id, new.statement.id)
            assertNull(store.observe(owner, old.id).first())
            assertTrue(database.statementImportDao().observeLines(owner, old.id).first().isEmpty())
            assertEquals(candidates.size, store.observe(owner, new.statement.id).first()!!.lines.size)
        }
    }

    @Test fun `manual card choice replaces discarded duplicate and preserves pending duplicate`() = runBlocking {
        database.cardDao().insert(card("chosen"))
        val discarded = create()
        assertTrue(store.setCard(owner, discarded.id, "chosen"))
        assertTrue(store.discard(owner, discarded.id))
        val fresh = create()
        assertTrue(store.setCard(owner, fresh.id, "chosen"))
        assertNull(store.observe(owner, discarded.id).first())
        assertTrue(database.statementImportDao().observeLines(owner, discarded.id).first().isEmpty())
        val pending = create()
        assertFalse(store.setCard(owner, pending.id, "chosen"))
    }

    @Test fun `replacement rollback preserves discarded row and lines if insertion fails`() = runBlocking {
        val old = create("atomic")
        assertTrue(store.discard(owner, old.id))
        try { create("atomic", listOf(importLine("same"), importLine("same"))); fail("must fail") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(StatementImportStatus.DISCARDED, store.observe(owner, old.id).first()!!.statement.status)
        assertEquals(1, database.statementImportDao().observeLines(owner, old.id).first().size)
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

    @Test fun `expired draft is hidden and purged with its lines`() = runBlocking {
        val statement = create()
        val expiredStore = RoomStatementImportStore(database, database.statementImportDao(),
            Clock.fixed(Instant.ofEpochMilli(statement.expiresAt), ZoneOffset.UTC), debitMatching)
        assertTrue(expiredStore.observePending(owner).first().isEmpty())
        assertNull(expiredStore.observe(owner, statement.id).first())
        assertEquals(1, expiredStore.purgeExpired(owner, statement.expiresAt))
        assertTrue(database.statementImportDao().observeLines(owner, statement.id).first().isEmpty())
    }

    @Test fun `matching filters archived debit and foreign cards and validates chosen card`() = runBlocking {
        for (card in listOf(card("credit"), card("archived").copy(isArchived = true), card("debit").copy(type = "DEBIT"), card("foreign").copy(userId = other))) {
            database.cardDao().insert(card)
        }
        val matching = RoomStatementImportMatching(database.cardDao(), database.statementImportMatchingDao(), instruments)
        assertEquals(listOf("credit"), matching.activeCreditCards(owner).map { it.id })
        val statement = create()
        assertFalse(store.setCard(owner, statement.id, "archived"))
        assertFalse(store.setCard(owner, statement.id, "foreign"))
        assertFalse(store.setCard(owner, statement.id, "debit"))
        assertTrue(store.setCard(owner, statement.id, "credit"))
    }

    @Test fun `manual card choice scopes deduplication when last four are shared`() = runBlocking {
        database.cardDao().insert(card("one"))
        database.cardDao().insert(card("two"))
        val first = create()
        assertTrue(store.setCard(owner, first.id, "one"))
        val second = create()
        assertTrue(store.setCard(owner, second.id, "two"))
        assertNotEquals(store.observe(owner, first.id).first()!!.statement.statementFingerprint,
            store.observe(owner, second.id).first()!!.statement.statementFingerprint)
        val replay = create()
        assertFalse(store.setCard(owner, replay.id, "one"))
        assertNull(store.observe(owner, replay.id).first()!!.statement.cardId)
    }

    @Test fun `duplicates include both endpoints of two days and exclude third day foreign cards currencies inactive`() = runBlocking {
        val matching = RoomStatementImportMatching(database.cardDao(), database.statementImportMatchingDao(), instruments)
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
        MIGRATION_32_33.migrate(sql)
        for (table in tables) assertEquals(expected.getValue(table), TableInfo.read(sql, table))
        sql.query("SELECT COUNT(*) FROM transactions WHERE id='existing'").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
    }

    @Test fun `deposit persists source rejects wrong card and currency keeps fingerprint and unselects duplicates`() = runBlocking {
        database.cardDao().insert(card("credit"))
        coEvery { debitMatching.activeDebitCards(owner) } returns listOf(
            StatementImportDebitCard("debit", "BCP", "Savings", "5678", Currency.PEN, "account"),
            StatementImportDebitCard("usd", "BCP", "Dollars", "5678", Currency.USD, "usd-account"))
        coEvery { debitMatching.hasAccountDuplicate(owner, "account", any(), any(), any(), any()) } returns true
        val parsed = parsedStatement(listOf(importLine(kind = StatementLineKind.ACCOUNT_DEBIT))).copy(
            source = StatementSource.DEPOSIT_ACCOUNT, accountReference = "5678", cardLastFour = "")
        val saved = store.create(owner, parsed, null, parsed.lines.map { StatementImportCandidate(it, true, false) }).statement
        assertEquals(StatementSource.DEPOSIT_ACCOUNT, saved.source)
        assertEquals("5678", saved.accountReference)
        assertFalse(store.setCard(owner, saved.id, "credit"))
        assertFalse(store.setCard(owner, saved.id, "usd"))
        assertTrue(store.setCard(owner, saved.id, "debit"))
        val result = store.observe(owner, saved.id).first()!!
        assertEquals(parsed.fingerprint, result.statement.statementFingerprint)
        assertTrue(result.lines.single().possibleDuplicate)
        assertFalse(result.lines.single().selected)
        assertFalse(store.create(owner, parsed, null, emptyList()).created)
    }

    @Test fun `account totals and own transfer provenance persist independently of selection and edits`() = runBlocking {
        val amounts = mapOf(
            Currency.PEN to StatementCurrencyTotals(10000, null, 0, null, 1000, 2000, 11000),
            Currency.USD to StatementCurrencyTotals(5000, null, 0, null, 500, 1000, 5500))
        val line = importLine(kind = StatementLineKind.ACCOUNT_DEBIT).copy(possibleOwnTransfer = true, defaultSelected = false)
        val parsed = parsedStatement(listOf(line)).copy(source = StatementSource.DEPOSIT_ACCOUNT, totals = amounts)
        val statement = store.create(owner, parsed, null, listOf(StatementImportCandidate(line, false, false))).statement
        assertEquals(StatementAccountTotals(10000, 1000, 2000, 11000), statement.accountTotals[Currency.PEN])
        assertEquals(StatementAccountTotals(5000, 500, 1000, 5500), statement.accountTotals[Currency.USD])
        val saved = store.observe(owner, statement.id).first()!!
        assertEquals(statement.accountTotals, saved.statement.accountTotals)
        assertTrue(saved.lines.single().possibleOwnTransfer)
        assertTrue(store.setLineSelected(owner, saved.lines.single().id, true))
        assertTrue(store.editLine(owner, saved.lines.single().id, edit(StatementLineKind.ACCOUNT_DEBIT)))
        database.statementImportDao().setDuplicate(owner, saved.lines.single().id, true)
        val updated = store.observe(owner, statement.id).first()!!
        assertTrue(updated.lines.single().possibleOwnTransfer)
        assertFalse(updated.lines.single().selected)
        assertEquals(statement.accountTotals, updated.statement.accountTotals)
        val legacy = create("credit-defaults")
        assertTrue(legacy.accountTotals.isEmpty())
        assertFalse(store.observe(owner, legacy.id).first()!!.lines.single().possibleOwnTransfer)
    }

    @Test fun `debit matching requires available card and linked account`() = runBlocking {
        val accountId = AccountId(UUID.randomUUID().toString())
        val userId = UserId(owner)
        val account = Account(accountId, userId, "Savings", AccountType.BANK, Currency.PEN,
            initialBalance = Money(0, Currency.PEN), openedAt = Instant.EPOCH)
        val debit = DebitCard(CardId(UUID.randomUUID().toString()), userId, issuer = "BCP", network = CardNetwork.VISA,
            lastFourDigits = "5678", currency = Currency.PEN, linkedAccountId = accountId)
        every { instruments.observeAccounts(true) } returns flowOf(listOf(account))
        every { instruments.observeCards(true) } returns flowOf(listOf(debit,
            debit.copy(id = CardId(UUID.randomUUID().toString()), isPlanLocked = true),
            debit.copy(id = CardId(UUID.randomUUID().toString()), isArchived = true),
            debit.copy(id = CardId(UUID.randomUUID().toString()), linkedAccountId = AccountId(UUID.randomUUID().toString()))))
        val matching = RoomStatementImportMatching(database.cardDao(), database.statementImportMatchingDao(), instruments)
        assertEquals(listOf(debit.id.value), matching.activeDebitCards(owner).map { it.id })
        every { instruments.observeAccounts(true) } returns flowOf(listOf(account.copy(isPlanLocked = true)))
        assertTrue(matching.activeDebitCards(owner).isEmpty())
    }

    @Test fun `account duplicate matching distinguishes day type currency owner and account`() = runBlocking {
        val matching = RoomStatementImportMatching(database.cardDao(), database.statementImportMatchingDao(), instruments)
        val at = statementDate.atTime(12, 0).atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli()
        // No account FK is needed for this projection-only fixture.
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys=OFF")
        database.movementDao().insertTransactions(listOf(transaction("expense", at, 1000).copy(sourceAccountId = "account"),
            transaction("income", at, 2000).copy(sourceAccountId = "account", type = "INCOME")))
        assertTrue(matching.hasAccountDuplicate(owner, "account", 1000, Currency.PEN, statementDate, StatementLineKind.ACCOUNT_DEBIT))
        assertTrue(matching.hasAccountDuplicate(owner, "account", 2000, Currency.PEN, statementDate, StatementLineKind.ACCOUNT_CREDIT))
        assertFalse(matching.hasAccountDuplicate(owner, "account", 1000, Currency.PEN, statementDate, StatementLineKind.ACCOUNT_CREDIT))
        assertFalse(matching.hasAccountDuplicate(owner, "account", 1000, Currency.USD, statementDate, StatementLineKind.ACCOUNT_DEBIT))
        assertFalse(matching.hasAccountDuplicate(other, "account", 1000, Currency.PEN, statementDate, StatementLineKind.ACCOUNT_DEBIT))
        assertFalse(matching.hasAccountDuplicate(owner, "other", 1000, Currency.PEN, statementDate, StatementLineKind.ACCOUNT_DEBIT))
        assertFalse(matching.hasAccountDuplicate(owner, "account", 1000, Currency.PEN, statementDate.plusDays(1), StatementLineKind.ACCOUNT_DEBIT))
    }

    private fun transaction(id: String, at: Long, amount: Long) = TransactionEntity(id, owner, "EXPENSE", amount,
        "PEN", occurredAt = at, cardId = "card", operationKind = "CARD_PURCHASE")
    private fun card(id: String) = CardEntity(id, owner, "op-$id", type = "CREDIT", currency = "PEN", network = "VISA",
        issuer = "BCP", lastFourDigits = "1234", createdAt = now, updatedAt = now)
    private fun edit(kind: StatementLineKind = StatementLineKind.PURCHASE) = StatementImportLineEdit("Edited", 1234, statementDate, "category", kind)
}
