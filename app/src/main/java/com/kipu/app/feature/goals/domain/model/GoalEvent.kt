package com.kipu.app.feature.goals.domain.model

import com.kipu.app.core.finance.domain.model.Money
import java.time.Instant

enum class GoalEventType {
    ALLOCATION,
    RELEASE,
    DIRECT_DEPOSIT,
    ADJUSTMENT,
    REASSIGNMENT_OUT,
    REASSIGNMENT_IN,
}

/**
 * Immutable reservation history. A null backing account is retained only for
 * legacy rows that could not be reconciled; new commands must supply one.
 */
data class GoalEvent(
    val id: String,
    val userId: String,
    val goalId: String,
    val type: GoalEventType,
    val delta: Money,
    val backingAccountId: String?,
    val operationId: String?,
    val transactionId: String? = null,
    val transactionVoided: Boolean = false,
    val occurredAt: Instant,
    val createdAt: Instant = occurredAt,
) {
    init {
        require(id.isNotBlank()) { "Goal event id is required" }
        require(userId.isNotBlank()) { "Goal event owner is required" }
        require(goalId.isNotBlank()) { "Goal event goal id is required" }
        require(delta.minorUnits != 0L) { "Goal event delta cannot be zero" }
        require(
            when (type) {
                GoalEventType.ALLOCATION, GoalEventType.DIRECT_DEPOSIT, GoalEventType.REASSIGNMENT_IN -> delta.minorUnits > 0L
                GoalEventType.RELEASE, GoalEventType.REASSIGNMENT_OUT -> delta.minorUnits < 0L
                GoalEventType.ADJUSTMENT -> true
            },
        ) { "Goal event delta sign does not match its type" }
    }

    val isLegacyUnlinked: Boolean get() = backingAccountId == null
}
