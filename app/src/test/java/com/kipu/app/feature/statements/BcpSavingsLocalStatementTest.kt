package com.kipu.app.feature.statements

import android.net.Uri
import com.kipu.app.feature.statements.data.PdfBoxStatementDocumentReader
import com.kipu.app.feature.statements.domain.*
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
