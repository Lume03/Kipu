package com.kipu.app.feature.notifications.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import com.kipu.app.feature.notifications.data.local.LocalReminderEventDao
import com.kipu.app.feature.notifications.data.local.LocalReminderEventEntity
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxDao
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxEntity
import com.kipu.app.feature.notifications.data.remote.NotificationDto
import com.kipu.app.feature.notifications.data.remote.NotificationsApi
import com.kipu.app.feature.notifications.data.remote.NotificationsApiResponse
import com.kipu.app.feature.notifications.data.sync.NotificationSyncScheduler

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFirstNotificationsRepositoryTest {
    @Test
    fun `observe requests only the selected account and maps cached rows`() = runTest {
        val dao = mockk<AppNotificationDao>()
        val row = entity("notice-a", USER_A, read = false)
        io.mockk.every { dao.observeActive(USER_A) } returns flowOf(listOf(row))
        val repository = repository(dao = dao)

        val result = repository.observeActive(USER_A).first()

        assertEquals(listOf("notice-a"), result.map { it.id })
        assertEquals(USER_A, result.single().userId)
        assertEquals("REMOTE", originName(result.single()))
        io.mockk.verify { dao.observeActive(USER_A) }
    }

    @Test
    fun `observe merges remote and local reminders in deterministic order for selected owner`() = runTest {
        val dao = mockk<AppNotificationDao>()
        val localDao = mockk<LocalReminderEventDao>()
        io.mockk.every { dao.observeActive(USER_A) } returns flowOf(listOf(entity("remote", USER_A)))
        io.mockk.every { localDao.observeActive(USER_A) } returns flowOf(
            listOf(localEvent("local-reminder:v1:abc", USER_A)),
        )
        val repository = repository(dao = dao, localDao = localDao)

        val result = repository.observeActive(USER_A).first()

        assertEquals(listOf("remote", "local-reminder:v1:abc"), result.map { it.id })
        assertEquals(listOf("REMOTE", "LOCAL_REMINDER"), result.map(::originName))
        assertEquals(USER_A, result[1].userId)
        assertEquals("Recordatorio de vencimiento", result[1].title)
        assertEquals("Recordatorio local de vencimiento.", result[1].body)
        assertEquals("{\"due_date\":\"2026-09-30\"}", result[1].eventPayloadJson)
        io.mockk.verify { dao.observeActive(USER_A) }
        io.mockk.verify { localDao.observeActive(USER_A) }
        io.mockk.verify(exactly = 0) { localDao.observeActive(USER_B) }
    }

    @Test
    fun `unread count includes active remote and local events for selected owner`() = runTest {
        val dao = mockk<AppNotificationDao>()
        val localDao = mockk<LocalReminderEventDao>()
        io.mockk.every { dao.observeUnreadCount(USER_A) } returns flowOf(2)
        io.mockk.every { localDao.observeUnreadCount(USER_A) } returns flowOf(3)
        val repository = repository(dao = dao, localDao = localDao)

        assertEquals(5, repository.observeUnreadCount(USER_A).first())
        io.mockk.verify { dao.observeUnreadCount(USER_A) }
        io.mockk.verify { localDao.observeUnreadCount(USER_A) }
    }

    @Test
    fun `refresh ignores other owners and protects pending local state while retaining tombstones`() = runTest {
        val dao = mockk<AppNotificationDao>()
        val outbox = mockk<NotificationSyncOutboxDao>()
        val api = mockk<NotificationsApi>()
        val pending = NotificationSyncOutboxEntity(
            operationId = "op-a",
            userId = USER_A,
            notificationId = "pending-local",
            isRead = true,
            deletedAt = null,
            createdAt = NOW,
        )
        coEvery { outbox.getPending(USER_A) } returns listOf(pending)
        coEvery { api.fetchForUser(USER_A) } returns NotificationsApiResponse.Success(
            listOf(
                dto("pending-local", USER_A, read = false),
                dto(
                    "remote-a",
                    USER_A,
                    eventPayload = Json.parseToJsonElement("""{"due_date":"2026-09-30"}""").jsonObject,
                ),
                dto("remote-tombstone", USER_A, deletedAt = NOW.toString()),
                dto("foreign", USER_B),
            ),
        )
        val saved = slot<List<AppNotificationEntity>>()
        coEvery { dao.upsertAll(capture(saved)) } returns Unit
        val repository = repository(dao = dao, outbox = outbox, api = api)

        val result = repository.refresh(USER_A)

        assertTrue(result.isSuccess)
        assertEquals(setOf("remote-a", "remote-tombstone"), saved.captured.map { it.id }.toSet())
        assertTrue(saved.captured.all { it.userId == USER_A })
        assertTrue(saved.captured.single { it.id == "remote-tombstone" }.deletedAt != null)
        assertEquals(
            "{\"due_date\":\"2026-09-30\"}",
            saved.captured.single { it.id == "remote-a" }.eventPayloadJson,
        )
        coVerify(exactly = 0) { api.patchForUser(any(), any(), any()) }
        coVerify(exactly = 0) { outbox.upsert(any()) }
    }

    @Test
    fun `refresh failure leaves cache as the observed source`() = runTest {
        val dao = mockk<AppNotificationDao>()
        val api = mockk<NotificationsApi>()
        io.mockk.every { dao.observeActive(USER_A) } returns flowOf(listOf(entity("cached", USER_A)))
        coEvery { api.fetchForUser(USER_A) } returns NotificationsApiResponse.NetworkFailure(IllegalStateException("offline"))
        val repository = repository(dao = dao, api = api)

        assertTrue(repository.refresh(USER_A).isFailure)
        assertEquals("cached", repository.observeActive(USER_A).first().single().id)
    }

    private fun repository(
        dao: AppNotificationDao = mockk(relaxed = true),
        localDao: LocalReminderEventDao? = null,
        outbox: NotificationSyncOutboxDao = mockk(relaxed = true),
        api: NotificationsApi = mockk(relaxed = true),
    ): OfflineFirstNotificationsRepository {
        val reminders = localDao ?: mockk<LocalReminderEventDao>(relaxed = true).also { local ->
            io.mockk.every { local.observeActive(any()) } returns flowOf(emptyList())
            io.mockk.every { local.observeUnreadCount(any()) } returns flowOf(0)
        }
        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R = block()
        }
        val scheduler = object : NotificationSyncScheduler {
            override fun scheduleSync(userId: String) = Unit
        }
        val dependencies = listOf<Any>(dao, reminders, outbox, api, transactionRunner, scheduler, Clock.fixed(NOW, ZoneOffset.UTC))
        val constructor = OfflineFirstNotificationsRepository::class.java.declaredConstructors
            .maxBy { it.parameterCount }
        val arguments = constructor.parameterTypes.map { parameter ->
            dependencies.firstOrNull { parameter.isInstance(it) }
                ?: error("No test dependency for repository constructor parameter ${parameter.name}")
        }.toTypedArray()
        return constructor.newInstance(*arguments) as OfflineFirstNotificationsRepository
    }

    private fun originName(notification: Any): String =
        notification.javaClass.getMethod("getOrigin").invoke(notification).toString()

    private fun entity(id: String, userId: String, read: Boolean = false) = AppNotificationEntity(
        id = id,
        userId = userId,
        title = "Notice $id",
        body = "Body",
        notificationType = "SYSTEM",
        referenceEntityType = null,
        referenceEntityId = null,
        isRead = read,
        createdAt = NOW,
        eventPayloadJson = "{\"due_date\":\"2026-09-30\"}",
    )

    private fun localEvent(id: String, userId: String) = LocalReminderEventEntity(
        id = id,
        userId = userId,
        sourceId = "debt-a",
        dueDate = "2026-09-30",
        createdAt = NOW,
    )

    private fun dto(
        id: String,
        userId: String,
        read: Boolean = false,
        deletedAt: String? = null,
        eventPayload: JsonObject? = null,
    ) = NotificationDto(
        id = id,
        userId = userId,
        title = "Notice $id",
        body = "Body",
        notificationType = "SYSTEM",
        referenceEntityType = null,
        referenceEntityId = null,
        isRead = read,
        createdAt = NOW.toString(),
        deletedAt = deletedAt,
        eventPayload = eventPayload,
    )

    private companion object {
        const val USER_A = "61000000-0000-4000-8000-000000000001"
        const val USER_B = "61000000-0000-4000-8000-000000000002"
        val NOW: Instant = Instant.parse("2026-09-26T10:00:00Z")
    }
}
