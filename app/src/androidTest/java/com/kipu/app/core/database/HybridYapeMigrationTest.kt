package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HybridYapeMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)
    @Test fun v35To36PreservesPendingReceiptsAndValidatesEntireSchema() {
        val name = "hybrid-yape-35-36"
        helper.createDatabase(name, 35).use { db ->
            db.execSQL("INSERT INTO receipt_candidates (candidate_id,owner_id,provider,amount_minor,occurred_at,recipient,reference,reference_fingerprint,image_fingerprint,status,created_at,updated_at,is_income) VALUES ('candidate','owner','YAPE',5000,1000,'Persona','00123','hash','image','PENDING',1000,1000,1)")
        }
        helper.runMigrationsAndValidate(name, 36, true, MIGRATION_35_36).use { db ->
            db.query("SELECT amount_minor FROM receipt_candidates WHERE candidate_id='candidate'").use {
                it.moveToFirst(); assertEquals(5000L, it.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM capture_evidence").use { it.moveToFirst(); assertEquals(0L, it.getLong(0)) }
        }
    }
}
