package com.kipu.app.feature.statements

import android.net.Uri
import com.kipu.app.feature.statements.data.PdfBoxStatementDocumentReader
import com.kipu.app.feature.statements.domain.*
import com.kipu.app.feature.categories.domain.MerchantTextMatcher
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BcpSavingsLocalStatementTest {
    @Test fun `real local PDFs report merchant suggestion coverage and examples`() = runBlocking {
        val directory = sequenceOf(File("docs/EECC"), File("../docs/EECC")).firstOrNull { it.isDirectory }
        assumeTrue("Private statement samples are optional", directory != null)
        val files = directory!!.listFiles { f -> f.extension.equals("pdf", true) }.orEmpty()
        assumeTrue(files.isNotEmpty())
        val context = RuntimeEnvironment.getApplication()
        val reader = PdfBoxStatementDocumentReader(context)
        val catalog = listOf("Rappi", "Tambo", "Steam", "Betano", "Conamu", "Royal Plaza").map {
            MerchantCatalogEntry(MerchantId(it.lowercase()), it, it.lowercase())
        }
        var total = 0; var suggested = 0
        files.forEachIndexed { index, file ->
            val uri = Uri.parse("content://statements/matcher/$index")
            shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { file.inputStream() }
            val read = reader.read(DocumentRef(uri.toString())) as? StatementReadResult.Opened ?: return@forEachIndexed
            val parser = listOf(BcpSavingsStatementParser(), BcpVisaStatementParser()).firstOrNull { it.canParse(read.pages) } ?: return@forEachIndexed
            val parsed = parser.parse(read.pages) as? StatementParseResult.Parsed ?: return@forEachIndexed
            val matches = parsed.statement.lines.mapNotNull { line -> MerchantTextMatcher.suggest(line.description, catalog)?.let { line.description to it.name } }
            total += parsed.statement.lines.size; suggested += matches.size
            println("Merchant suggestions sample ${index + 1}: ${matches.size}/${parsed.statement.lines.size}; examples=${matches.take(6)}")
        }
        assumeTrue("No readable statement samples", total > 0)
        println("Merchant suggestions TOTAL: $suggested/$total (reference catalog: ${catalog.size} merchants)")
        assertTrue(suggested in 0..total)
    }

    @Test fun `local account statements reconcile and have consistent cycles`() = runBlocking {
        val directory = sequenceOf(File("docs/EECC"), File("../docs/EECC")).firstOrNull { it.isDirectory }
        assumeTrue(directory != null)
        val files = directory!!.listFiles { f -> f.extension.equals("pdf", true) }.orEmpty().sortedBy { it.name }
        assumeTrue(files.isNotEmpty())
        val context = RuntimeEnvironment.getApplication()
        val reader = PdfBoxStatementDocumentReader(context)
        val parser = BcpSavingsStatementParser()
        val results = mutableListOf<ParsedStatement>()
        files.forEachIndexed { index, file ->
            val uri = Uri.parse("content://statements/savings/$index")
            shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { file.inputStream() }
            val read = reader.read(DocumentRef(uri.toString())) as StatementReadResult.Opened
            assertTrue("sample ${index + 1} is not a savings statement", parser.canParse(read.pages))
            assertFalse(BcpVisaStatementParser().canParse(read.pages))
            val parsed = parser.parse(read.pages)
            assertTrue("sample ${index + 1}: $parsed", parsed is StatementParseResult.Parsed)
            val statement = (parsed as StatementParseResult.Parsed).statement
            val totals = statement.totals.values.single()
            println("Savings sample ${index + 1} (${file.name}): lines=${statement.lines.size} debits=${totals.accountDebitsMinor} credits=${totals.accountCreditsMinor} previous=${totals.previousBalanceMinor} closing=${totals.closingBalanceMinor} reconciliation=${statement.reconciliation.values.single()}")
            assertEquals(StatementSource.DEPOSIT_ACCOUNT, statement.source)
            assertTrue("sample ${index + 1} mismatch: ${statement.reconciliation}", statement.reconciliation.values.single().matches)
            results += statement
        }
        assumeTrue(results.isNotEmpty())
        results.groupBy { it.cycleStart }.values.forEach { group -> assertEquals(1, group.map { it.fingerprint }.distinct().size) }
        results.distinctBy { it.cycleStart }.sortedBy { it.cycleStart }.zipWithNext().forEach { (a, b) ->
            if (a.cycleEnd.plusDays(1) == b.cycleStart) assertEquals(a.totals.values.single().closingBalanceMinor, b.totals.values.single().previousBalanceMinor)
        }
    }
}
