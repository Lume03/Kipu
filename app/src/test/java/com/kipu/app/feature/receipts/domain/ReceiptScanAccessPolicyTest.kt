package com.kipu.app.feature.receipts.domain

import com.kipu.app.feature.plans.domain.FeatureAccessPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptScanAccessPolicyTest {
    @Test
    fun yapeOcrIsAvailableWithoutPremiumEntitlement() {
        assertTrue(ReceiptScanAccessPolicy(FeatureAccessPolicy()).allows())
    }
}
