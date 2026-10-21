package com.kipu.app.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.feature.receipts.data.AndroidReceiptImageRecognizer
import com.kipu.app.feature.receipts.domain.YapeReceiptParser
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/** Owner fixtures are adb-pushed locally, never bundled in an APK or committed to Git. */
class YapePrivateSamplesAcceptanceTest {
    @Test fun allEightOwnerSamplesProduceReviewableFieldsIncludingBothDirections() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "ocr-yape")
        val files = directory.listFiles()?.filter { it.extension.equals("png", ignoreCase = true) }.orEmpty().sortedBy { it.name }
        assumeTrue("Private fixtures are optional outside the owner's local QA run", files.isNotEmpty())
        assertEquals("Validate every supplied local fixture", 8, files.size)
        val recognizer = AndroidReceiptImageRecognizer(context)
        val parser = YapeReceiptParser()
        var incomes = 0
        var expenses = 0
        files.forEachIndexed { index, file ->
            val recognized = recognizer.recognizeBytes(file.readBytes())
            assertNotNull("Sample $index must be readable by bundled ML Kit", recognized)
            val fields = parser.parse(requireNotNull(recognized).text)
            assertTrue("Sample $index needs a positive amount", (fields.amountMinor ?: 0) > 0)
            assertNotNull("Sample $index needs a date and time", fields.occurredAt)
            assertNotNull("Sample $index needs a counterparty", fields.recipient)
            assertNotNull("Sample $index needs an operation reference", fields.reference)
            assertTrue("Sample $index must not use the security code as reference", fields.reference.orEmpty().length > 3)
            when (fields.isIncome) {
                true -> incomes++
                false -> expenses++
                null -> fail("Sample $index needs a recognized direction")
            }
        }
        assertTrue("The supplied set includes incoming and outgoing payments", incomes > 0 && expenses > 0)
    }
}
