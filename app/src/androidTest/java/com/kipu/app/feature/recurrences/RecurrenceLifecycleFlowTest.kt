package com.kipu.app.feature.recurrences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.kipu.app.feature.recurrences.domain.model.RecurrenceRule
import com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus
import com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState
import com.kipu.app.feature.recurrences.presentation.RecurrenceLifecycleScreen
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecurrenceLifecycleFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cancelConfirmationBackOutDispatchesNoLifecycleCommand() {
        var cancelCalls = 0
        val rule = recurrence("active-1", RecurrenceStatus.ACTIVE, RecurrenceSyncState.SYNCED)
        compose.setContent { lifecycle(listOf(rule), onCancel = { cancelCalls++ }) }

        compose.onNodeWithText("Cancelar recurrencia").performClick()
        compose.onNodeWithTag("recurrence_cancel_back").performClick()
        compose.runOnIdle { assertEquals("backing out must not send SET_STATUS", 0, cancelCalls) }
        compose.onNodeWithTag("recurrence_state_active-1").assertIsDisplayed()
    }

    @Test
    fun resumeShowsFutureDateAndBackOutSendsNoCommand() {
        var resumeCalls = 0
        val rule = recurrence("paused-1", RecurrenceStatus.PAUSED, RecurrenceSyncState.PENDING)
        val futureDate = LocalDate.now().plusDays(7)
        val previewDate = mutableStateOf<LocalDate?>(null)
        compose.setContent {
            lifecycle(
                listOf(rule),
                resumeDate = previewDate.value,
                onRequestResume = { previewDate.value = futureDate },
                onConfirmResume = { resumeCalls++ },
            )
        }

        compose.onNodeWithText("Reanudar").performClick()
        compose.onNodeWithText("La próxima fecha válida será $futureDate. No se crearán gastos de periodos omitidos.").assertIsDisplayed()
        compose.onNodeWithTag("recurrence_resume_back").performClick()
        compose.runOnIdle { assertEquals("dismissed resume preview must not send SET_STATUS", 0, resumeCalls) }
        compose.onNodeWithText("Pendiente de sincronización local").assertIsDisplayed()
    }

    @Test
    fun staleOfflineConflictAndRevisionAreVisible() {
        val stale = recurrence("conflict-1", RecurrenceStatus.ACTIVE, RecurrenceSyncState.CONFLICT, revision = 4)
        compose.setContent { lifecycle(listOf(stale)) }
        compose.onNodeWithTag("recurrence_state_conflict-1").assertIsDisplayed()
        compose.onNodeWithTag("recurrence_conflict_conflict-1").assertIsDisplayed()
        compose.onNodeWithText("Estado: ACTIVE · Próxima fecha: ${stale.nextDueDate} · Revisión 4").assertIsDisplayed()
    }

    @Test
    fun pauseResumeAndCancelDispatchOnlyTheirConfirmedActions() {
        val active = recurrence("active-actions", RecurrenceStatus.ACTIVE, RecurrenceSyncState.SYNCED)
        val paused = recurrence("paused-actions", RecurrenceStatus.PAUSED, RecurrenceSyncState.SYNCED)
        val previewDate = mutableStateOf<LocalDate?>(null)
        var pausedCount = 0
        var resumeCount = 0
        var cancelCount = 0
        compose.setContent {
            lifecycle(
                listOf(active, paused),
                resumeDate = previewDate.value,
                onPause = { pausedCount++ },
                onRequestResume = { previewDate.value = LocalDate.now().plusDays(3) },
                onConfirmResume = { resumeCount++ },
                onCancel = { cancelCount++ },
            )
        }

        compose.onNodeWithTag("recurrence_pause_active-actions").performClick()
        compose.onNodeWithTag("recurrence_resume_paused-actions").performClick()
        compose.onNodeWithTag("recurrence_resume_confirm").performClick()
        compose.onNodeWithTag("recurrence_cancel_active-actions").performClick()
        compose.onNodeWithTag("recurrence_cancel_confirm").performClick()
        compose.runOnIdle {
            assertEquals(1, pausedCount)
            assertEquals(1, resumeCount)
            assertEquals(1, cancelCount)
        }
    }

    @androidx.compose.runtime.Composable
    private fun lifecycle(
        rules: List<RecurrenceRule>,
        resumeDate: LocalDate? = null,
        onPause: (RecurrenceRule) -> Unit = {},
        onRequestResume: (RecurrenceRule) -> Unit = {},
        onCancel: (RecurrenceRule) -> Unit = {},
        onConfirmResume: () -> Unit = {},
    ) = RecurrenceLifecycleScreen(
        recurrences = rules,
        isLoading = false,
        errorMessage = null,
        onPause = onPause,
        onRequestResume = onRequestResume,
        onCancel = onCancel,
        onConfirmResume = onConfirmResume,
        onDismissConfirmation = {},
        resumeDate = resumeDate,
        onBack = {},
    )

    private fun recurrence(id: String, status: RecurrenceStatus, sync: RecurrenceSyncState, revision: Long = 2) = RecurrenceRule(
        id = id,
        userId = "user-1",
        templateTransaction = buildJsonObject { put("concept", "Alquiler") },
        amountMode = "FIXED",
        frequency = "MONTHLY",
        intervalCount = 1,
        customIntervalUnit = null,
        startDate = LocalDate.now().minusMonths(2),
        endDate = null,
        nextDueDate = LocalDate.now().plusDays(2),
        reminderLeadDays = 2,
        status = status,
        revision = revision,
        syncState = sync,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )
}
