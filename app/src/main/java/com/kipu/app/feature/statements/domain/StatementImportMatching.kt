package com.kipu.app.feature.statements.domain

import com.kipu.app.core.finance.domain.model.Currency
import java.time.LocalDate

data class StatementImportCard(val id: String, val issuer: String, val lastFour: String, val currency: Currency)

interface StatementImportMatching {
    suspend fun activeCreditCards(ownerId: String): List<StatementImportCard>
    suspend fun hasDuplicate(ownerId: String, cardId: String, amountMinor: Long, currency: Currency, date: LocalDate): Boolean
}
