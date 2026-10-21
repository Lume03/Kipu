package com.kipu.app.feature.receipts.domain

import com.kipu.app.feature.plans.domain.FeatureAccessPolicy
import com.kipu.app.feature.plans.domain.model.Capability
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import com.kipu.app.feature.plans.domain.model.FeatureAccessDecision
import com.kipu.app.feature.plans.domain.model.FeatureAccessRequest
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import javax.inject.Inject

/** Local Yape receipt OCR is available in Kipu Free; consent and review remain separate requirements. */
class ReceiptScanAccessPolicy @Inject constructor(private val policy: FeatureAccessPolicy) {
    fun allows(): Boolean = policy.evaluate(
        FeatureAccessRequest(Capability.YapeReceiptOcr, 0, FreePlanLimits(), effectiveEntitlement = null),
    ) is FeatureAccessDecision.Allowed
}
