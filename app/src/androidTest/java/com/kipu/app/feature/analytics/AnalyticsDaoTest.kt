package com.kipu.app.feature.analytics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kipu.app.core.database.KipuDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsDaoTest {
    @Test fun excludesTransfersPrincipalVoidsAndOtherOwnersAndNetsValidRefund() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).build()
        try {
            val sql = db.openHelper.writableDatabase
            fun insert(id: String, owner: String, type: String, amount: Long, time: Long, status: String = "ACTIVE", kind: String? = null, sync: String = "SYNCED") {
                sql.execSQL("INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,occurred_at,status,sync_status,created_at,updated_at,operation_kind,revision) VALUES (?,?,?,?,?,?,?,?,?,?,?,1)", arrayOf<Any?>(id, owner, type, amount, "PEN", time, status, sync, 0L, 0L, kind))
            }
            insert("purchase", "a", "EXPENSE", 10000, 100)
            insert("transfer", "a", "TRANSFER", 20000, 100)
            insert("principal", "a", "EXPENSE", 30000, 100, kind = "DEBT_PAYMENT")
            insert("void", "a", "EXPENSE", 40000, 100, status = "VOIDED")
            insert("other", "b", "EXPENSE", 50000, 100)
            insert("outside", "a", "EXPENSE", 60000, 200)
            assertEquals(10000L, db.analyticsDao().observeExpenses("a", 100, 200).first().single().amountMinor)
            insert("refund", "a", "INCOME", 1000, 300, kind = "REFUND")
            insert("rejected", "a", "INCOME", 2000, 300, kind = "REFUND", sync = "FAILED_PERMANENT")
            sql.execSQL("INSERT INTO transaction_links (user_id,source_transaction_id,target_transaction_id,link_type,created_at) VALUES ('a','purchase','refund','REFUND',0)")
            sql.execSQL("INSERT INTO transaction_links (user_id,source_transaction_id,target_transaction_id,link_type,created_at) VALUES ('a','purchase','rejected','REFUND',0)")
            val rows = db.analyticsDao().observeExpenses("a", 100, 200).first()
            assertEquals(1, rows.size)
            assertEquals(10000L, rows.single().amountMinor)
            assertEquals(1000L, rows.single().refundedMinor)
            assertEquals(50000L, db.analyticsDao().observeExpenses("b", 100, 200).first().single().amountMinor)
        } finally { db.close() }
    }
    /** HU-41 E2: real SQL boundaries and repository projections feed a filtered comparison. */
    @Test fun repositoryComparisonCombinesFiltersAndKeepsPeriodBoundariesAndOwnerIsolation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).build()
        try {
            val zone = java.time.ZoneId.of("America/Lima")
            val previous = com.kipu.app.feature.analytics.domain.AnalyticsPeriod(java.time.LocalDate.parse("2026-09-01"), java.time.LocalDate.parse("2026-09-30"), zone)
            val current = com.kipu.app.feature.analytics.domain.AnalyticsPeriod(java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-10-31"), zone)
            val sql = db.openHelper.writableDatabase
            fun insert(id: String, time: Long, amount: Long, category: String = "food", account: String = "bank", card: String = "card", merchant: String = "shop", currency: String = "PEN", owner: String = "a") {
                sql.execSQL("INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,occurred_at,status,sync_status,created_at,updated_at,revision,category_id,source_account_id,card_id,merchant_id) VALUES (?,?,?,?,?,?,?,?,?,?,1,?,?,?,?)",
                    arrayOf<Any?>(id, owner, "EXPENSE", amount, currency, time, "ACTIVE", "SYNCED", 0L, 0L, category, account, card, merchant))
            }
            for ((owner, account) in listOf("a" to "bank", "a" to "other", "b" to "bank")) {
                db.accountDao().insert(com.kipu.app.feature.accounts.data.local.AccountEntity(
                    account, owner, "$owner-$account-op", "Cuenta", "SAVINGS", "PEN", null, null, null,
                    0, 1, createdAt = 1, updatedAt = 1))
            }
            insert("before", previous.fromMillis, 1000)
            insert("current", current.fromMillis, 1500)
            insert("last", current.toExclusiveMillis - 1, 500)
            insert("outside", current.toExclusiveMillis, 99999)
            insert("foreign", current.fromMillis, 99999, owner = "b")
            insert("category", current.fromMillis, 99999, category = "other")
            insert("account", current.fromMillis, 99999, account = "other")
            insert("card", current.fromMillis, 99999, card = "other")
            insert("merchant", current.fromMillis, 99999, merchant = "other")
            insert("usd", current.fromMillis, 99999, currency = "USD")
            val repository = com.kipu.app.feature.analytics.data.AnalyticsRepository(db.analyticsDao())
            val filters = com.kipu.app.feature.analytics.domain.AnalyticsFilters(setOf("food"), setOf("bank"), setOf("card"), setOf("shop"), currency = com.kipu.app.core.finance.domain.model.Currency.PEN)
            val today = java.time.LocalDate.parse("2026-11-05")
            fun calculate(period: com.kipu.app.feature.analytics.domain.AnalyticsPeriod, rows: List<com.kipu.app.feature.analytics.domain.ExpenseRow>) =
                com.kipu.app.feature.analytics.domain.AnalyticsCalculator.calculate(period, today, filters, rows)
            val comparison = com.kipu.app.feature.analytics.domain.AnalyticsCalculator.compare(
                calculate(previous, repository.observe("a", previous).first()), calculate(current, repository.observe("a", current).first()))
            val total = comparison.byCurrency.getValue(com.kipu.app.core.finance.domain.model.Currency.PEN)
            assertEquals(1000L, total.previous.minorUnits)
            assertEquals(2000L, total.current.minorUnits)
            assertEquals(1000L, total.difference.minorUnits)
            assertEquals(com.kipu.app.feature.analytics.domain.PercentageChange.Value(java.math.BigDecimal("100.00")), total.percentage)
        } finally { db.close() }
    }

}
