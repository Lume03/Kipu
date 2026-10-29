package com.kipu.app.feature.receipts.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.feature.receipts.domain.ReceiptImageRecognizer
import com.kipu.app.feature.receipts.domain.RecognizedReceiptText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidReceiptImageRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReceiptImageRecognizer {
    override suspend fun recognize(uri: Uri): RecognizedReceiptText? = withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return@withContext unreadable("unsupported scheme")
        val bytes = readBounded(uri) ?: return@withContext unreadable("image not readable")
        recognizeBytes(bytes)
    }

    /** Public to enable instrumented ML Kit checks with adb-pushed, untracked fixtures. */
    suspend fun recognizeBytes(bytes: ByteArray): RecognizedReceiptText? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@withContext unreadable("size out of bounds")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_DIMENSION || bounds.outHeight !in 1..MAX_DIMENSION ||
            bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) return@withContext unreadable("dimensions out of bounds")
        val options = BitmapFactory.Options().apply {
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DECODE_SIDE) sampleSize *= 2
            inSampleSize = sampleSize
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return@withContext unreadable("decode failed")
        val bitmap = orient(decoded, runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL))
        try {
            val result = recognizeBitmap(bitmap)
            RecognizedReceiptText(readingOrder(result), bytes.sha256())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            unreadable("text recognition failed", error)
        } finally {
            bitmap.recycle()
        }
    }

    private fun orient(bitmap: android.graphics.Bitmap, orientation: Int): android.graphics.Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(270f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postScale(-1f, 1f); postRotate(90f) }
            }
        }
        if (matrix.isIdentity) return bitmap
        val oriented = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    /** ML Kit blocks are column-oriented. Rejoin visual rows so labels retain their values. */
    private fun readingOrder(text: com.google.mlkit.vision.text.Text): String {
        val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
            .sortedBy { it.boundingBox!!.centerY() }
        if (lines.isEmpty()) return text.text
        val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
        lines.forEach { line ->
            val box = requireNotNull(line.boundingBox)
            val row = rows.lastOrNull()
            val anchor = row?.firstOrNull()?.boundingBox
            if (row != null && anchor != null &&
                kotlin.math.abs(box.centerY() - anchor.centerY()) <= minOf(box.height(), anchor.height()) * 0.6f) {
                row.add(line)
            } else rows.add(mutableListOf(line))
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text } }
    }

    private fun readBounded(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_BYTES) return null
                out.write(buffer, 0, count)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        }
    } catch (error: Exception) {
        SecureLog.w(TAG, "Receipt image read failed: ${error.javaClass.simpleName}")
        null
    }

    private suspend fun recognizeBitmap(bitmap: android.graphics.Bitmap): com.google.mlkit.vision.text.Text =
        suspendCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text -> recognizer.close(); continuation.resume(text) }
                .addOnFailureListener { error -> recognizer.close(); continuation.resumeWithException(error) }
        }

    /** Logs only the failing stage and exception type; never image content or recognized text. */
    private fun unreadable(stage: String, error: Exception? = null): RecognizedReceiptText? {
        SecureLog.w(TAG, "Receipt OCR unreadable: $stage${error?.let { " (${it.javaClass.name}: ${it.message})" }.orEmpty()}")
        return null
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "ReceiptOcr"
        const val MAX_BYTES = 12 * 1024 * 1024
        const val MAX_DIMENSION = 8_192
        const val MAX_PIXELS = 40_000_000L
        const val MAX_DECODE_SIDE = 2_048
    }
}
