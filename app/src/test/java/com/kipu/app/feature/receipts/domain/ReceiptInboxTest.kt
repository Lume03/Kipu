package com.kipu.app.feature.receipts.domain

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class ReceiptInboxTest {
    private fun row(id: String, amount: Long = 1000, income: Boolean? = false, time: Long = 1) =
        newReceiptCandidate("owner", id, YapeReceiptFields(amount, 123, "Carlos", "op-$id", isIncome = income), income, time).copy(id = id)

    @Test fun `preview adds income subtracts expense and excludes incomplete or settled rows`() {
        val rows = listOf(row("income", 12000, true), row("expense", 3550),
            row("unknown", 5000, null), row("missing").copy(reference = null),
            row("settled", 5000, true).copy(status = ReceiptCandidateStatus.CONFIRMED))
        assertEquals(BigInteger.valueOf(8450), receiptBalanceEffect(rows))
        assertEquals(1, rows[2].missingDataCount)
        assertEquals(1, rows[3].missingDataCount)
    }

    @Test fun `preview does not overflow long or use floating point`() {
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.TWO,
            receiptBalanceEffect(listOf(row("a", Long.MAX_VALUE, true), row("b", Long.MAX_VALUE, true))))
    }

    @Test fun `queue uses newest first skips discarded and stops after final pending id`() {
        val rows = listOf(row("old", time = 1), row("new", time = 3), row("middle", time = 2),
            row("discarded", time = 4).copy(status = ReceiptCandidateStatus.DISCARDED))
        val queue = ReceiptReviewQueue.start(rows)
        assertEquals(listOf("new", "middle", "old"), queue.ids)
        assertEquals("1 de 3", queue.position)
        val next = queue.next(setOf("old"))!!
        assertEquals("old", next.currentId)
        assertEquals("3 de 3", next.position)
        assertNull(next.next(emptySet()))
        assertEquals("middle", ReceiptReviewQueue.start(rows, "middle").currentId)
    }

    @Test fun `each import failure and duplicate status maps to the matching notice`() {
        assertEquals(ReceiptImportNotice.NOT_YAPE, receiptImportNotice(ReceiptImportResult.NotYape))
        assertEquals(ReceiptImportNotice.UNREADABLE, receiptImportNotice(ReceiptImportResult.Unreadable))
        assertEquals(ReceiptImportNotice.UNSUPPORTED_FORMAT, receiptImportNotice(ReceiptImportResult.UnsupportedFormat))
        val result = ReceiptImportResult.Duplicate("same")
        assertEquals(ReceiptImportNotice.ALREADY_PENDING, receiptImportNotice(result, ReceiptCandidateStatus.PENDING))
        assertEquals(ReceiptImportNotice.ALREADY_REGISTERED, receiptImportNotice(result, ReceiptCandidateStatus.CONFIRMED))
        assertEquals(ReceiptImportNotice.ALREADY_DISCARDED, receiptImportNotice(result, ReceiptCandidateStatus.DISCARDED))
        assertNull(receiptImportNotice(ReceiptImportResult.AccessDenied))
        assertNull(receiptImportNotice(ReceiptImportResult.CandidateCreated(row("new"))))
    }

    @Test fun `footer waits for confirmation during autoplay but appears on manual or reduced motion`() {
        assertEquals(listOf(4.2f, 4.4f, 5.6f), ReceiptGuideTimeline.durations)
        assertFalse(ReceiptGuideTimeline.footerVisible(1, 10f, false, false))
        assertFalse(ReceiptGuideTimeline.footerVisible(2, 3.8f, false, false))
        assertTrue(ReceiptGuideTimeline.footerVisible(2, 4.9f, false, false))
        assertTrue(ReceiptGuideTimeline.footerVisible(2, 0f, true, false))
        assertTrue(ReceiptGuideTimeline.footerVisible(0, 0f, false, true))
    }
}
