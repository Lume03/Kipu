package com.kipu.app.feature.statements.data

import android.content.Context
import android.net.Uri
import com.kipu.app.feature.statements.domain.StatementDocumentReader
import com.kipu.app.feature.statements.domain.StatementPage
import com.kipu.app.feature.statements.domain.StatementReadResult
import com.kipu.app.feature.statements.domain.StatementWord
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PdfBoxStatementDocumentReader @Inject constructor(@param:ApplicationContext private val context: Context) : StatementDocumentReader {
    private val initialized by lazy { PDFBoxResourceLoader.init(context.applicationContext) }

    override suspend fun read(uri: Uri, password: CharArray?): StatementReadResult = try {
        withContext(Dispatchers.IO) {
            try {
                initialized
                val stream = context.contentResolver.openInputStream(uri) ?: return@withContext StatementReadResult.Unreadable
                stream.use { input ->
                    // Explicit main-memory mode prevents PDFBox scratch files containing source data.
                    PDDocument.load(input, password?.concatToString().orEmpty(), MemoryUsageSetting.setupMainMemoryOnly()).use { document ->
                        val pages = document.pages.mapIndexed { index, page ->
                            val stripper = WordStripper().apply { startPage = index + 1; endPage = index + 1 }
                            stripper.getText(document)
                            val rotated = page.rotation % 180 != 0
                            StatementPage(index, if (rotated) page.cropBox.height else page.cropBox.width,
                                if (rotated) page.cropBox.width else page.cropBox.height, stripper.words.toList())
                        }
                        if (pages.none { it.words.isNotEmpty() }) StatementReadResult.NoExtractableText else StatementReadResult.Opened(pages)
                    }
                }
            } catch (_: InvalidPasswordException) {
                if (password == null) StatementReadResult.PasswordRequired else StatementReadResult.WrongPassword
            } catch (_: IOException) {
                StatementReadResult.Unreadable
            } catch (_: SecurityException) {
                StatementReadResult.Unreadable
            } catch (_: IllegalArgumentException) {
                StatementReadResult.Unreadable
            }
        }
    } finally {
        password?.fill('\u0000')
    }

    private class WordStripper : PDFTextStripper() {
        val words = mutableListOf<StatementWord>()

        init { sortByPosition = true }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val current = mutableListOf<TextPosition>()
            fun flush() {
                if (current.isEmpty()) return
                words += StatementWord(current.joinToString("") { it.unicode },
                    current.minOf { it.xDirAdj }, current.minOf { it.yDirAdj - it.heightDir },
                    current.maxOf { it.xDirAdj + it.widthDirAdj }, current.maxOf { it.yDirAdj })
                current.clear()
            }
            for (position in textPositions) {
                if (position.unicode.isBlank()) {
                    flush()
                    continue
                }
                val previous = current.lastOrNull()
                if (previous != null && position.xDirAdj - (previous.xDirAdj + previous.widthDirAdj) >
                    maxOf(previous.widthOfSpace * .5f, 1f)) flush()
                current += position
            }
            flush()
        }
    }
}
