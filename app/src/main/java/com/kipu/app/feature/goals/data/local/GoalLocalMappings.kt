package com.kipu.app.feature.goals.data.local

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.feature.goals.domain.model.GoalEvent
import com.kipu.app.feature.goals.domain.model.GoalEventType
import com.kipu.app.feature.goals.domain.model.GoalStatus
import com.kipu.app.feature.goals.domain.model.GoalSyncState
import java.time.Instant
import java.time.LocalDate

fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    userId = userId,
    name = name,
    target = Money(targetMinor, Currency.fromCode(currencyCode)),
    targetDate = targetDate?.let(LocalDate::parse),
    linkedAccountId = linkedAccountId,
    iconKey = iconKey,
    colorArgb = colorArgb,
    status = GoalStatus.valueOf(status),
    revision = revision,
    syncState = runCatching { GoalSyncState.valueOf(syncState) }.getOrDefault(GoalSyncState.PENDING),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Goal.toEntity(syncState: String = this.syncState.name): GoalEntity = GoalEntity(
    id = id,
    userId = userId,
    name = name,
    targetMinor = target.minorUnits,
    currencyCode = target.currency.name,
    targetDate = targetDate?.toString(),
    linkedAccountId = linkedAccountId,
    iconKey = iconKey,
    colorArgb = colorArgb,
    status = status.name,
    revision = revision,
    syncState = syncState,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun GoalEventWithTransactionStatusRow.toDomain(): GoalEvent {
    val entity = event
    return GoalEvent(
        id = entity.id,
        userId = entity.userId,
        goalId = entity.goalId,
        type = GoalEventType.valueOf(entity.eventType),
        delta = Money(entity.deltaMinor, Currency.fromCode(entity.currencyCode)),
        backingAccountId = entity.backingAccountId,
        operationId = entity.operationId,
        transactionId = entity.transactionId,
        transactionVoided = linkedTransactionStatus == "VOIDED",
        occurredAt = Instant.ofEpochMilli(entity.occurredAt),
        createdAt = Instant.ofEpochMilli(entity.createdAt),
    )
}

fun GoalEvent.toEntity(): GoalEventEntity = GoalEventEntity(
    id = id,
    userId = userId,
    goalId = goalId,
    transactionId = transactionId,
    eventType = type.name,
    deltaMinor = delta.minorUnits,
    currencyCode = delta.currency.name,
    backingAccountId = backingAccountId,
    operationId = operationId,
    occurredAt = occurredAt.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
)

fun GoalBackingAccountRow.toDomain(): GoalBackingAccount {
    val currency = Currency.fromCode(currencyCode)
    return GoalBackingAccount(
        id = accountId,
        name = accountName,
        currency = currency,
        confirmedBalance = Money(confirmedBalanceMinor, currency),
        reserved = Money(reservedMinor, currency),
        available = Money(availableMinor, currency),
        shortfall = Money(shortfallMinor, currency),
    )
}
