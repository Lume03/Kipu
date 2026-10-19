package com.kipu.app.feature.recurrences.domain

import com.kipu.app.feature.recurrences.domain.model.RecurrenceCommandResult
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.schedule.RecurrenceScheduleCalculator
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class RecurrenceResumePreview(
    val recurrenceId: String,
    val expectedRevision: Long,
    val firstFutureDueDate: LocalDate,
)

class RecurrenceLifecycleUseCase @Inject constructor(
    private val repository: RecurrenceRepository,
    private val clock: Clock,
) {
    fun previewResume(recurrence: RecurrenceRule): RecurrenceResumePreview {
        require(recurrence.status == RecurrenceStatus.PAUSED) { "Only paused recurrence may resume" }
        val firstFuture = RecurrenceScheduleCalculator.firstOccurrenceAfter(
            recurrence.startDate, recurrence.frequency, recurrence.intervalCount,
            recurrence.customIntervalUnit, LocalDate.now(clock),
        )
        require(recurrence.endDate == null || firstFuture <= recurrence.endDate) { "No future due date remains before end date" }
        return RecurrenceResumePreview(recurrence.id, recurrence.revision, firstFuture)
    }

    suspend fun resume(recurrence: RecurrenceRule, preview: RecurrenceResumePreview, confirmed: Boolean): RecurrenceCommandResult? {
        if (!confirmed || recurrence.status != RecurrenceStatus.PAUSED ||
            preview.recurrenceId != recurrence.id || preview.expectedRevision != recurrence.revision
        ) return null
        val currentPreview = previewResume(recurrence)
        if (currentPreview.firstFutureDueDate != preview.firstFutureDueDate) return null
        return repository.setStatus(recurrence.userId, recurrence.id, recurrence.revision, "ACTIVE", preview.firstFutureDueDate)
    }

    suspend fun pause(recurrence: RecurrenceRule): RecurrenceCommandResult =
        repository.setStatus(recurrence.userId, recurrence.id, recurrence.revision, "PAUSED")

    suspend fun cancel(recurrence: RecurrenceRule, confirmed: Boolean): RecurrenceCommandResult? {
        if (!confirmed || recurrence.status == RecurrenceStatus.CANCELLED) return null
        return repository.setStatus(recurrence.userId, recurrence.id, recurrence.revision, "CANCELLED")
    }
}
