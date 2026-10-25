package com.kipu.app.feature.accounts.data.remote

import java.time.Instant
import java.time.OffsetDateTime

/**
 * Cursor rules for `pull_financial_changes_v1`. The feed sequence is global across owners
 * (docs/arquitectura/modelo-de-datos.md), so one user's page legitimately skips values
 * committed by other users: a page is valid when it is strictly increasing after the
 * checkpoint and `next_sequence` points at its last change.
 */
object SyncFeedCursor {
    fun isValidPage(checkpoint: Long, page: PullChangesResponseDto): Boolean {
        var previous = checkpoint
        for (change in page.changes) {
            if (change.sequence <= previous) return false
            previous = change.sequence
        }
        return page.nextSequence == previous
    }
}

/**
 * Feed timestamps come as `2026-10-03T05:16:52.616547+00:00`. `Instant.parse` on Android only
 * accepts a `Z` suffix (it threw on device), so parse the offset form explicitly; `Z` still works.
 */
fun parseFeedInstant(value: String): Instant = OffsetDateTime.parse(value).toInstant()
