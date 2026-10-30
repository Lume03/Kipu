package com.kipu.app.feature.statements

import android.net.Uri
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.statements.domain.*
import io.mockk.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ImportCardStatementTest {
    private val uri = mockk<Uri>()
    private val store = mockk<StatementImportStore>()
    private val reader = mockk<StatementDocumentReader>()
    private val matching = mockk<StatementImportMatching>()
    private val parser = mockk<StatementParser>()
    private var premium = true
    private val useCase = ImportCardStatement(StatementImportAccess { premium }, reader,
        StatementParserRegistry(setOf(parser)), store, matching, Clock.fixed(Instant.ofEpochMilli(5000), ZoneOffset.UTC))

    init {
        coEvery { store.purgeExpired("owner", 5000) } returns 0
        coEvery { store.findByFingerprint(any(), any()) } returns null
        coEvery { reader.read(uri, any()) } returns StatementReadResult.Opened(emptyList())
        every { parser.canParse(any()) } returns true
        every { parser.parse(any()) } returns StatementParseResult.Parsed(parsedStatement())
        coEvery { matching.activeCreditCards(any()) } returns emptyList()
        coEvery { matching.hasDuplicate(any(), any(), any(), any(), any()) } returns false
        coEvery { store.create(any(), any(), any(), any()) } returns StatementImportCreation(storedStatement(), true)
    }

    private suspend fun invoke(consented: Boolean = true): StatementImportResult {
        val password = "secret".toCharArray()
        return useCase("owner", uri, consented, password).also { assertTrue(password.all { it == '\u0000' }) }
    }

    @Test fun `consent precedes access and never purges or opens`() = runTest {
        premium = false
        assertEquals(StatementImportResult.ConsentRequired, invoke(false))
        coVerify(exactly = 0) { store.purgeExpired(any(), any()); reader.read(any(), any()) }
    }

    @Test fun `premium precedes purge and opening`() = runTest {
        premium = false
        assertEquals(StatementImportResult.PremiumRequired, invoke())
        coVerify(exactly = 0) { store.purgeExpired(any(), any()); reader.read(any(), any()) }
    }

    @Test fun `reader results map to actionable outcomes and erase password`() = runTest {
        for ((read, result) in listOf(
            StatementReadResult.PasswordRequired to StatementImportResult.PasswordRequired,
            StatementReadResult.WrongPassword to StatementImportResult.WrongPassword,
            StatementReadResult.NoExtractableText to StatementImportResult.NoExtractableText,
            StatementReadResult.Unreadable to StatementImportResult.Invalid,
        )) {
            coEvery { reader.read(any(), any()) } returns read
            assertEquals(result, invoke())
        }
        coVerify(exactly = 0) { store.create(any(), any(), any(), any()) }
    }

    @Test fun `unsupported and invalid parse do not persist`() = runTest {
        every { parser.parse(any()) } returns StatementParseResult.Unsupported
        assertEquals(StatementImportResult.Unsupported, invoke())
        every { parser.parse(any()) } returns StatementParseResult.InvalidStatement
        assertEquals(StatementImportResult.Invalid, invoke())
        coVerify(exactly = 0) { store.create(any(), any(), any(), any()) }
    }

    @Test fun `reimport returns pending or completed id before card lookup`() = runTest {
        for (status in listOf(StatementImportStatus.PENDING, StatementImportStatus.COMPLETED)) {
            coEvery { store.findByFingerprint("owner", "statement") } returns storedStatement(status)
            assertEquals(StatementImportResult.AlreadyImported("import", status == StatementImportStatus.COMPLETED), invoke())
        }
        coVerify(exactly = 0) { matching.activeCreditCards(any()); store.create(any(), any(), any(), any()) }
    }

    @Test fun `no card persists with null match and one card matches regardless issuer`() = runTest {
        assertEquals(StatementImportResult.Imported("import"), invoke())
        coVerify { store.create("owner", any(), null, any()) }
        coEvery { matching.activeCreditCards("owner") } returns listOf(card("only", "OTHER"))
        invoke()
        coVerify { store.create("owner", any(), "only", any()) }
    }

    @Test fun `multiple cards use unique BCP issuer or stay unresolved`() = runTest {
        coEvery { matching.activeCreditCards("owner") } returns listOf(card("other", "OTHER"), card("bcp"))
        invoke()
        coVerify { store.create("owner", any(), "bcp", any()) }
        coEvery { matching.activeCreditCards("owner") } returns listOf(card("one"), card("two"))
        invoke()
        coVerify { store.create("owner", any(), null, any()) }
        coEvery { matching.activeCreditCards("owner") } returns listOf(card("different").copy(lastFour = "9999"))
        invoke()
        coVerify(exactly = 2) { store.create("owner", any(), null, any()) }
    }

    @Test fun `duplicates mismatched currency payments and points stay unselected and monthly charges excluded`() = runTest {
        val lines = listOf(importLine(), importLine("usd", Currency.USD),
            importLine("payment", kind = StatementLineKind.CARD_PAYMENT),
            importLine("points", kind = StatementLineKind.POINTS_REDEMPTION),
            importLine("monthly", kind = StatementLineKind.INSTALLMENT_MONTHLY_CHARGE))
        every { parser.parse(any()) } returns StatementParseResult.Parsed(parsedStatement(lines))
        coEvery { matching.activeCreditCards("owner") } returns listOf(card("card"))
        coEvery { matching.hasDuplicate("owner", "card", 1000, Currency.PEN, statementDate) } returns true
        val captured = slot<List<StatementImportCandidate>>()
        coEvery { store.create(any(), any(), any(), capture(captured)) } returns StatementImportCreation(storedStatement(), true)
        invoke()
        assertEquals(4, captured.captured.size)
        assertTrue(captured.captured.none { it.selected })
        assertTrue(captured.captured.first().possibleDuplicate)
        assertFalse(captured.captured[1].possibleDuplicate)
    }

    @Test fun `USD card accepts USD without duplicate`() = runTest {
        every { parser.parse(any()) } returns StatementParseResult.Parsed(parsedStatement(listOf(importLine(currency = Currency.USD))))
        coEvery { matching.activeCreditCards("owner") } returns listOf(card("usd").copy(currency = Currency.USD))
        invoke()
        coVerify { store.create("owner", any(), "usd", match { it.single().selected }) }
        coVerifyOrder { store.purgeExpired("owner", 5000); reader.read(uri, any()); store.create(any(), any(), any(), any()) }
    }

    @Test fun `concurrent reimport from atomic store is returned as existing`() = runTest {
        coEvery { store.create(any(), any(), any(), any()) } returns StatementImportCreation(storedStatement(), false)
        assertEquals(StatementImportResult.AlreadyImported("import", false), invoke())
    }

    @Test fun `password erased even when a dependency throws or cancels`() = runTest {
        for (failure in listOf(IllegalStateException("synthetic"), CancellationException("cancelled"))) {
            coEvery { store.purgeExpired(any(), any()) } throws failure
            val password = "secret".toCharArray()
            try {
                useCase("owner", uri, true, password)
                fail("must propagate")
            } catch (actual: Exception) { assertSame(failure, actual) }
            assertTrue(password.all { it == '\u0000' })
        }
    }

    private fun card(id: String, issuer: String = "BCP") = StatementImportCard(id, issuer, "1234", Currency.PEN)
}
