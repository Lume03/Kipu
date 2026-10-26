package com.kipu.app.feature.movements.data.local

import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionStatus

internal fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    userId = userId,
    type = MovementType.fromString(type),
    amountMinor = amountMinor,
    currency = currencyCode,
    // Older planning snapshots used destination_account_id for an income. Read those
    // rows through the same single-account contract without changing their ledger.
    sourceAccountId = sourceAccountId ?: destinationAccountId.takeIf { type.equals("INCOME", ignoreCase = true) },
    destinationAccountId = destinationAccountId.takeUnless { type.equals("INCOME", ignoreCase = true) && sourceAccountId == null },
    cardId = cardId,
    operationKind = operationKind,
    installmentCount = installmentCount,
    categoryId = categoryId,
    merchantId = merchantId,
    merchantProvisionalText = merchantProvisionalText,
    legacyKind = legacyKind,
    occurredAt = occurredAt,
    note = note,
    status = TransactionStatus.fromString(status),
    syncStatus = MovementSyncStatus.fromString(syncStatus),
    createdAt = createdAt,
    updatedAt = updatedAt,
    paymentChannel = source,
)
