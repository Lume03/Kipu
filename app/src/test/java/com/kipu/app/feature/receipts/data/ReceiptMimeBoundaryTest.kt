package com.kipu.app.feature.receipts.data

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import com.kipu.app.feature.receipts.domain.UnsupportedReceiptImageFormat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReceiptMimeBoundaryTest {
    @Test fun nonImagesAndUnknownTypesAreRejectedWithoutOpeningBytes() = runTest {
        for (mime in listOf("application/pdf", "text/plain", "application/octet-stream", null)) {
            val uri = mockk<Uri> { every { scheme } returns "content" }
            val resolver = mockk<ContentResolver> { every { getType(uri) } returns mime }
            val context = mockk<Context> { every { contentResolver } returns resolver }
            var rejected = false
            try { AndroidReceiptImageRecognizer(context).recognize(uri) }
            catch (_: UnsupportedReceiptImageFormat) { rejected = true }
            assertTrue("MIME $mime", rejected)
            verify(exactly = 0) { resolver.openInputStream(any()) }
        }
    }
}
