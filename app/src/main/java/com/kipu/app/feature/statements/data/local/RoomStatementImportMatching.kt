package com.kipu.app.feature.statements.data.local

import com.kipu.app.feature.statements.domain.StatementImportDebitCard
import com.kipu.app.feature.statements.domain.StatementLineKind
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.accounts.domain.model.DebitCard

import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.statements.domain.StatementImportCard
import com.kipu.app.feature.statements.domain.StatementImportMatching
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomStatementImportMatching @Inject constructor(
    private val cards: CardDao,
    private val dao: StatementImportMatchingDao,
    private val instruments: ObserveInstruments,
) : StatementImportMatching {
    override suspend fun activeDebitCards(ownerId: String): List<StatementImportDebitCard> {
        val accounts = instruments.observeAccounts(true).first().filter { it.userId.value == ownerId && !it.isArchived && !it.isPlanLocked }.associateBy { it.id.value }
        return instruments.observeCards(true).first().filterIsInstance<DebitCard>()
            .filter { it.userId.value == ownerId && !it.isArchived && !it.isPlanLocked && accounts[it.linkedAccountId.value]?.currency == it.currency }
            .map { StatementImportDebitCard(it.id.value, it.issuer, it.alias, it.lastFourDigits, it.currency, it.linkedAccountId.value) }
    }

    override suspend fun hasAccountDuplicate(ownerId: String, accountId: String, amountMinor: Long, currency: Currency, date: LocalDate, kind: StatementLineKind): Boolean {
        val type = when (kind) {
            StatementLineKind.ACCOUNT_DEBIT -> "EXPENSE"
            StatementLineKind.ACCOUNT_CREDIT -> "INCOME"
            else -> return false
        }
        val zone = ZoneId.of("America/Lima")
        return dao.hasAccountDuplicate(ownerId, accountId, amountMinor, currency.name, type,
            date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

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
