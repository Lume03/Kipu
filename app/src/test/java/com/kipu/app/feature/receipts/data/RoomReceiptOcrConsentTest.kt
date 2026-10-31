package com.kipu.app.feature.receipts.data

import com.kipu.app.feature.receipts.domain.OCR_CONSENT_VERSION
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.model.PermissionSource
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoomReceiptOcrConsentTest {
    private val owner = UUID.randomUUID()
    private val source = PermissionSource.YAPE_RECEIPT_OCR.name
    private val rows = mutableMapOf<Pair<UUID, String>, AccountSourceConsentEntity>()
    private val dao = mockk<PermissionConsentDao> {
        coEvery { findAccountConsent(any(), any()) } answers { rows[firstArg<UUID>() to secondArg<String>()] }
        coEvery { putAccountConsent(any()) } answers {
            val row = firstArg<AccountSourceConsentEntity>()
            rows[row.userId to row.source] = row
        }
    }

    @Test fun grant_is_persisted_by_owner_and_survives_repository_recreation() = runTest {
        val repository = RoomReceiptOcrConsent(dao)
        assertFalse(repository.isGranted(owner.toString()))
        repository.grant(owner.toString())
        assertEquals(OCR_CONSENT_VERSION, rows[owner to source]?.explanationVersion)
        assertTrue(RoomReceiptOcrConsent(dao).isGranted(owner.toString()))
        assertFalse(repository.isGranted(UUID.randomUUID().toString()))
    }

    @Test fun revoke_requires_a_new_grant() = runTest {
        val repository = RoomReceiptOcrConsent(dao)
        repository.grant(owner.toString())
        repository.revoke(owner.toString())
        assertFalse(repository.isGranted(owner.toString()))
        assertEquals("REVOKED", rows[owner to source]?.consentState)
        repository.grant(owner.toString())
        assertTrue(repository.isGranted(owner.toString()))
    }

    @Test fun changed_version_or_denied_capability_fails_closed() = runTest {
        val repository = RoomReceiptOcrConsent(dao)
        repository.grant(owner.toString())
        val granted = requireNotNull(rows[owner to source])
        rows[owner to source] = granted.copy(explanationVersion = OCR_CONSENT_VERSION + 1)
        assertFalse(repository.isGranted(owner.toString()))
        rows[owner to source] = granted.copy(capabilityState = "DENIED")
        assertFalse(repository.isGranted(owner.toString()))
        rows[owner to source] = granted.copy(consentState = "NOT_EXPLAINED")
        assertFalse(repository.isGranted(owner.toString()))
    }

    @Test fun notification_consent_and_invalid_owner_do_not_authorize_ocr() = runTest {
        rows[owner to PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name] = AccountSourceConsentEntity(
            owner, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT.name, "GRANTED", "ALLOWED",
        )
        assertFalse(RoomReceiptOcrConsent(dao).isGranted(owner.toString()))
        assertFalse(RoomReceiptOcrConsent(dao).isGranted("invalid-owner"))
    }
}
