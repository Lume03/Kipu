package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.runtime.saveable.listSaver
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.CandidateStatus
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate
import java.time.Instant

/** Preserve the original operation/processing instants when the host screen is recreated. */
internal val PurchaseCandidateStateSaver = listSaver<PurchaseCandidate?, String>(
    save = { candidate -> candidate?.let { listOf(it.id, it.cardId.value, it.amount.minorUnits.toString(),
        it.amount.currency.name, it.merchant, it.occurredAt.toString(), it.suggestedInstallments.toString(),
        it.status.name, it.categoryId.orEmpty(), it.processedAt?.toString().orEmpty()) } ?: emptyList() },
    restore = { values -> if (values.isEmpty()) null else PurchaseCandidate(
        id = values[0], cardId = CardId(values[1]), amount = Money(values[2].toLong(), Currency.valueOf(values[3])),
        merchant = values[4], occurredAt = Instant.parse(values[5]), suggestedInstallments = values[6].toInt(),
        status = CandidateStatus.valueOf(values[7]), categoryId = values[8].ifBlank { null },
        processedAt = values[9].takeIf(String::isNotBlank)?.let(Instant::parse),
    ) },
)
