package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureRoomMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test fun addingCaptureTablesPreservesExistingFinancialHistory() {
        val name = "capture-v28-to-v29"
        helper.createDatabase(name, 28).use { db ->
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
        helper.runMigrationsAndValidate(name, 29, true, MIGRATION_28_29).use { db ->
            db.query("SELECT SUM(signed_amount_minor) FROM ledger_entries WHERE user_id='owner'").use {
                it.moveToFirst(); assertEquals(-1250L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM transactions WHERE id='movement'").use {
                it.moveToFirst(); assertEquals(1L, it.getLong(0))
            }
            db.query("PRAGMA table_info(capture_candidates)").use { cursor ->
                var found = false
                var followups = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "followups_pending") {
                        followups = true
                        assertEquals("INTEGER", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                        assertEquals("0", cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
                    }
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "expected_match_state") {
                        found = true
                        assertEquals("TEXT", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                    }
                }
                org.junit.Assert.assertTrue(found)
                org.junit.Assert.assertTrue(followups)
            }
            for (table in listOf("capture_source_events", "capture_candidates", "recurrence_expected_payments")) {
                db.query("SELECT COUNT(*) FROM $table").use {
                    it.moveToFirst(); assertEquals(0L, it.getLong(0))
                }
            }
        }
    }

    @Test fun captureUniqueIndexesRejectDuplicatesPerOwner() {
        val name = "capture-v29-unique"
        helper.createDatabase(name, 28).close()
        helper.runMigrationsAndValidate(name, 29, true, MIGRATION_28_29).use { db ->
            db.execSQL("INSERT INTO capture_source_events VALUES ('o1','fp','pkg',1,'CANDIDATE_CREATED')")
            db.execSQL("INSERT OR IGNORE INTO capture_source_events VALUES ('o1','fp','pkg',2,'DUPLICATE')")
            db.execSQL("INSERT INTO capture_source_events VALUES ('o2','fp','pkg',3,'CANDIDATE_CREATED')")
            db.query("SELECT COUNT(*) FROM capture_source_events").use {
                it.moveToFirst(); assertEquals(2L, it.getLong(0))
            }
            db.execSQL("""INSERT INTO recurrence_expected_payments VALUES ('e1','o1','r1','2026-11-05',1000,'PEN','EXPECTED',NULL,NULL,1,1)""")
            db.execSQL("""INSERT OR IGNORE INTO recurrence_expected_payments VALUES ('e2','o1','r1','2026-11-05',1000,'PEN','EXPECTED',NULL,NULL,2,2)""")
            db.query("SELECT COUNT(*) FROM recurrence_expected_payments").use {
                it.moveToFirst(); assertEquals(1L, it.getLong(0))
            }
            db.execSQL("UPDATE recurrence_expected_payments SET matched_candidate_id='candidate', status='ATTENDED' WHERE id='e1'")
            db.execSQL("INSERT OR IGNORE INTO recurrence_expected_payments VALUES ('e3','o1','r2','2026-11-05',1000,'PEN','ATTENDED','tx','candidate',2,2)")
            db.execSQL("INSERT INTO recurrence_expected_payments VALUES ('e4','o2','r2','2026-11-05',1000,'PEN','ATTENDED','tx','candidate',2,2)")
            db.execSQL("INSERT INTO recurrence_expected_payments VALUES ('e5','o1','r3','2026-11-05',1000,'PEN','EXPECTED',NULL,NULL,2,2)")
            db.execSQL("INSERT INTO recurrence_expected_payments VALUES ('e6','o1','r4','2026-11-05',1000,'PEN','EXPECTED',NULL,NULL,2,2)")
            db.query("SELECT COUNT(*) FROM recurrence_expected_payments").use {
                it.moveToFirst(); assertEquals(4L, it.getLong(0))
            }
        }
    }
}
