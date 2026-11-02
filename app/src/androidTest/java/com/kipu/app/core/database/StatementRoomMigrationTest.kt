package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatementRoomMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test fun savingsSourceDefaultsForExistingDraftAndPreservesLedger() {
        val name = "statements-v32-to-v33"
        helper.createDatabase(name, 32).use { db ->
            db.execSQL("""INSERT INTO statement_imports (id,owner_id,statement_fingerprint,issuer,card_last_four,
                cycle_start,cycle_end,status,created_at,expires_at,card_id)
                VALUES ('draft','owner','fp','BCP','1234','2026-07-01','2026-07-31','PENDING',1,1000,'credit')""")
            db.execSQL("""INSERT INTO statement_import_lines (id,import_id,owner_id,line_fingerprint,position,kind,process_date,
                occurred_date,description,amount_minor,currency,selected,possible_duplicate,status)
                VALUES ('line','draft','owner','line-fp',0,'PURCHASE','2026-07-01','2026-07-01','Store',100,'PEN',1,0,'PENDING')""")
            db.execSQL("""INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,operation_kind,
                occurred_at,status,sync_status,created_at,updated_at)
                VALUES ('existing','owner','EXPENSE',100,'PEN','CARD_PURCHASE',1000,'ACTIVE','SYNCED',1000,1000)""")
        }
        helper.runMigrationsAndValidate(name, 33, true, MIGRATION_32_33).use { db ->
            db.query("SELECT source,account_reference,card_id,status FROM statement_imports WHERE id='draft'").use {
                it.moveToFirst(); assertEquals("CREDIT_CARD", it.getString(0))
                org.junit.Assert.assertTrue(it.isNull(1)); assertEquals("credit", it.getString(2)); assertEquals("PENDING", it.getString(3))
            }
            db.query("SELECT possible_own_transfer FROM statement_import_lines WHERE id='line'").use {
                it.moveToFirst(); assertEquals(0, it.getInt(0))
            }
            for (currency in listOf("pen", "usd")) for (field in listOf("previous_balance", "account_debits", "account_credits", "closing_balance")) {
                db.query("SELECT ${field}_${currency}_minor FROM statement_imports WHERE id='draft'").use {
                    it.moveToFirst(); org.junit.Assert.assertTrue(it.isNull(0))
                }
            }
            db.query("SELECT COUNT(*) FROM transactions WHERE id='existing'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        }
    }

    @Test fun statementCandidatesPreserveLedger() {
        val name = "statements-v29-to-v30"
        helper.createDatabase(name, 29).use { db ->
            db.execSQL("""INSERT INTO transactions (id,user_id,type,amount_minor,currency_code,operation_kind,
                occurred_at,status,sync_status,created_at,updated_at)
                VALUES ('existing','owner','EXPENSE',100,'USD','CARD_PURCHASE',1000,'ACTIVE','SYNCED',1000,1000)""")
        }
        helper.runMigrationsAndValidate(name, 30, true, MIGRATION_29_30).use { db ->
            db.query("SELECT COUNT(*) FROM transactions WHERE id='existing'").use {
                it.moveToFirst(); assertEquals(1, it.getInt(0))
            }
            for (table in listOf("statement_imports", "statement_import_lines")) {
                db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            }
        }
    }
}
