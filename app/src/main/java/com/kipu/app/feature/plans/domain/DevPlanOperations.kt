package com.kipu.app.feature.plans.domain

import com.kipu.app.feature.plans.domain.model.BillingVerificationResult
import com.kipu.app.feature.plans.domain.model.CommercialOption

/** Server-authorized development commands; commercial preferences remain inert. */
interface DevPlanOperations {
    suspend fun isDevelopmentMode(): Boolean
    suspend fun activate(option: CommercialOption): BillingVerificationResult
}
