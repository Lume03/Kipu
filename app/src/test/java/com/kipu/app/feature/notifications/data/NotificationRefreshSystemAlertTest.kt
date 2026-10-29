package com.kipu.app.feature.notifications.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import com.kipu.app.feature.notifications.data.local.LocalReminderEventDao
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxDao
import com.kipu.app.feature.notifications.data.remote.NotificationDto
import com.kipu.app.feature.notifications.data.remote.NotificationsApi
import com.kipu.app.feature.notifications.data.remote.NotificationsApiResponse
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRefreshSystemAlertTest {
    @Test
    fun `refresh posts only recent notices first seen on this device`() = runTest {
        val dao = mockk<AppNotificationDao>(relaxed = true)
        coEvery { dao.getAllIds(USER_ID) } returns listOf("known")
        val api = mockk<NotificationsApi>()
        coEvery { api.fetchForUser(USER_ID) } returns NotificationsApiResponse.Success(
            listOf(
                dto("known", NOW.minus(Duration.ofMinutes(5))),
                dto("fresh", NOW.minus(Duration.ofMinutes(5))),
                dto("old", NOW.minus(Duration.ofDays(3))),
            ),
        )
        val published = mutableListOf<String>()
        val repository = repository(dao, api) { published += it.id }

        val result = repository.refresh(USER_ID)

        assertTrue(result.isSuccess)
        assertEquals(listOf("fresh"), published)
    }

    @Test
    fun `publishable notices are unread active budget or credit alerts`() {
        val base = entity("x", "CREDIT_UTILIZATION_THRESHOLD_CROSSED")
        assertTrue(FinancialAlertSystemNotifier.isPublishable(base))
        assertTrue(FinancialAlertSystemNotifier.isPublishable(base.copy(notificationType = "budget_alert")))
        assertFalse(FinancialAlertSystemNotifier.isPublishable(base.copy(notificationType = "SYSTEM")))
        assertFalse(FinancialAlertSystemNotifier.isPublishable(base.copy(isRead = true)))
        assertFalse(FinancialAlertSystemNotifier.isPublishable(base.copy(deletedAt = NOW)))
    }

    private fun repository(
        dao: AppNotificationDao,
        api: NotificationsApi,
        publish: (AppNotificationEntity) -> Unit,
    ): OfflineFirstNotificationsRepository {
        val outbox = mockk<NotificationSyncOutboxDao>()
        coEvery { outbox.getPending(USER_ID) } returns emptyList()
        return OfflineFirstNotificationsRepository(
            notificationDao = dao,
            localReminderEventDao = mockk<LocalReminderEventDao>(relaxed = true),
            outboxDao = outbox,
            api = api,
            transactionRunner = object : DatabaseTransactionRunner {
                override suspend operator fun <R> invoke(block: suspend () -> R): R = block()
            },
            syncScheduler = mockk(relaxed = true),
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
            systemNotifier = object : FinancialAlertSystemNotifier {
                override fun publish(notice: AppNotificationEntity) = publish(notice)
            },
        )
    }

    private fun dto(id: String, createdAt: Instant) = NotificationDto(
        id = id,
        userId = USER_ID,
        title = "Uso de crédito: 50%",
        body = "La utilización de tu tarjeta alcanzó el umbral de 50%.",
        notificationType = "CREDIT_UTILIZATION_THRESHOLD_CROSSED",
        referenceEntityType = "CARD",
        referenceEntityId = "card-1",
        isRead = false,
        createdAt = createdAt.toString(),
    )

    private fun entity(id: String, type: String) = AppNotificationEntity(
        id = id,
        userId = USER_ID,
        title = "t",
        body = "b",
        notificationType = type,
        referenceEntityType = null,
        referenceEntityId = null,
        isRead = false,
        createdAt = NOW,
        deletedAt = null,
        eventPayloadJson = "{}",
    )

    private companion object {
        const val USER_ID = "user-1"
        val NOW: Instant = Instant.parse("2026-10-28T23:00:00Z")
    }
}
