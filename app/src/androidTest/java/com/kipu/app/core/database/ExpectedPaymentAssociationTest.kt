package com.kipu.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.feature.capture.data.local.CaptureCandidateEntity
import com.kipu.app.feature.recurrences.data.local.ExpectedPaymentEntity
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real Room transaction and SQLite constraints, not a mocked check/update. */
class ExpectedPaymentAssociationTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KipuDatabase::class.java).build()
    private val payments = db.expectedPaymentDao()
    private val candidates = db.captureCandidateDao()
    private val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()

    @After fun close() = db.close()

    private fun candidate(id: String = "candidate") = CaptureCandidateEntity(
        id = id, ownerId = "owner", sourcePackage = "pkg", provider = "BCP", sourceEventFingerprint = id,
        status = "AUTO_REGISTERED", direction = "EXPENSE", amountMinor = 1000, currency = "PEN", occurredAt = now,
        occurredAtInferred = false, merchantSourceText = null, merchantId = null, categoryId = null, cardLast4 = null,
        instrumentKind = "DEBIT_ACCOUNT", accountId = "account", cardId = null, instrumentAmbiguous = false,
        confidence = "HIGH", confidenceReasons = "[]", creditMode = null, installmentCount = null,
        resolvedTransactionId = "tx", resolutionKey = "key", evidenceSnippet = null, evidenceExpiresAt = null,
        userCorrectedFields = "[]", createdAt = now, updatedAt = now, resolvedAt = now, expectedMatchState = "PENDING_CHOICE",
    )
    private fun payment(id: String, status: String = "EXPECTED", owner: String = "owner") = ExpectedPaymentEntity(
        id, owner, "rec", "2026-10-28", 1000, "PEN", status, null, null, now, now,
    )

    @Test fun concurrentLinksAllowOneExpectedAndRetrySamePairSucceeds() = runTest {
        candidates.insertIfAbsent(candidate())
        payments.insertIfAbsent(payment("one"))
        payments.insertIfAbsent(payment("two").copy(recurrenceId = "rec-two"))
        val results = listOf("one", "two").map { id -> async { id to payments.markAttended("owner", id, "tx", "candidate", now) } }.awaitAll()
        val winner = results.single { it.second == 1 }.first
        val loser = results.single { it.second == 0 }.first
        assertEquals("LINKED", candidates.get("owner", "candidate")?.expectedMatchState)
        assertEquals("EXPECTED", payments.get("owner", loser)?.status)
        assertEquals(1, payments.markAttended("owner", winner, "tx", "candidate", now + 1))
        assertEquals(0, payments.markAttended("owner", loser, "tx", "candidate", now + 1))
        assertEquals(now, payments.get("owner", winner)?.updatedAt)
        assertEquals(0, candidates.setExpectedMatchState("owner", "candidate", "NONE", now))
    }

    @Test fun pendingChoiceIsRecoverableAndNonePreventsLinking() = runTest {
        candidates.insertIfAbsent(candidate())
        payments.insertIfAbsent(payment("one"))
        assertEquals("candidate", candidates.observePendingExpectedMatches("owner").first().single().id)
        assertTrue(candidates.observePendingExpectedMatches("other").first().isEmpty())
        assertEquals(1, candidates.setExpectedMatchState("owner", "candidate", "NONE", now))
        assertEquals("NONE", candidates.get("owner", "candidate")?.expectedMatchState)
        assertTrue(candidates.observePendingExpectedMatches("owner").first().isEmpty())
        assertEquals(0, payments.markAttended("owner", "one", "tx", "candidate", now))
        assertEquals("EXPECTED", payments.get("owner", "one")?.status)
    }

    @Test fun templateSyncChangesOnlyExpectedRowsOfSameOwnerAndRecurrence() = runTest {
        payments.insertIfAbsent(payment("expected"))
        payments.insertIfAbsent(payment("attended", "ATTENDED").copy(dueDate = "2026-11-28"))
        payments.insertIfAbsent(payment("skipped", "SKIPPED").copy(dueDate = "2026-12-28"))
        payments.insertIfAbsent(payment("foreign", owner = "other"))
        payments.insertIfAbsent(payment("other-rec").copy(recurrenceId = "other-rec"))
        payments.syncExpectedTemplate("owner", "rec", 2500, "USD", now + 1)
        assertEquals(2500L, payments.get("owner", "expected")?.expectedAmountMinor)
        assertEquals("USD", payments.get("owner", "expected")?.currency)
        for (id in listOf("attended", "skipped", "other-rec")) {
            assertEquals(1000L, payments.get("owner", id)?.expectedAmountMinor)
            assertEquals("PEN", payments.get("owner", id)?.currency)
        }
        assertEquals(1000L, payments.get("other", "foreign")?.expectedAmountMinor)
    }

    @Test fun wrongOwnerOrTransactionOrUnavailableExpectedCannotLink() = runTest {
        candidates.insertIfAbsent(candidate())
        payments.insertIfAbsent(payment("one", "SKIPPED"))
        assertEquals(0, payments.markAttended("other", "one", "tx", "candidate", now))
        assertEquals(0, payments.markAttended("owner", "one", "wrong", "candidate", now))
        assertEquals(0, payments.markAttended("owner", "one", "tx", "candidate", now))
        assertEquals("PENDING_CHOICE", candidates.get("owner", "candidate")?.expectedMatchState)
    }
}
