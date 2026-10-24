package com.kipu.app.feature.budgets.data.remote

import com.kipu.app.feature.budgets.data.local.BudgetEntity
import com.kipu.app.feature.budgets.data.local.BudgetPeriodEntity
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetScope
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal fun BudgetEntity.toRemoteDto(): BudgetRemoteDto = BudgetRemoteDto(
    id = id,
    userId = userId,
    name = name,
    categoryId = categoryId,
    limitMinor = limitMinor,
    currencyCode = currencyCode,
    periodType = periodType,
    rangeStartOn = rangeStartOn,
    rangeEndOn = rangeEndOn,
    alertThresholdsBps = alertThresholdsBps.split(',').filter(String::isNotBlank).map(String::toInt),
    status = status,
    isActive = status == "ACTIVE",
    revision = revision,
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
    updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
)

internal fun BudgetPeriodEntity.toRemoteDto(): BudgetPeriodRemoteDto = BudgetPeriodRemoteDto(
    id = remoteId(),
    userId = userId,
    budgetId = budgetId,
    startsOn = startsOn,
    endsOn = endsOn,
    limitMinor = limitMinor,
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
)

internal fun BudgetRemoteDto.toLocalEntity(zoneId: ZoneId): BudgetEntity {
    val createdAtMillis = Instant.parse(createdAt).toEpochMilli()
    val periodType = if (periodType == "CUSTOM") "CUSTOM_RANGE" else periodType
    return BudgetEntity(
        id = id,
        userId = userId,
        name = name,
        categoryId = categoryId,
        limitMinor = limitMinor,
        currencyCode = currencyCode.trim(),
        periodType = periodType,
        rangeStartOn = rangeStartOn,
        rangeEndOn = rangeEndOn,
        alertThresholdsBps = alertThresholdsBps.joinToString(","),
        status = if (status == "ACTIVE" && isActive) "ACTIVE" else "FINISHED",
        revision = revision,
        createdOn = Instant.ofEpochMilli(createdAtMillis).atZone(zoneId).toLocalDate().toString(),
        createdAt = createdAtMillis,
        updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    )
}

internal fun BudgetPeriodRemoteDto.toLocalEntity(budget: BudgetEntity): BudgetPeriodEntity {
    val start = java.time.LocalDate.parse(startsOn)
    val end = java.time.LocalDate.parse(endsOn)
    val interval = BudgetInterval(start, end)
    val scope = budget.categoryId?.let(BudgetScope::Category) ?: BudgetScope.Total
    return BudgetPeriodEntity(
        id = BudgetPeriod.idFor(budgetId, interval),
        userId = userId,
        budgetId = budgetId,
        startsOn = startsOn,
        endsOn = endsOn,
        limitMinor = limitMinor,
        scopeCategoryId = (scope as? BudgetScope.Category)?.categoryId,
        createdAt = Instant.parse(createdAt).toEpochMilli(),
    )
}

private fun BudgetPeriodEntity.remoteId(): String = UUID.nameUUIDFromBytes(
    "kipu:budget-period:v1:$userId:$budgetId:$startsOn:$endsOn".toByteArray(Charsets.UTF_8),
).toString()
