package com.kipu.app.navigation

import android.content.*
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.receipts.data.AndroidReceiptImageRecognizer
import com.kipu.app.feature.receipts.domain.YapeReceiptParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReceiptShareUriGrantTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun fixture(action: String): Uri {
        val latch = CountDownLatch(1)
        var result: String? = null
        instrumentation.targetContext.sendOrderedBroadcast(
            Intent(action).setComponent(ComponentName(instrumentation.context.packageName, SyntheticShareGrantReceiver::class.java.name)),
            null, object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { result = resultData; latch.countDown() }
            }, null, 0, null, null)
        check(latch.await(15, TimeUnit.SECONDS)) { "Synthetic sender did not reply" }
        return Uri.parse(requireNotNull(result))
    }

    @Test fun separateUidGrantMakesSendUriReadableAndRevocationClosesAccess() = runBlocking {
        val context = instrumentation.targetContext
        val uri = fixture("fixture.REVOKE")
        fun denied() {
            try { context.contentResolver.openInputStream(uri)?.close(); fail("Non-exported provider must deny access") }
            catch (_: SecurityException) { /* Android checks the grant across UIDs. */ }
        }
        denied()
        try {
            assertEquals(uri, fixture("fixture.GRANT"))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Synthetic voucher", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val sharedUri = requireNotNull(ReceiptShareIntent.imageUri(intent))
            val recognized = requireNotNull(AndroidReceiptImageRecognizer(context).recognize(sharedUri))
            assertEquals(1250L, YapeReceiptParser().parse(recognized.text).amountMinor)
        } finally { fixture("fixture.REVOKE") }
        denied()
    }
}
