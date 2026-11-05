package com.kipu.app.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kipu.app.feature.calendar.domain.*
import com.kipu.app.feature.notifications.presentation.NotificationsUiEvent
import com.kipu.app.feature.notifications.presentation.NotificationsUiState
import com.kipu.app.feature.notifications.presentation.NotificationsViewModel
import com.kipu.app.feature.recurrences.presentation.RecurrencePresentationState
import com.kipu.app.feature.recurrences.presentation.RecurrencePresentationViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Executes production destination builders while injecting presentation models, without a live Hilt/session graph. */
class ObjectSourceNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun missingCalendarRecurrenceShowsUnavailableAndReturnsToCalendar() {
        val model = mockk<RecurrencePresentationViewModel>(relaxed = true)
        every { model.state } returns MutableStateFlow(RecurrencePresentationState(isLoading = false))
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = CALENDAR_ROUTE) {
                composable(CALENDAR_ROUTE) { Text("Calendario de prueba") }
                calendarRecurrenceDestination(controller) { model }
            }
        }
        compose.runOnIdle { controller.navigateToCalendarEventSource(event("deleted")) }
        compose.onNodeWithText("Objeto no disponible: el registro de origen ya no existe.").assertIsDisplayed()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithText("Calendario de prueba").assertIsDisplayed()
        compose.runOnIdle { assertEquals(CALENDAR_ROUTE, controller.currentDestination?.route) }
        io.mockk.coVerify(exactly = 0) { model.create(any()) }
        io.mockk.verify(exactly = 0) { model.pause(any()); model.cancel(any()); model.confirmResume() }
    }

    @Test
    fun calendarRecurrenceRouteShowsOnlyItsSourceAndDoesNotCreatePayments() {
        val rule = com.kipu.app.feature.recurrences.domain.model.RecurrenceRule(
            id = "wanted", userId = "owner", templateTransaction = kotlinx.serialization.json.buildJsonObject {
                put("concept", kotlinx.serialization.json.JsonPrimitive("Internet previsto"))
            }, amountMode = "FIXED", frequency = "MONTHLY", intervalCount = 1, customIntervalUnit = null,
            startDate = LocalDate.parse("2026-11-01"), endDate = null, nextDueDate = LocalDate.parse("2026-12-01"),
            reminderLeadDays = 2, status = com.kipu.app.feature.recurrences.domain.model.RecurrenceStatus.ACTIVE,
            revision = 1, syncState = com.kipu.app.feature.recurrences.domain.model.RecurrenceSyncState.SYNCED,
            createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH,
        )
        val model = mockk<RecurrencePresentationViewModel>(relaxed = true)
        every { model.state } returns MutableStateFlow(RecurrencePresentationState(
            recurrences = listOf(rule, rule.copy(id = "other", templateTransaction = kotlinx.serialization.json.buildJsonObject {
                put("concept", kotlinx.serialization.json.JsonPrimitive("Otra obligación"))
            })), isLoading = false))
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = CALENDAR_ROUTE) {
                composable(CALENDAR_ROUTE) { Text("Calendario") }
                calendarRecurrenceDestination(controller) { model }
            }
        }
        compose.runOnIdle { controller.navigateToCalendarEventSource(event("wanted")) }
        compose.onNodeWithText("Internet previsto").assertIsDisplayed()
        compose.onNodeWithText("Otra obligación").assertDoesNotExist()
        compose.runOnIdle { assertEquals("wanted", controller.currentBackStackEntry?.arguments?.getString("recurrenceId")) }
        io.mockk.verify(exactly = 0) { model.create(any()); model.pause(any()); model.cancel(any()); model.confirmResume() }
    }

    @Test
    fun notificationOpenDebtRoutesToDebtDetailWithSourceId() {
        checkNotificationEvent(NotificationsUiEvent.OpenDebt("debt-1"), "debts/detail/{debtId}", "debtId", "debt-1")
    }

    @Test
    fun notificationOpenGoalRoutesToGoalDetailWithSourceId() {
        checkNotificationEvent(NotificationsUiEvent.OpenGoal("goal-1"), "goals/{goalId}", "goalId", "goal-1")
    }

    private fun checkNotificationEvent(event: NotificationsUiEvent, targetRoute: String, argument: String, id: String) {
        val events = Channel<NotificationsUiEvent>(Channel.BUFFERED)
        val model = mockk<NotificationsViewModel>(relaxed = true)
        every { model.state } returns MutableStateFlow(NotificationsUiState())
        every { model.unreadCount } returns MutableStateFlow(0)
        every { model.events } returns events.receiveAsFlow()
        lateinit var controller: NavHostController
        compose.setContent {
            controller = rememberNavController()
            NavHost(controller, startDestination = NOTIFICATION_CENTER_ROUTE) {
                notificationDestinations(controller) { model }
                composable(targetRoute, listOf(navArgument(argument) { type = NavType.StringType })) {
                    Text("Detalle de $id")
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { check(events.trySend(event).isSuccess) }
        compose.onNodeWithText("Detalle de $id").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(targetRoute, controller.currentDestination?.route)
            assertEquals(id, controller.currentBackStackEntry?.arguments?.getString(argument))
        }
        events.close()
    }

    private fun event(id: String) = CalendarEvent("event", LocalDate.parse("2026-11-05"), CalendarEventKind.RECURRENCE,
        CalendarEventStatus.PLANNED, "Esperado", source = CalendarEventSource.Recurrence(id))
}
