package com.kipu.app.feature.analytics.data

import com.kipu.app.feature.analytics.domain.AnalyticsPeriod
import com.kipu.app.feature.analytics.domain.ExpenseRow
import com.kipu.app.feature.analytics.domain.OperatingExpenseClassification
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class AnalyticsRepository @Inject constructor(private val dao: AnalyticsDao) {
    fun observe(owner: String, period: AnalyticsPeriod) = dao.observeExpenses(owner, period.fromMillis, period.toExclusiveMillis)
        .map { rows -> rows.filter { OperatingExpenseClassification.includes(it.type, it.status, it.syncStatus, it.operationKind, it.legacyKind) }.map { ExpenseRow(it.amountMinor, it.refundedMinor, it.currency, it.categoryId, it.accountId, it.cardId, it.merchantId) } }
}
