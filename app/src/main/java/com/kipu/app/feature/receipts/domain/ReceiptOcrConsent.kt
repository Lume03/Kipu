package com.kipu.app.feature.receipts.domain

const val OCR_CONSENT_VERSION = 1

/** Owner-scoped permission for local OCR, independent of Premium and notification access. */
interface ReceiptOcrConsent {
    suspend fun isGranted(ownerId: String): Boolean
    suspend fun grant(ownerId: String)
    suspend fun revoke(ownerId: String)
}
