package com.kipu.app.feature.receipts.domain

import android.net.Uri
import com.kipu.app.feature.receipts.domain.YapeReceiptFields
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import io.mockk.mockk
import kotlin.test.assertIs

class ImportYapeReceiptTest {
    @Test fun consentIsRequiredBeforeAnyImageAccess() = runTest {
        var opened = false
        val useCase = importer(allowed = true, opened = { opened = true })
        assertIs<ReceiptImportResult.ConsentRequired>(useCase("owner", mockk<Uri>(), false))
        assertEquals(false, opened)
    }

    @Test fun deniedEntitlementDoesNotOpenUri() = runTest {
        var opened = false
        val useCase = importer(allowed = false, opened = { opened = true })
        assertIs<ReceiptImportResult.AccessDenied>(useCase("owner", mockk<Uri>(), true))
        assertEquals(false, opened)
    }

    private fun importer(allowed: Boolean, opened: () -> Unit) = ImportYapeReceipt(
        access = ReceiptScanAccess { allowed },
        recognizer = ReceiptImageRecognizer {
            opened()
            RecognizedReceiptText("temporary OCR text", "image-hash")
        },
        parser = YapeReceiptParser(),
        store = object : ReceiptCandidateStore {
            override suspend fun import(ownerId: String, imageFingerprint: String, fields: YapeReceiptFields, isIncome: Boolean?) =
                ReceiptCandidateSaveResult.Saved(newReceiptCandidate(ownerId, imageFingerprint, fields, isIncome))
            override fun observe(ownerId: String) = kotlinx.coroutines.flow.emptyFlow<List<ReceiptCandidate>>()
            override suspend fun get(ownerId: String, candidateId: String): ReceiptCandidate? = null
            override suspend fun edit(ownerId: String, candidateId: String, edit: ReceiptCandidateEdit): ReceiptCandidate? = null
            override suspend fun markConfirmed(ownerId: String, candidateId: String) = false
            override suspend fun discard(ownerId: String, candidateId: String) = false
        },
    )
}
