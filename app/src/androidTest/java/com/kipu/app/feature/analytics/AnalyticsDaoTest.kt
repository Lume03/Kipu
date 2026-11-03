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
}
