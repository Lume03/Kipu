package com.kipu.app.feature.statements

import android.content.Context
import android.net.Uri
import com.kipu.app.feature.statements.data.PdfBoxStatementDocumentReader
import com.kipu.app.feature.statements.domain.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PdfBoxStatementDocumentReaderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun generatedPdf(text: String? = "Synthetic statement text", encrypted: Boolean = false): ByteArray {
        PDFBoxResourceLoader.init(context)
        return PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            if (text != null) PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText(text)
                stream.endText()
            }
            if (encrypted) document.protect(StandardProtectionPolicy("owner-synthetic", "secret-synthetic", AccessPermission()))
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
    }

    private fun register(bytes: ByteArray): Uri = Uri.parse("content://statements/synthetic").also {
        shadowOf(context.contentResolver).registerInputStreamSupplier(it) { bytes.inputStream() }
    }

    @Test fun `reads positions and clears password on successful opening`() = runBlocking {
        val password = "secret-synthetic".toCharArray()
        val result = PdfBoxStatementDocumentReader(context).read(register(generatedPdf(encrypted = true)), password)
        assertTrue(result is StatementReadResult.Opened)
        val words = (result as StatementReadResult.Opened).pages.single().words
        assertEquals(listOf("Synthetic", "statement", "text"), words.map { it.text })
        assertTrue(words.all { it.x1 > it.x0 && it.y1 > it.y0 })
        assertTrue(password.all { it == '\u0000' })
    }

    @Test fun `distinguishes absent and wrong passwords and wipes failed password`() = runBlocking {
        val reader = PdfBoxStatementDocumentReader(context)
        val uri = register(generatedPdf(encrypted = true))
        assertEquals(StatementReadResult.PasswordRequired, reader.read(uri, null))
        val wrong = "wrong".toCharArray()
        assertEquals(StatementReadResult.WrongPassword, reader.read(uri, wrong))
        assertTrue(wrong.all { it == '\u0000' })
    }

    @Test fun `handles empty and unreadable documents`() = runBlocking {
        val reader = PdfBoxStatementDocumentReader(context)
        assertEquals(StatementReadResult.NoExtractableText, reader.read(register(generatedPdf(null)), null))
        val password = charArrayOf('x')
        assertEquals(StatementReadResult.Unreadable, reader.read(register(byteArrayOf(1, 2, 3)), password))
        assertTrue(password.all { it == '\u0000' })
    }

    @Test fun `private local statements reconcile in both currencies`() = runBlocking {
        val directory = sequenceOf(File("docs/EECC"), File("../docs/EECC")).firstOrNull { it.isDirectory }
        assumeTrue("Local private samples are optional", directory != null)
        val files = directory!!.listFiles { file -> file.extension.equals("pdf", true) }.orEmpty().sortedBy { it.name }
        assumeTrue("No local private samples", files.isNotEmpty())
        val reader = PdfBoxStatementDocumentReader(context)
        val parser = BcpVisaStatementParser()
        files.forEachIndexed { index, file ->
            // Supplier streams the private fixture directly; no copies or extracted text are written.
            val uri = Uri.parse("content://statements/local/$index")
            shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { file.inputStream() }
            val read = reader.read(uri, null)
            assertTrue("Local sample ${index + 1}: opening failed", read is StatementReadResult.Opened)
            val parsed = parser.parse((read as StatementReadResult.Opened).pages)
            assertTrue("Local sample ${index + 1}: parsing failed", parsed is StatementParseResult.Parsed)
            val statement = (parsed as StatementParseResult.Parsed).statement
            val counts = statement.lines.groupingBy { it.kind }.eachCount()
            val status = statement.reconciliation.mapValues { it.value.matches }
            println("Local sample ${index + 1}: counts=$counts reconciliation=$status")
            assertTrue("Local sample ${index + 1}: reconciliation failed", statement.reconciliation.values.all { it.matches })
        }
    }
}
