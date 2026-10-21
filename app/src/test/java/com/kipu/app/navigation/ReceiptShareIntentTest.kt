package com.kipu.app.navigation

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReceiptShareIntentTest {
    private val image = Uri.parse("content://receipts/receipt.png")

    @Test fun `accepts single shared image stream`() {
        assertEquals(image, ReceiptShareIntent.imageUri(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }))
    }

    @Test fun `accepts clip data fallback`() {
        assertEquals(image, ReceiptShareIntent.imageUri(Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            clipData = ClipData.newRawUri("receipt", image)
        }))
    }

    @Test fun `rejects files remote urls other content and multiple shares`() {
        listOf("file:///sdcard/receipt.jpg", "https://example.org/receipt.jpg").forEach { value ->
            assertNull(ReceiptShareIntent.imageUri(Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(value))
            }))
        }
        assertNull(ReceiptShareIntent.imageUri(Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/png" }))
        assertNull(ReceiptShareIntent.imageUri(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, image)
        }))
        assertNull(ReceiptShareIntent.imageUri(null))
    }
}
