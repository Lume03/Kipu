package com.kipu.app.feature.statements.data.local

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.statements.domain.StatementImportCard
import com.kipu.app.feature.statements.domain.StatementImportMatching
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomStatementImportMatching @Inject constructor(
    private val cards: CardDao,
    private val dao: StatementImportMatchingDao,
) : StatementImportMatching {
    override suspend fun activeCreditCards(ownerId: String) = cards.getActive(ownerId)
        .filter { it.type == "CREDIT" && !it.isArchived && it.userId == ownerId }
        .map { StatementImportCard(it.id, it.issuer, it.lastFourDigits, Currency.valueOf(it.currency)) }

    override suspend fun hasDuplicate(ownerId: String, cardId: String, amountMinor: Long, currency: Currency, date: LocalDate): Boolean {
        // Statement dates describe the issuer's calendar day, independently of device timezone.
        val zone = ZoneId.of("America/Lima")
        val from = date.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = date.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.hasDuplicate(ownerId, cardId, amountMinor, currency.name, from, until, from * 1000, until * 1000)
    }
}
