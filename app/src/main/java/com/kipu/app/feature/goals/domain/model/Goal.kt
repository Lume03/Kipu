package com.kipu.app.feature.goals.domain.model

import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import java.time.Instant
import java.time.LocalDate

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
    LOCKED_BY_PLAN,
}

enum class GoalSyncState {
    PENDING,
    SYNCED,
    CONFLICT,
    FAILED,
    DELETE_PENDING,
}

data class Goal(
    val id: String,
    val userId: String,
    val name: String,
    val target: Money,
    val targetDate: LocalDate? = null,
    val linkedAccountId: String? = null,
    val iconKey: String? = null,
    val colorArgb: Int? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val revision: Long = 0L,
    val syncState: GoalSyncState = GoalSyncState.PENDING,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Goal id is required" }
        require(userId.isNotBlank()) { "Goal owner is required" }
        require(name.isNotBlank()) { "Goal name is required" }
        require(target.minorUnits > 0L) { "Goal target must be positive" }
        require(revision >= 0L) { "Goal revision cannot be negative" }
    }

    val currency: Currency get() = target.currency
}

data class GoalBackingAccount(
    val id: String,
    val name: String,
    val currency: Currency,
    val confirmedBalance: Money,
    val reserved: Money,
    val available: Money,
    val shortfall: Money,
    val accountType: AccountType = AccountType.BANK,
    val preset: AccountPreset? = null,
)
