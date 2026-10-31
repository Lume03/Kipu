package com.kipu.app.feature.receipts.data

import com.kipu.app.feature.receipts.domain.OCR_CONSENT_VERSION
import com.kipu.app.feature.receipts.domain.ReceiptOcrConsent
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.model.CapabilityState
import com.kipu.app.feature.settings.domain.model.ConsentState
import com.kipu.app.feature.settings.domain.model.PermissionSource
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Reuses the local consent table; no image, OCR text or remote synchronization is added. */
class RoomReceiptOcrConsent @Inject constructor(private val dao: PermissionConsentDao) : ReceiptOcrConsent {
    override suspend fun isGranted(ownerId: String): Boolean {
        val owner = runCatching { UUID.fromString(ownerId) }.getOrNull() ?: return false
        val row = dao.findAccountConsent(owner, PermissionSource.YAPE_RECEIPT_OCR.name) ?: return false
        return row.userId == owner && row.source == PermissionSource.YAPE_RECEIPT_OCR.name &&
            row.consentState == ConsentState.GRANTED.name && row.capabilityState == CapabilityState.ALLOWED.name &&
            row.explanationVersion == OCR_CONSENT_VERSION
    }

    override suspend fun grant(ownerId: String) = save(ownerId, ConsentState.GRANTED, CapabilityState.ALLOWED)
    override suspend fun revoke(ownerId: String) = save(ownerId, ConsentState.REVOKED, CapabilityState.DENIED)

    private suspend fun save(ownerId: String, state: ConsentState, capability: CapabilityState) {
        dao.putAccountConsent(AccountSourceConsentEntity(
            userId = UUID.fromString(ownerId), source = PermissionSource.YAPE_RECEIPT_OCR.name,
            consentState = state.name, capabilityState = capability.name,
            explanationVersion = OCR_CONSENT_VERSION, updatedAt = Instant.now(),
        ))
    }
}
