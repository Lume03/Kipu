package com.kipu.app.feature.movements.data.local

import org.junit.Assert.*
import org.junit.Test

class MovementRevisionAuditMapperTest {
    private fun revision(kind: String, base: Long, local: Long, time: Long, origin: String = "LOCAL") =
        TransactionRevisionEntity("owner", "r$time", "tx", "c$time", kind, base, local,
            "retained draft", "snapshot", null, origin, time)

    @Test fun `rejected correction retains draft and is not presented as requested void`() {
        val audit = listOf(revision("REVISE", 1, 2, 10),
            revision("VOID", 2, 3, 20, "RECONCILIATION")).toMovementRevisionAudit()
        assertEquals("CORRECTION_REJECTED", audit.first().operation)
        assertEquals("retained draft", audit.first().rejectedSnapshot)
    }

    @Test fun `later successful attempt supersedes rejection even with lower revision number`() {
        val audit = listOf(revision("REVISE", 1, 2, 10),
            revision("VOID", 2, 3, 20, "RECONCILIATION"),
            revision("REVISE", 1, 2, 30)).toMovementRevisionAudit()
        assertEquals("REVISE", audit.first().operation)
        assertNull(audit.first().rejectedSnapshot)
    }

    @Test fun `requested void and rejected registration do not become rejected corrections`() {
        assertEquals("VOID", listOf(revision("VOID", 1, 2, 10)).toMovementRevisionAudit().first().operation)
        assertNull(listOf(revision("VOID", 1, 2, 10, "RECONCILIATION")).toMovementRevisionAudit().first().rejectedSnapshot)
    }

    @Test fun `newer official head from another device supersedes retained rejection`() {
        val revisions = listOf(revision("REVISE", 1, 2, 10), revision("VOID", 2, 3, 20, "RECONCILIATION"))
        assertNotNull(revisions.toMovementRevisionAudit(currentRevision = 1).first().rejectedSnapshot)
        assertNull(revisions.toMovementRevisionAudit(currentRevision = 2).first().rejectedSnapshot)
    }
}
