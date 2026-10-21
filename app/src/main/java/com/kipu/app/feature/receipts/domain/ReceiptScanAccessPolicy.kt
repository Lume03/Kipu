package com.kipu.app.feature.receipts.domain

import com.kipu.app.feature.plans.domain.FeatureAccessPolicy
import com.kipu.app.feature.plans.domain.model.Capability
import com.kipu.app.feature.plans.domain.model.EffectiveEntitlement
import com.kipu.app.feature.plans.domain.model.FeatureAccessDecision
import com.kipu.app.feature.plans.domain.model.FeatureAccessRequest
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import javax.inject.Inject

/** The verified offline lease is checked by the data implementation before this policy is invoked. */
class ReceiptScanAccessPolicy @Inject constructor(private val policy: FeatureAccessPolicy) {
    fun allows(entitlement: EffectiveEntitlement?): Boolean =
        policy.evaluate(FeatureAccessRequest(Capability.PremiumOnly, 0, FreePlanLimits(), entitlement)) is FeatureAccessDecision.Allowed
}
