package com.kipu.app.core.sync

import com.kipu.app.feature.accounts.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SharedChangeFeedTest {
    private fun change(sequence: Long, type: String = "CATEGORY") = SyncChangeItemDto(sequence, type, "id", 1, "UPSERT")

    @Test fun mixedPageIsDownloadedOnceAndAllModulesReceiveTheirSuffix() = runTest {
        val cursors = longArrayOf(0, 0, 0)
        val seen = mutableListOf<String>()
        var downloads = 0
        val modules = listOf("MOVEMENT", "DEBT", "GOAL").mapIndexed { index, type ->
            FeedModule({ cursors[index] }) { page ->
                seen += page.changes.filter { it.entityType == type }.map { it.entityType }
                cursors[index] = page.nextSequence
                true
            }
        }
        assertTrue(SharedFeedReader().pull({ downloads++; FinancialApiResponse.Success(PullChangesResponseDto(listOf(change(1,"MOVEMENT"),change(2,"DEBT"),change(3,"GOAL")),3)) }, modules))
        assertEquals(1, downloads)
        assertEquals(listOf("MOVEMENT", "DEBT", "GOAL"), seen)
        assertArrayEquals(longArrayOf(3,3,3), cursors)
    }

    @Test fun failedModuleKeepsCursorWhileOtherModulesFinishLaterPages() = runTest {
        val cursors = longArrayOf(0,0,0)
        val modules = cursors.indices.map { i -> FeedModule({ cursors[i] }) { p ->
            if(i == 1) false else { cursors[i] = p.nextSequence; true }
        } }
        val downloaded = mutableListOf<Long>()
        assertFalse(SharedFeedReader().pull({ c -> downloaded += c
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(change(c+1)),c+1,c==0L))
        }, modules))
        assertEquals(listOf(0L,1L), downloaded)
        assertArrayEquals(longArrayOf(2,0,2),cursors)
    }

    @Test fun restartAfterProcessDeathUsesOnlyUncommittedModuleSuffix() = runTest {
        val cursors = longArrayOf(0,0,0)
        var killed = false
        val applied = IntArray(3)
        fun modules() = cursors.indices.map { i -> FeedModule({ cursors[i] }) { page ->
            if(i==1 && !killed) { killed=true; throw CancellationException("process stopped") }
            applied[i]++; cursors[i]=page.nextSequence; true
        } }
        val page = PullChangesResponseDto(listOf(change(5)),5)
        try { SharedFeedReader().pull({ FinancialApiResponse.Success(page) },modules()); fail() } catch (_: CancellationException) { }
        assertArrayEquals(longArrayOf(5,0,0),cursors)
        assertTrue(SharedFeedReader().pull({ c -> assertEquals(0L,c); FinancialApiResponse.Success(page) },modules()))
        assertArrayEquals(intArrayOf(1,1,1), applied)
    }

    @Test fun schema41CursorsAreReusedWithoutResetAndSkewIsHandledWithinPage() = runTest {
        val cursors = longArrayOf(100,105,110)
        val applied = mutableListOf<List<Long>>()
        val modules = cursors.indices.map { i -> FeedModule({cursors[i]}) { page ->
            applied += page.changes.map { it.sequence }; cursors[i]=page.nextSequence; true
        } }
        assertTrue(SharedFeedReader().pull({ c -> assertEquals(100L,c)
            FinancialApiResponse.Success(PullChangesResponseDto(listOf(change(103),change(105),change(110)),110))
        },modules))
        assertEquals(listOf(listOf(103L,105L,110L),listOf(110L)),applied)
        assertArrayEquals(longArrayOf(110,110,110),cursors)
    }

    @Test fun unknownTypeAndMalformedCursorAreRejectedBeforeAnyModuleWrites() = runTest {
        var writes=0
        val module=FeedModule({0}) { writes++; true }
        for(page in listOf(PullChangesResponseDto(listOf(change(1,"FUTURE_TYPE")),1),
            PullChangesResponseDto(listOf(change(2),change(1)),1), PullChangesResponseDto(emptyList(),0,true))) {
            assertFalse(SharedFeedReader().pull({FinancialApiResponse.Success(page)},listOf(module)))
        }
        assertEquals(0,writes)
    }
}
