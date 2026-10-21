package com.kipu.app.feature.receipts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReceiptCandidateIdentityTest {
    @Test fun referenceFingerprintIgnoresPresentationSpacingAndCase() {
        assertEquals("ABC 123".sha256(), " abc   123 ".normalizedReference()!!.sha256())
    }

    @Test fun differentReferencesRemainDistinctEvenForSameAmount() {
        val first = newReceiptCandidate("owner", "image-a", YapeReceiptFields(1000, 1, "Store", "REF-A", emptyList()), false)
        val second = newReceiptCandidate("owner", "image-b", YapeReceiptFields(1000, 1, "Store", "REF-B", emptyList()), false)
        assertNotEquals(first.referenceFingerprint, second.referenceFingerprint)
    }
}
