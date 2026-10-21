package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReceiptRoomMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test fun addingReceiptCandidatesPreservesExistingFinancialHistory() {
        val name = "receipts-v24-to-v25"
        helper.createDatabase(name, 24).use { db ->
            db.execSQL("""INSERT INTO accounts (
                id,user_id,creation_operation_id,alias,type,currency,initial_balance_minor_units,
                opened_at,is_archived,remote_revision,created_at,updated_at
            ) VALUES ('account','owner','account-op','Prueba','SAVINGS','PEN',0,1000,0,1,1000,1000)""")
            db.execSQL("""INSERT INTO transactions (
                id,user_id,type,amount_minor,currency_code,source_account_id,operation_kind,
                occurred_at,status,sync_status,created_at,updated_at
            ) VALUES ('movement','owner','EXPENSE',1250,'PEN','account','STANDARD',2000,'ACTIVE','SYNCED',2000,2000)""")
            db.execSQL("""INSERT INTO ledger_entries (
                id,user_id,transaction_id,account_id,role,signed_amount_minor,currency_code,created_at
            ) VALUES ('entry','owner','movement','account','SOURCE',-1250,'PEN',2000)""")
        }
        helper.runMigrationsAndValidate(name, 25, true, MIGRATION_24_25).use { db ->
            db.query("SELECT SUM(signed_amount_minor) FROM ledger_entries WHERE user_id='owner'").use {
                it.moveToFirst(); assertEquals(-1250L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM transactions WHERE id='movement'").use {
                it.moveToFirst(); assertEquals(1L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM receipt_candidates").use {
                it.moveToFirst(); assertEquals(0L, it.getLong(0))
            }
        }
    }
}
