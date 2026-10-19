package com.kipu.app.feature.recurrences.domain

import com.kipu.app.feature.recurrences.domain.model.RecurrenceCommandResult
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceLifecycleTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC)
    private val recurrence = RecurrenceRule(
        id = "rule-1", userId = "owner-1", templateTransaction = buildJsonObject { }, amountMode = "FIXED",
        frequency = "MONTHLY", intervalCount = 1, customIntervalUnit = null,
        startDate = LocalDate.of(2026, 8, 31), endDate = null, nextDueDate = LocalDate.of(2026, 8, 31),
        reminderLeadDays = 2, status = RecurrenceStatus.PAUSED, revision = 4,
        syncState = RecurrenceSyncState.SYNCED, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun resumePreviewUsesFirstFutureDateFromOriginalAnchorAndConfirmationStagesOneCommand() = runBlocking {
        val repository = RecordingRecurrenceRepository()
        val useCase = RecurrenceLifecycleUseCase(repository, clock)
        val preview = useCase.previewResume(recurrence)
        assertEquals(LocalDate.of(2026, 10, 31), preview.firstFutureDueDate)
        assertEquals(4L, preview.expectedRevision)
        assertEquals(0, repository.statusCalls)
        useCase.resume(recurrence, preview, confirmed = true)
        assertEquals(1, repository.statusCalls)
        assertEquals("ACTIVE", repository.target)
        assertEquals(LocalDate.of(2026, 10, 31), repository.confirmedDate)
        assertEquals(4L, repository.expectedRevision)
    }

    @Test
    fun abandoningResumePreviewEmitsNoCommandAndDoesNotChangePausedRevision() = runBlocking {
        val repository = RecordingRecurrenceRepository()
        val useCase = RecurrenceLifecycleUseCase(repository, clock)
        val result = useCase.resume(recurrence, useCase.previewResume(recurrence), confirmed = false)
        assertNull(result)
        assertEquals(0, repository.statusCalls)
        assertEquals(RecurrenceStatus.PAUSED, recurrence.status)
        assertEquals(4L, recurrence.revision)
    }

    @Test
    fun staleResumePreviewIsRejectedWithoutStagingCommand() = runBlocking {
        val repository = RecordingRecurrenceRepository()
        val useCase = RecurrenceLifecycleUseCase(repository, clock)
        val changedRevision = recurrence.copy(revision = 5)
        val result = useCase.resume(changedRevision, useCase.previewResume(recurrence), confirmed = true)
        assertNull(result)
        assertEquals(0, repository.statusCalls)
    }

    @Test
    fun cancelWithoutExplicitConfirmationEmitsNoCommand() = runBlocking {
        val repository = RecordingRecurrenceRepository()
        val useCase = RecurrenceLifecycleUseCase(repository, clock)
        val result = useCase.cancel(recurrence, confirmed = false)
        assertNull(result)
        assertEquals(0, repository.statusCalls)
    }

    private class RecordingRecurrenceRepository : RecurrenceRepository {
        var statusCalls = 0
        var target: String? = null
        var confirmedDate: LocalDate? = null
        var expectedRevision: Long? = null
        override fun observeAll(userId: String): Flow<List<RecurrenceRule>> = emptyFlow()
        override fun observe(userId: String, recurrenceId: String): Flow<RecurrenceRule?> = emptyFlow()
        override suspend fun create(userId: String, draft: RecurrenceDraft): RecurrenceCommandResult = error("unused")
        override suspend fun update(userId: String, recurrenceId: String, expectedRevision: Long, draft: RecurrenceUpdate): RecurrenceCommandResult = error("unused")
        override suspend fun setStatus(userId: String, recurrenceId: String, expectedRevision: Long, target: String, confirmedNextDueDate: LocalDate?): RecurrenceCommandResult {
            statusCalls++
            this.target = target
            this.confirmedDate = confirmedNextDueDate
            this.expectedRevision = expectedRevision
            return RecurrenceCommandResult.Rejected("TEST", "recorded")
        }
        override suspend fun reconcileReceipt(receipt: RecurrenceCommandReceipt) = Unit
        override suspend fun applyChangeFeed(userId: String, sequence: Long, changes: List<RecurrenceRule>) = Unit
    }
}
