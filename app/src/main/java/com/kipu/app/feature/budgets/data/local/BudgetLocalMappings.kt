package com.kipu.app.feature.budgets.data.local

import com.kipu.app.feature.budgets.domain.Budget
import com.kipu.app.feature.budgets.domain.BudgetInterval
import com.kipu.app.feature.budgets.domain.BudgetPeriod
import com.kipu.app.feature.budgets.domain.BudgetPeriodType
import com.kipu.app.feature.budgets.domain.BudgetSchedule
import com.kipu.app.feature.budgets.domain.BudgetScope
import com.kipu.app.feature.budgets.domain.BudgetStatus
import com.kipu.app.feature.budgets.domain.PendingBudgetChange
import java.time.LocalDate

internal fun BudgetEntity.toDomain(): Budget {
    val type = BudgetPeriodType.valueOf(periodType)
    val schedule = if (type == BudgetPeriodType.CUSTOM_RANGE) {
        BudgetSchedule.CustomRange(requireNotNull(rangeStartOn).toLocalDate(), requireNotNull(rangeEndOn).toLocalDate())
    } else {
        BudgetSchedule.Recurring(type)
    }
    val pending = pendingEffectiveFrom?.let { effectiveFrom ->
        PendingBudgetChange(
            scope = when (pendingScopeType) {
                null -> null
                "TOTAL" -> BudgetScope.Total
                "CATEGORY" -> BudgetScope.Category(requireNotNull(pendingScopeCategoryId))
                else -> error("Unknown pending budget scope: $pendingScopeType")
            },
            schedule = pendingScheduleType?.let { pendingType ->
                val parsedType = BudgetPeriodType.valueOf(pendingType)
                if (parsedType == BudgetPeriodType.CUSTOM_RANGE) {
                    BudgetSchedule.CustomRange(
                        requireNotNull(pendingRangeStartOn).toLocalDate(),
                        requireNotNull(pendingRangeEndOn).toLocalDate(),
                    )
                } else {
                    BudgetSchedule.Recurring(parsedType)
                }
            },
            effectiveFrom = effectiveFrom.toLocalDate(),
        )
    }
    return Budget(
        id = id,
        ownerId = userId,
        name = name,
        scope = categoryId?.let(BudgetScope::Category) ?: BudgetScope.Total,
        limitMinor = limitMinor,
        currency = currencyCode,
        schedule = schedule,
        thresholdsBps = alertThresholdsBps.csvInts(),
        status = BudgetStatus.valueOf(status),
        revision = revision,
        createdOn = createdOn.toLocalDate(),
        pendingChange = pending,
    )
}

internal fun Budget.toEntity(existing: BudgetEntity?, now: Long): BudgetEntity {
    val custom = schedule as? BudgetSchedule.CustomRange
    val pending = pendingChange
    val pendingCustom = pending?.schedule as? BudgetSchedule.CustomRange
    return BudgetEntity(
        id = id,
        userId = ownerId,
        name = name,
        categoryId = (scope as? BudgetScope.Category)?.categoryId,
        limitMinor = limitMinor,
        currencyCode = currency,
        periodType = schedule.type.name,
        rangeStartOn = custom?.start?.toString(),
        rangeEndOn = custom?.end?.toString(),
        alertThresholdsBps = thresholdsBps.joinToString(","),
        status = status.name,
        revision = revision,
        createdOn = createdOn.toString(),
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
        pendingScopeType = pending?.scope?.let { if (it is BudgetScope.Category) "CATEGORY" else "TOTAL" },
        pendingScopeCategoryId = (pending?.scope as? BudgetScope.Category)?.categoryId,
        pendingScheduleType = pending?.schedule?.type?.name,
        pendingRangeStartOn = pendingCustom?.start?.toString(),
        pendingRangeEndOn = pendingCustom?.end?.toString(),
        pendingEffectiveFrom = pending?.effectiveFrom?.toString(),
    )
}

internal fun BudgetPeriodEntity.toDomain(): BudgetPeriod = BudgetPeriod(
    id = id,
    budgetId = budgetId,
    ownerId = userId,
    interval = BudgetInterval(startsOn.toLocalDate(), endsOn.toLocalDate()),
    limitMinor = limitMinor,
    scope = scopeCategoryId?.let(BudgetScope::Category) ?: BudgetScope.Total,
)

internal fun BudgetPeriod.toEntity(now: Long): BudgetPeriodEntity = BudgetPeriodEntity(
    id = id,
    userId = ownerId,
    budgetId = budgetId,
    startsOn = interval.start.toString(),
    endsOn = interval.endExclusive.toString(),
    limitMinor = limitMinor,
    scopeCategoryId = (scope as? BudgetScope.Category)?.categoryId,
    createdAt = now,
)

private fun String.csvInts(): List<Int> = split(',').filter(String::isNotBlank).map(String::toInt)

private fun String.toLocalDate(): LocalDate = LocalDate.parse(this)
