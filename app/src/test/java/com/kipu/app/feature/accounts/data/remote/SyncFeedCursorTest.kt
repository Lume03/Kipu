package com.kipu.app.feature.accounts.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFeedCursorTest {
    @Test
    fun globalSequenceGapsFromOtherOwnersAreAValidPage() {
        // Real shape seen in production: one owner's changes skip 16 -> 23 and 35 -> 41.
        assertTrue(SyncFeedCursor.isValidPage(0L, page(listOf(1, 2, 16, 23, 35, 41), next = 41)))
        assertTrue(SyncFeedCursor.isValidPage(41L, page(listOf(64, 106), next = 106)))
    }

    @Test
    fun repeatedOrDecreasingSequencesAreRejected() {
        assertFalse(SyncFeedCursor.isValidPage(0L, page(listOf(3, 3), next = 3)))
        assertFalse(SyncFeedCursor.isValidPage(0L, page(listOf(5, 4), next = 5)))
        assertFalse(SyncFeedCursor.isValidPage(10L, page(listOf(10, 11), next = 11)))
    }

    @Test
    fun nextSequenceMustPointAtTheLastChange() {
        assertFalse(SyncFeedCursor.isValidPage(0L, page(listOf(1, 2), next = 3)))
        assertTrue(SyncFeedCursor.isValidPage(7L, page(emptyList(), next = 7)))
        assertFalse(SyncFeedCursor.isValidPage(7L, page(emptyList(), next = 9)))
    }

    @Test
    fun pullRequestAlwaysSendsEveryNamedRpcParameter() {
        // Same configuration as SupabaseNetworkModule: defaults are not encoded globally. A missing
        // parameter made every pull answer 404, so a reinstall never restored server data.
        val clientJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

        val body = clientJson.parseToJsonElement(clientJson.encodeToString(PullChangesRequestDto(afterSequence = 0L))).jsonObject

        assertEquals(setOf("contract_version", "after_sequence", "limit"), body.keys)
        assertEquals("1", body["contract_version"].toString())
        assertEquals("100", body["limit"].toString())
    }

    @Test
    fun feedTimestampsParseWithOffsetOrZulu() {
        assertEquals(1_791_004_612_616L, parseFeedInstant("2026-10-03T05:16:52.616547+00:00").toEpochMilli())
        assertEquals(1_791_004_612_616L, parseFeedInstant("2026-10-03T05:16:52.616547Z").toEpochMilli())
        assertEquals(1_791_004_612_000L, parseFeedInstant("2026-10-03T00:16:52-05:00").toEpochMilli())
    }

    private fun page(sequences: List<Int>, next: Int) = PullChangesResponseDto(
        changes = sequences.map { SyncChangeItemDto(it.toLong(), "ACCOUNT", "id-$it", 1L, "UPSERT", null) },
        nextSequence = next.toLong(),
    )
}
