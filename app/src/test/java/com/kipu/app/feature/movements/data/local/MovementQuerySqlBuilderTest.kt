package com.kipu.app.feature.movements.data.local

import com.kipu.app.feature.movements.domain.model.AdvancedHistoryCriteria
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.WalletPaymentChannel
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementQuerySqlBuilderTest {
    @Test
    fun walletContextSurvivesFreeFallbackForItemsAndSummaries() {
        val query = MovementHistoryQuery(contextPaymentChannel = WalletPaymentChannel.YAPE,
            advancedCriteria = AdvancedHistoryCriteria(merchantIds = setOf("shop"))).toBasicFallback()
        org.junit.Assert.assertFalse(query.requiresAdvancedAccess)
        val queries = listOf(MovementQuerySqlBuilder.build("owner", query), MovementQuerySqlBuilder.buildCount("owner", query), MovementQuerySqlBuilder.buildNetFlow("owner", query))
        assertTrue(queries.all { it.sql.contains("AND source = ?") })
    }

    @Test
    fun accountContextRemainsScopedWithoutPremiumFilters() {
        val query = MovementHistoryQuery(contextAccountId = "bank").toBasicFallback()
        org.junit.Assert.assertFalse(query.requiresAdvancedAccess)
        assertTrue(MovementQuerySqlBuilder.build("owner", query).sql.contains("source_account_id = ? OR destination_account_id = ?"))
    }

    @Test
    fun revisedTransactionsRemainInTheNetFlowSummary() {
        val sql = MovementQuerySqlBuilder
            .buildNetFlow(userId = "owner", query = MovementHistoryQuery())
            .sql

        assertTrue(
            "A corrected transaction still contributes its current financial effect",
            sql.contains("status IN ('ACTIVE', 'CONFIRMED', 'REVISED')"),
        )
    }

    @Test
    fun walletChannelsFilterPagesCountsAndNetFlowByThePersistedMovementChannel() {
        val query = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(
                paymentChannels = setOf(WalletPaymentChannel.YAPE, WalletPaymentChannel.PLIN),
            ),
        )
        val sqlQueries = listOf(
            MovementQuerySqlBuilder.build(userId = "owner", query = query),
            MovementQuerySqlBuilder.buildCount(userId = "owner", query = query),
            MovementQuerySqlBuilder.buildNetFlow(userId = "owner", query = query),
        )

        assertTrue(
            "Wallet filtering uses the transaction's persisted payment channel for every history query",
            sqlQueries.all { it.sql.contains("source IN (?,?)") },
        )
    }
}
