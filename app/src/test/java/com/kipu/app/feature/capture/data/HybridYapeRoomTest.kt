package com.kipu.app.feature.capture.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.database.MIGRATION_35_36
import com.kipu.app.feature.capture.data.local.*
import com.kipu.app.feature.capture.domain.*
import com.kipu.app.feature.receipts.data.local.RoomReceiptCandidateStore
import com.kipu.app.feature.receipts.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class HybridYapeRoomTest {
    private lateinit var db: KipuDatabase
    private lateinit var receipts: RoomReceiptCandidateStore
    private lateinit var captures: RoomCaptureCandidateStore
    private val owner = UUID.randomUUID().toString()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).build()
        receipts = RoomReceiptCandidateStore(db, db.receiptCandidateDao())
        captures = RoomCaptureCandidateStore(db, db.captureCandidateDao())
    }
    @After fun close() = db.close()
    private fun notification(ref: String? = "00123", id: String = UUID.randomUUID().toString()) = captureCandidate(owner, id).copy(
        operationReference = ref, sourcePackage = "com.bcp.innovacxion.yapeapp", provider = CaptureProvider.YAPE,
        amountMinor = 5000, merchantSourceText = "José Pérez", occurredAt = 1_000_000, direction = CaptureDirection.INCOME)
    private fun fields(ref: String? = "00123") = YapeReceiptFields(5000, 1_000_000, "Jose Perez", ref, isIncome = true)

    @Test fun notificationThenShareKeepsSingleCandidateAndEditedFieldsEvenAfterConfirmation() = runBlocking {
        val n = notification()
        assertTrue(captures.insertIfAbsent(n))
        captures.updatePending(n.copy(amountMinor = 9999, userCorrectedFields = listOf("amountMinor")))
        assertEquals(ReceiptCandidateSaveResult.Duplicate(n.id), receipts.import(owner, "image", fields().copy(amountMinor = 9999), true))
        assertEquals(9999L, captures.get(owner, n.id)?.amountMinor)
        assertTrue(captures.markResolved(owner, n.id, CaptureCandidateStatus.CONFIRMED, "movement", "resolution", emptyList(), 20))
        assertEquals(ReceiptCandidateSaveResult.Duplicate(n.id), receipts.import(owner, "image-2", fields().copy(amountMinor = 9999), true))
        assertEquals("movement", RoomCaptureCorrelation(db).movement(owner, "capture:${n.id}"))
        assertEquals(1, db.captureEvidenceDao().all(owner).map { it.root }.distinct().size)
        assertTrue(receipts.observe(owner).first().isEmpty())
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ledger_entries").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
    @Test fun shareThenNotificationKeepsReceiptAndNoSecondCandidateForConfirmedOrPending() = runBlocking {
        val r = (receipts.import(owner, "image", fields(), true) as ReceiptCandidateSaveResult.Saved).candidate
        receipts.edit(owner, r.id, ReceiptCandidateEdit(7777, r.occurredAt, "Editado", r.reference, true))
        assertFalse(captures.insertIfAbsent(notification().copy(amountMinor = 7777)))
        assertEquals(7777L, receipts.get(owner, r.id)?.amountMinor)
        receipts.markConfirmed(owner, r.id)
        assertFalse(captures.insertIfAbsent(notification().copy(amountMinor = 7777)))
        assertTrue(captures.observePending(owner).first().isEmpty())
        assertEquals(1, db.captureEvidenceDao().all(owner).map { it.root }.distinct().size)
    }
    @Test fun missingReferenceProposesAndExplicitMergeMovesEvidenceWithoutLedger() = runBlocking {
        val n = notification(null)
        captures.insertIfAbsent(n)
        val r = (receipts.import(owner, "image", fields(), true) as ReceiptCandidateSaveResult.Saved).candidate
        val service = RoomCaptureCorrelation(db)
        assertEquals("capture:${n.id}", service.suggestion(owner, "receipt:${r.id}"))
        assertTrue(service.merge(owner, "receipt:${r.id}", "capture:${n.id}"))
        assertEquals(ReceiptCandidateStatus.DISCARDED, receipts.get(owner, r.id)?.status)
        assertEquals(1, db.captureEvidenceDao().all(owner).map { it.root }.distinct().size)
        assertFalse(service.merge(owner, "receipt:${r.id}", "capture:${n.id}"))
    }
    @Test fun ambiguousOrDistinctReferencesDoNotMergeAndOwnersStayIsolated() = runBlocking {
        captures.insertIfAbsent(notification(null))
        captures.insertIfAbsent(notification(null))
        val r = (receipts.import(owner, "image", fields(), true) as ReceiptCandidateSaveResult.Saved).candidate
        assertNull(RoomCaptureCorrelation(db).suggestion(owner, "receipt:${r.id}"))
        assertTrue(captures.insertIfAbsent(notification("00234")))
        assertEquals(3, captures.observePending(owner).first().size)
        assertTrue(receipts.import(UUID.randomUUID().toString(), "image", fields(), true) is ReceiptCandidateSaveResult.Saved)
    }
    @Test fun keepingSeparateSurvivesReloadAndFailedAttachmentRollsBack() = runBlocking {
        val n = notification(null)
        captures.insertIfAbsent(n)
        val r = (receipts.import(owner, "image", fields(), true) as ReceiptCandidateSaveResult.Saved).candidate
        val root = "receipt:${r.id}"
        RoomCaptureCorrelation(db).separate(owner, root)
        assertNull(RoomCaptureCorrelation(db).suggestion(owner, root))
        assertNull(RoomCaptureCorrelation(db).suggestion(owner, "capture:${n.id}"))
        val before = db.captureEvidenceDao().all(owner).size
        try {
            db.withTransaction {
                receipts.import(owner, "rollback-image", fields("new-reference"), true)
                error("Simulated interrupted write")
            }
        } catch (_: IllegalStateException) { }
        assertEquals(before, db.captureEvidenceDao().all(owner).size)
        assertEquals(1, receipts.observe(owner).first().size)
        assertTrue(receipts.import(owner, "rollback-image", fields("new-reference"), true) is ReceiptCandidateSaveResult.Saved)
    }

    @Test fun migrationAddsOnlyEvidenceAndUniqueReferenceIsPerOwnerAndSource() {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("DROP TABLE capture_evidence")
        sql.execSQL("ALTER TABLE receipt_candidates RENAME TO receipt_candidates_v36")
        sql.execSQL("CREATE TABLE receipt_candidates (candidate_id TEXT PRIMARY KEY NOT NULL, amount_minor INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO receipt_candidates VALUES ('legacy',5000,1000,2000)")
        MIGRATION_35_36.migrate(sql)
        sql.query("SELECT amount_minor,fields_edited FROM receipt_candidates WHERE candidate_id='legacy'").use {
            it.moveToFirst(); assertEquals(5000L, it.getLong(0)); assertEquals(1, it.getInt(1))
        }
        sql.execSQL("INSERT INTO capture_evidence VALUES ('owner','notification','one','capture:one','REF',0)")
        sql.execSQL("INSERT INTO capture_evidence VALUES ('owner','ocr','two','capture:one','REF',0)")
        sql.execSQL("INSERT INTO capture_evidence VALUES ('other','ocr','three','receipt:three','REF',0)")
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            sql.execSQL("INSERT INTO capture_evidence VALUES ('owner','ocr','four','receipt:four','REF',0)")
        }
    }
    @Test fun conflictingReferenceFactsStaySeparateAndMigrationSeedsOnce() = runBlocking {
        val n = notification()
        assertTrue(captures.insertIfAbsent(n))
        assertTrue(captures.insertIfAbsent(notification().copy(amountMinor = 6000)))
        assertTrue(captures.insertIfAbsent(notification().copy(currency = "USD")))
        assertTrue(captures.insertIfAbsent(notification().copy(direction = CaptureDirection.EXPENSE)))
        assertEquals(4, captures.observePending(owner).first().size)
        db.openHelper.writableDatabase.execSQL("DELETE FROM capture_evidence")
        com.kipu.app.core.database.MIGRATION_36_37.migrate(db.openHelper.writableDatabase)
        assertEquals(4, db.captureEvidenceDao().all(owner).size)
        assertEquals(3, db.captureEvidenceDao().nearbyFacts(owner, 5000, 400000, 1600000).size)
        assertTrue(db.captureEvidenceDao().nearbyFacts(owner, 5000, 2000000, 3000000).isEmpty())
    }
}
