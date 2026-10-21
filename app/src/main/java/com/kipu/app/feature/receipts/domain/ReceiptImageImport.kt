package com.kipu.app.feature.receipts.domain

import android.net.Uri
import javax.inject.Inject

sealed interface ReceiptImportResult {
    data class CandidateCreated(val candidate: ReceiptCandidate) : ReceiptImportResult
    data class Duplicate(val existingCandidateId: String) : ReceiptImportResult
    data object AccessDenied : ReceiptImportResult
    data object ConsentRequired : ReceiptImportResult
    data object Unreadable : ReceiptImportResult
}

fun interface ReceiptImageRecognizer {
    suspend fun recognize(uri: Uri): RecognizedReceiptText?
}

data class RecognizedReceiptText(val text: String, val imageFingerprint: String)

/** Authorization and explicit user consent are checked before the provider is opened. */
class ImportYapeReceipt @Inject constructor(
    private val access: ReceiptScanAccess,
    private val recognizer: ReceiptImageRecognizer,
    private val parser: YapeReceiptParser,
    private val store: ReceiptCandidateStore,
) {
    suspend operator fun invoke(ownerId: String, uri: Uri, userConsented: Boolean): ReceiptImportResult {
        if (!userConsented) return ReceiptImportResult.ConsentRequired
        if (!access.canScan(ownerId)) return ReceiptImportResult.AccessDenied
        val recognized = recognizer.recognize(uri) ?: return ReceiptImportResult.Unreadable
        val fields = parser.parse(recognized.text)
        return when (val result = store.import(ownerId, recognized.imageFingerprint, fields, fields.isIncome)) {
            is ReceiptCandidateSaveResult.Saved -> ReceiptImportResult.CandidateCreated(result.candidate)
            is ReceiptCandidateSaveResult.Duplicate -> ReceiptImportResult.Duplicate(result.existingCandidateId)
        }
    }
}


fun interface ReceiptScanAccess {
    suspend fun canScan(ownerId: String): Boolean
}
