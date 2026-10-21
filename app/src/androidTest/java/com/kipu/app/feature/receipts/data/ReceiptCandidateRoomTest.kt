package com.kipu.app.feature.receipts.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.receipts.data.local.RoomReceiptCandidateStore
import com.kipu.app.feature.receipts.domain.ReceiptCandidateSaveResult
import com.kipu.app.feature.receipts.domain.ReceiptCandidateStatus
import com.kipu.app.feature.receipts.domain.YapeReceiptFields
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import kotlin.test.assertIs
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ReceiptCandidateRoomTest {
    private lateinit var database: KipuDatabase
    private lateinit var store: RoomReceiptCandidateStore

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KipuDatabase::class.java).build()
        store = RoomReceiptCandidateStore(database, database.receiptCandidateDao())
    }

    @After fun tearDown() = database.close()

    @Test fun deduplicatesImageAndReferencePerOwnerButNotSameAmountWithAnotherReference() = runBlocking {
        val owner = UUID.randomUUID().toString()
        val one = YapeReceiptFields(1200, 10, "Persona", "ref-1", emptyList())
        val created = store.import(owner, "image-hash-1", one, false) as ReceiptCandidateSaveResult.Saved
        val imageDuplicate = store.import(owner, "image-hash-1", one, false)
        assertIs<ReceiptCandidateSaveResult.Duplicate>(imageDuplicate)
        val referenceDuplicate = store.import(owner, "image-hash-2", one, false)
        assertIs<ReceiptCandidateSaveResult.Duplicate>(referenceDuplicate)
        val distinctPayment = store.import(owner, "image-hash-3", one.copy(reference = "ref-2"), false)
        assertIs<ReceiptCandidateSaveResult.Saved>(distinctPayment)
        assertEquals(2, store.observe(owner).first().size)
        assertNotEquals(created.candidate.id, (distinctPayment as ReceiptCandidateSaveResult.Saved).candidate.id)
    }

    @Test fun editConfirmDiscardAreOwnerScopedAndOnlyPendingRowsMutate() = runBlocking {
        val owner = UUID.randomUUID().toString()
        val candidate = (store.import(owner, "img", YapeReceiptFields(null, null, null, null, listOf("amount")), null)
            as ReceiptCandidateSaveResult.Saved).candidate
        assertEquals(null, store.edit(UUID.randomUUID().toString(), candidate.id,
            com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit(500, 1, "Name", "ref")))
        assertEquals(true, store.markConfirmed(owner, candidate.id))
        assertEquals(false, store.discard(owner, candidate.id))
        assertEquals(ReceiptCandidateStatus.CONFIRMED, store.get(owner, candidate.id)?.status)
        assertEquals(null, store.edit(owner, candidate.id,
            com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit(999, 1, "Cambio", "other-ref")))

        val pending = (store.import(owner, "img-2", YapeReceiptFields(null, null, null, null, emptyList()), null)
            as ReceiptCandidateSaveResult.Saved).candidate
        assertEquals(true, store.discard(owner, pending.id))
        assertEquals(emptyList<Any>(), store.observe(owner).first())
    }

    @Test fun correctedReferenceParticipatesInDeduplicationAndCannotStealAnotherCandidateReference() = runBlocking {
        val owner = UUID.randomUUID().toString()
        val first = (store.import(owner, "unreadable", YapeReceiptFields(), null) as ReceiptCandidateSaveResult.Saved).candidate
        val edit = com.kipu.app.feature.receipts.domain.ReceiptCandidateEdit(500, 1, "Persona", "00012345", false)
        val corrected = store.edit(owner, first.id, edit)
        org.junit.Assert.assertNotNull(corrected?.referenceFingerprint)
        assertEquals(ReceiptCandidateSaveResult.Duplicate(first.id), store.import(owner, "another-photo", YapeReceiptFields(reference = "00012345"), false))
        val other = (store.import(owner, "other-payment", YapeReceiptFields(reference = "00012346"), false) as ReceiptCandidateSaveResult.Saved).candidate
        assertEquals(null, store.edit(owner, other.id, edit))
        assertEquals("00012346", store.get(owner, other.id)?.reference)
    }
}
