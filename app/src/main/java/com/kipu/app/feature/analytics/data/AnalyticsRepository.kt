package com.kipu.app.feature.analytics.data

import com.kipu.app.core.storage.data.MerchantLogoUrlResolver
import com.kipu.app.feature.analytics.domain.AnalyticsFilterOption
import com.kipu.app.feature.analytics.domain.AnalyticsFilterOptions
import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.ExpenseRow
import com.kipu.app.feature.analytics.domain.IncomeRow
import com.kipu.app.feature.analytics.domain.LedgerDelta
import com.kipu.app.feature.analytics.domain.MerchantVisual
import com.kipu.app.feature.analytics.domain.OperatingExpenseClassification
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AnalyticsRepository @Inject constructor(private val dao: AnalyticsDao) {
    fun observe(owner: String, period: AnalyticsPeriod) = dao.observeExpenses(owner, period.fromMillis, period.toExclusiveMillis)
        .map { rows ->
            rows.filter { OperatingExpenseClassification.includes(it.type, it.status, it.syncStatus, it.operationKind, it.legacyKind) }
                .map { ExpenseRow(it.amountMinor, it.refundedMinor, it.currency, it.categoryId, it.accountId, it.cardId, it.merchantId, it.occurredAt) }
        }

    /** Operating income of the period; the classification already happened in the query. */
    fun observeIncomes(owner: String, period: AnalyticsPeriod): Flow<List<IncomeRow>> =
        dao.observeIncomes(owner, period.fromMillis, period.toExclusiveMillis)
            .map { rows -> rows.map { IncomeRow(it.amountMinor, it.currency, it.accountId, it.occurredAt) } }

    /** Ledger entries since the start of [period]; later entries are what the daily balance has to undo. */
    fun observeLedgerDeltas(owner: String, period: AnalyticsPeriod): Flow<List<LedgerDelta>> =
        dao.observeLedgerRows(owner, period.fromMillis)
            .map { rows -> rows.map { LedgerDelta(it.accountId, it.currency, it.deltaMinor, it.atMillis) } }

    /** Owner-scoped labels for the filter sheet and the category breakdown. */
    fun observeFilterOptions(owner: String): Flow<AnalyticsFilterOptions> = combine(
        dao.observeCategoryOptions(owner),
        dao.observeAccountOptions(owner),
        dao.observeCardOptions(owner),
        dao.observeMerchantOptions(owner),
    ) { categories, accounts, cards, merchants ->
        AnalyticsFilterOptions(
            categories = categories.distinctBy { it.id }.map { AnalyticsFilterOption(it.id, it.name, icon = it.icon, color = it.color) },
            accounts = accounts.toOptions(),
            cards = cards.toOptions(),
            merchants = merchants.distinctBy { it.id }.map { row ->
                AnalyticsFilterOption(
                    id = row.id, name = row.name,
                    merchant = MerchantVisual(
                        id = row.id, name = row.name, logoKey = row.logoKey, brandColor = row.brandColor,
                        logoUrl = MerchantLogoUrlResolver.resolve(com.kipu.app.BuildConfig.SUPABASE_URL, row.logoPath),
                        logoDarkUrl = MerchantLogoUrlResolver.resolve(com.kipu.app.BuildConfig.SUPABASE_URL, row.logoDarkPath),
                    ),
                )
            },
        )
    }

    private fun List<AnalyticsOptionRow>.toOptions() = distinctBy { it.id }.map { AnalyticsFilterOption(it.id, it.name) }
}
