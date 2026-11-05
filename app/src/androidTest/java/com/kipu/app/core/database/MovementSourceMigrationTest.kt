package com.kipu.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.receipts.domain.receiptRegistrationKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class MovementSourceMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KipuDatabase::class.java)

    @Test fun sourceBackfillUsesOwnerEvidenceAndKeepsUnlinkedHistoryManual() {
        val name = "source-migration-${UUID.randomUUID()}"
        helper.createDatabase(name, 39).use { db ->
            listOf("manual", "notification", "ocr", "foreign-notification", "foreign-ocr", "both", "unresolved").forEach { id ->
                db.execSQL("""INSERT INTO transactions(id,user_id,type,amount_minor,currency_code,occurred_at,status,sync_status,created_at,updated_at)
                    VALUES (?, 'owner','INCOME',100,'PEN',1000,'ACTIVE','PENDING',1,1)""", arrayOf(id))
            }
            fun capture(id: String, owner: String, transaction: String?) {
                db.execSQL("""INSERT INTO capture_candidates(id,owner_id,source_package,provider,source_event_fingerprint,status,
                    occurred_at_inferred,instrument_ambiguous,confidence,confidence_reasons,user_corrected_fields,created_at,updated_at,resolved_transaction_id)
                    VALUES (?,?,'test.package','YAPE',?,'CONFIRMED',0,0,'HIGH','[]','[]',1,1,?)""", arrayOf(id, owner, id, transaction))
            }
            fun receipt(id: String, owner: String, transaction: String) {
                db.execSQL("""INSERT INTO receipt_candidates(candidate_id,owner_id,provider,image_fingerprint,status,created_at,updated_at)
                    VALUES (?,?,'YAPE',?,'CONFIRMED',1,1)""", arrayOf(id, owner, id))
                db.execSQL("""INSERT INTO local_command_receipts(user_id,idempotency_key,request_hash,transaction_id,status,created_at,updated_at)
                    VALUES (?,?,'hash',?,'APPLIED',1,1)""", arrayOf(owner, receiptRegistrationKey(owner, id), transaction))
            }
            capture("noti", "owner", "notification")
            capture("foreign-noti", "someone-else", "foreign-notification")
            capture("pending", "owner", null)
            capture("both-noti", "owner", "both")
            receipt("ocr-candidate", "owner", "ocr")
            receipt("foreign-ocr-candidate", "someone-else", "foreign-ocr")
            receipt("both-ocr", "owner", "both")
        }
        helper.runMigrationsAndValidate(name, 40, true, MIGRATION_39_40).use { db ->
            val actual = mutableMapOf<String, String>()
            db.query("SELECT id,capture_source FROM transactions").use { while (it.moveToNext()) actual[it.getString(0)] = it.getString(1) }
            assertEquals(mapOf("manual" to "MANUAL", "notification" to "NOTIFICATION", "ocr" to "OCR",
                "foreign-notification" to "MANUAL", "foreign-ocr" to "MANUAL", "both" to "NOTIFICATION", "unresolved" to "MANUAL"), actual)
        }
        // A new open validates persisted backfill rather than a one-shot cursor result.
        helper.runMigrationsAndValidate(name, 40, true).use { db ->
            db.query("SELECT COUNT(*) FROM transactions WHERE capture_source='MANUAL'").use { it.moveToFirst(); assertEquals(4L, it.getLong(0)) }
        }
    }
}
