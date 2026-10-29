package com.kipu.app.feature.plans.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Access derived from the current owner's verified offline entitlement, never a display tier. */
interface VerifiedPlanAccess {
    suspend fun isPremiumVerified(ownerId: String? = null): Boolean
    fun observePremiumVerified(): Flow<Boolean>
}

/** Conservative default for direct construction outside dependency injection. */
object FreePlanAccess : VerifiedPlanAccess {
    override suspend fun isPremiumVerified(ownerId: String?): Boolean = false
    override fun observePremiumVerified(): Flow<Boolean> = flowOf(false)
}
