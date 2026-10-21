package com.kipu.app.feature.receipts.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.kipu.app.feature.receipts.domain.YapeReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/** External fixtures are pushed by QA to app-specific external storage; no image is a git asset. */
class YapeReceiptOcrInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun bundledMlKitRecognizesSyntheticYapeLikeVoucherLocally() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 1500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
        listOf("¡Yapeaste!", "S/ 46.70", "María Test", "20 oct. 2026 | 12:40 a. m.", "Nro. de operación", "912345678", "CÓDIGO DE SEGURIDAD", "123")
            .forEachIndexed { index, line -> canvas.drawText(line, 60f, 120f + index * 145f, paint) }
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()

        val recognized = AndroidReceiptImageRecognizer(context).recognizeBytes(output.toByteArray())
        assertNotNull(recognized)
        val fields = YapeReceiptParser().parse(requireNotNull(recognized).text)
        assertEquals(false, fields.isIncome)
        assertEquals(4670L, fields.amountMinor)
        assertNotNull(fields.occurredAt)
        assertNotNull(fields.reference)
        assertTrue(fields.reference != "123")
        assertTrue(fields.recipient != null)
    }

}
