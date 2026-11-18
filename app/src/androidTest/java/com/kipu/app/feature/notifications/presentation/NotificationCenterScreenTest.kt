package com.kipu.app.feature.notifications.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.Density
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationCenterScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun failedReadShowsRetryWithoutTheEmptySuccessMessage() {
        var retries = 0
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(errorMessage = "No pudimos cargar tus notificaciones.", hasLoadError = true),
                    unreadCount = 0, onFilterSelected = {}, onMarkAllRead = {}, onMarkRead = {},
                    onDismiss = {}, onOpenNotification = {}, onNavigateBack = {},
                    onRefresh = { retries++ }, reducedMotion = true,
                )
            }
        }
        compose.onNodeWithTag("notification-error").assertIsDisplayed()
        compose.onNodeWithText("Todo al día. No tienes avisos pendientes").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
        captureIssue99("notifications-read-error")
    }

    @Test
    fun refreshingKeepsCachedContentVisibleAtTwoHundredPercent() {
        val notice = AppNotification(
            id = "qa", userId = "qa-owner", title = "Aviso de prueba", body = "Datos sintéticos",
            notificationType = "SYSTEM", referenceEntityType = null, referenceEntityId = null,
            isRead = false, createdAt = Instant.parse("2026-11-16T00:00:00Z"), deletedAt = null,
        )
        val notices = listOf(notice, notice.copy(id = "qa-read", title = "Otro aviso de prueba", isRead = true))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KipuTheme(darkTheme = true) {
                    NotificationCenterScreen(
                        state = NotificationsUiState(notifications = notices, visibleNotifications = notices, isRefreshing = true),
                        unreadCount = 1, onFilterSelected = {}, onMarkAllRead = {}, onMarkRead = {},
                        onDismiss = {}, onOpenNotification = {}, onNavigateBack = {}, reducedMotion = true,
                    )
                }
            }
        }
        compose.onNodeWithText("Actualizando…").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Aviso de prueba", useUnmergedTree = true).assertIsDisplayed()
        captureIssue99("notifications-dark-font200-refresh")
        compose.onNodeWithContentDescription("Borrar leídas").assertIsDisplayed()
        val actionBounds = compose.onNodeWithText("Marcar todas como leídas").getUnclippedBoundsInRoot()
        val screenBounds = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("La acción debe caber al 200 % sin recortes laterales",
            actionBounds.left >= screenBounds.left && actionBounds.right <= screenBounds.right)
    }

    private fun captureIssue99(name: String) {
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.filesDir, "issue99-evidence/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun emptyCenterShowsApprovedCopyOnFirstLocalRender() {
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(isLoading = false),
                    unreadCount = 0,
                    onFilterSelected = {},
                    onMarkAllRead = {},
                    onMarkRead = {},
                    onDismiss = {},
                    onOpenNotification = {},
                    onNavigateBack = {},
                )
            }
        }

        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Todo al día. No tienes avisos pendientes").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Todo al día. No tienes avisos pendientes").assertIsDisplayed()
    }

    @Test
    fun filterChipsReportAlertAndReminderSelection() {
        var selected: NotificationFilter? = null
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(isLoading = false),
                    unreadCount = 0,
                    onFilterSelected = { selected = it },
                    onMarkAllRead = {},
                    onMarkRead = {},
                    onDismiss = {},
                    onOpenNotification = {},
                    onNavigateBack = {},
                )
            }
        }

        listOf(
            "Alertas" to NotificationFilter.ALERTS,
            "Recordatorios" to NotificationFilter.REMINDERS,
            "Presupuestos" to NotificationFilter.BUDGETS,
            "Metas" to NotificationFilter.GOALS,
        ).forEach { (label, filter) ->
            compose.onNodeWithTag("notification-filter-button").performClick()
            compose.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(filter, selected)
        }
    }

    @Test
    fun largeTextKeepsRealFiltersAndHeaderActionReachable() {
        var selected: NotificationFilter? = null
        var readAllCount = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KipuTheme {
                    NotificationCenterScreen(
                        state = NotificationsUiState(),
                        unreadCount = 1,
                        onFilterSelected = { selected = it },
                        onMarkAllRead = { readAllCount++ },
                        onMarkRead = {},
                        onDismiss = {},
                        onOpenNotification = {},
                        onNavigateBack = {},
                        reducedMotion = true,
                    )
                }
            }
        }

        compose.onNodeWithText("Notificaciones").assertIsDisplayed()
        compose.onNodeWithText("Marcar todas como leídas").assertIsDisplayed().performClick()
        NotificationFilter.entries.reversed().forEach { filter ->
            compose.onNodeWithTag("notification-filter-button").performClick()
            compose.onAllNodesWithText(filter.label).onLast().performScrollTo().assertIsDisplayed().performClick()
            assertEquals(filter, selected)
        }
        assertEquals(1, readAllCount)
    }

    @Test
    fun billingReminderKeepsExpectedDateVisibleAndDoesNotClaimPayment() {
        val reminder = AppNotification(
            id = "notice-1",
            userId = "user-1",
            title = "Cuota próxima",
            body = "Vence el 30 de septiembre",
            notificationType = "BILLING_DUE",
            referenceEntityType = "DEBT",
            referenceEntityId = "debt-1",
            isRead = false,
            createdAt = Instant.parse("2026-09-26T10:00:00Z"),
            deletedAt = null,
        )
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(
                        activeUserId = "user-1",
                        notifications = listOf(reminder),
                        visibleNotifications = listOf(reminder),
                        isLoading = false,
                    ),
                    unreadCount = 1,
                    onFilterSelected = {},
                    onMarkAllRead = {},
                    onMarkRead = {},
                    onDismiss = {},
                    onOpenNotification = {},
                    onNavigateBack = {},
                )
            }
        }

        compose.onNodeWithText("Cuota próxima").assertIsDisplayed()
        compose.onNodeWithText("Vence el 30 de septiembre").assertIsDisplayed()
        compose.onNodeWithText("Recordatorio").assertIsDisplayed()
    }

    @Test
    fun billingReminderRendersStructuredDueDateAsExpectedFutureDate() {
        val reminder = AppNotification(
            id = "notice-2",
            userId = "user-1",
            title = "Cuota próxima",
            body = "Se acerca tu vencimiento.",
            notificationType = "BILLING_DUE",
            referenceEntityType = "DEBT",
            referenceEntityId = "debt-1",
            isRead = false,
            createdAt = Instant.parse("2026-09-26T10:00:00Z"),
            deletedAt = null,
            eventPayloadJson = """{"due_date":"2026-09-30"}""",
        )
        compose.setContent {
            KipuTheme {
                NotificationCenterScreen(
                    state = NotificationsUiState(
                        activeUserId = "user-1",
                        notifications = listOf(reminder),
                        visibleNotifications = listOf(reminder),
                        isLoading = false,
                    ),
                    unreadCount = 1,
                    onFilterSelected = {},
                    onMarkAllRead = {},
                    onMarkRead = {},
                    onDismiss = {},
                    onOpenNotification = {},
                    onNavigateBack = {},
                    reducedMotion = true,
                )
            }
        }

        // Android locale data varies in the spelling and capitalization of September.
        compose.onNode(
            hasText("Vence el 30 de setiembre de 2026", ignoreCase = true) or
                hasText("Vence el 30 de septiembre de 2026", ignoreCase = true),
            useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recordatorio", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("notification-row-notice-2")
            .assertContentDescriptionContains("Vence el 30 de", substring = true)
            .assertContentDescriptionContains("Se acerca tu vencimiento.", substring = true)
    }
}
