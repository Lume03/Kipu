package com.kipu.app.feature.budgets.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTO columns match the local EP-PRE PostgreSQL migration; pending edits and period scope snapshots stay local. */
@Serializable
data class BudgetRemoteDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("limit_minor") val limitMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("period_type") val periodType: String,
    @SerialName("range_start_on") val rangeStartOn: String?,
    @SerialName("range_end_on") val rangeEndOn: String?,
    @SerialName("alert_thresholds_bps") val alertThresholdsBps: List<Int>,
    val status: String,
    @SerialName("is_active") val isActive: Boolean,
    val revision: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class BudgetPeriodRemoteDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("budget_id") val budgetId: String,
    @SerialName("starts_on") val startsOn: String,
    @SerialName("ends_on") val endsOn: String,
    @SerialName("limit_minor") val limitMinor: Long,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BudgetTombstoneRemoteDto(
    @SerialName("budget_id") val budgetId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_revision") val lastRevision: Long,
    @SerialName("deleted_at") val deletedAt: String,
)

@Serializable
data class BudgetDeleteCommandDto(@SerialName("budget_id") val budgetId: String)

@Serializable
data class BudgetDeleteRpcRequestDto(@SerialName("p_command") val command: BudgetDeleteCommandDto)

@Serializable
data class BudgetSyncPayload(
    val budget: BudgetRemoteDto? = null,
    val period: BudgetPeriodRemoteDto? = null,
    @SerialName("budget_id") val budgetId: String? = null,
    @SerialName("last_revision") val lastRevision: Long? = null,
)

@Serializable
data class BudgetCommandResponseDto(
    val status: String? = null,
    @SerialName("budget_id") val budgetId: String? = null,
    val deleted: Boolean? = null,
)
