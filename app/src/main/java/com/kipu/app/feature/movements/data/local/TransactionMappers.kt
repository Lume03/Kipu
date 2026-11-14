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
    // The historical refund feed exposes the card's backing liability as source_account_id.
    // Hydrating an existing local card refund retains card_id. That account is a ledger
    // implementation detail, not a second refund destination in the domain (L2).
    sourceAccountId = if (type.equals("INCOME", true) && operationKind.equals("REFUND", true) && !cardId.isNullOrBlank()) {
        null
    } else {
        sourceAccountId ?: destinationAccountId.takeIf { type.equals("INCOME", ignoreCase = true) }
    },
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
    captureSource = com.kipu.app.feature.movements.domain.model.MovementSource.valueOf(captureSource),
)
