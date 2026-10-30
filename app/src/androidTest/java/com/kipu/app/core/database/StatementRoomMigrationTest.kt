package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatementRoomMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

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
