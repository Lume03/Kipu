package com.kipu.app.feature.notifications.data

import com.kipu.app.core.database.DatabaseTransactionRunner
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.LocalReminderEventDao
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxDao
import com.kipu.app.feature.notifications.data.remote.NotificationsApi
import com.kipu.app.feature.notifications.data.sync.NotificationSyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class LocalReminderCenterTest {
    @Test
    fun `marking a local reminder read only updates owner scoped local storage`() = runTest {
        val localDao = mockk<LocalReminderEventDao>()
        coEvery { localDao.markRead(USER_A, LOCAL_ID) } returns 1
        val outbox = mockk<NotificationSyncOutboxDao>(relaxed = true)
        val api = mockk<NotificationsApi>(relaxed = true)
        val scheduler = RecordingScheduler()
        val repository = repository(localDao, outbox, api, scheduler)

        assertTrue(repository.markRead(USER_A, LOCAL_ID).isSuccess)

        coVerify(exactly = 1) { localDao.markRead(USER_A, LOCAL_ID) }
        coVerify(exactly = 0) { outbox.upsert(any()) }
        coVerify(exactly = 0) { api.patchForUser(any(), any(), any()) }
        assertTrue(scheduler.scheduledUsers.isEmpty())
    }

    @Test
    fun `dismissing a local reminder tombstones local row without remote sync`() = runTest {
        val localDao = mockk<LocalReminderEventDao>()
        coEvery { localDao.dismiss(USER_A, LOCAL_ID, any()) } returns 1
        val outbox = mockk<NotificationSyncOutboxDao>(relaxed = true)
        val api = mockk<NotificationsApi>(relaxed = true)
        val scheduler = RecordingScheduler()
        val repository = repository(localDao, outbox, api, scheduler)

        assertTrue(repository.dismiss(USER_A, LOCAL_ID).isSuccess)

        coVerify(exactly = 1) { localDao.dismiss(USER_A, LOCAL_ID, Instant.parse("2026-09-26T10:00:00Z")) }
        coVerify(exactly = 0) { outbox.upsert(any()) }
        coVerify(exactly = 0) { api.patchForUser(any(), any(), any()) }
        assertTrue(scheduler.scheduledUsers.isEmpty())
    }

    @Test
    fun `mark all read updates local reminders without scheduling remote sync`() = runTest {
        val localDao = mockk<LocalReminderEventDao>()
        coEvery { localDao.markAllRead(USER_A) } returns 1
        val outbox = mockk<NotificationSyncOutboxDao>(relaxed = true)
        val api = mockk<NotificationsApi>(relaxed = true)
        val scheduler = RecordingScheduler()
        val repository = repository(localDao, outbox, api, scheduler)

        assertTrue(repository.markAllRead(USER_A).isSuccess)

        coVerify(exactly = 1) { localDao.markAllRead(USER_A) }
        coVerify(exactly = 0) { outbox.upsert(any()) }
        coVerify(exactly = 0) { api.patchForUser(any(), any(), any()) }
        assertTrue(scheduler.scheduledUsers.isEmpty())
    }

    private fun repository(
        localDao: LocalReminderEventDao,
        outbox: NotificationSyncOutboxDao,
        api: NotificationsApi,
        scheduler: RecordingScheduler,
    ): OfflineFirstNotificationsRepository {
        val transactionRunner = object : DatabaseTransactionRunner {
            override suspend operator fun <R> invoke(block: suspend () -> R): R = block()
        }
        val dependencies = listOf<Any>(
            mockk<AppNotificationDao>(relaxed = true),
            localDao,
            outbox,
            api,
            transactionRunner,
            scheduler,
            Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC),
            FinancialAlertSystemNotifier.None,
        )
        val constructor = OfflineFirstNotificationsRepository::class.java.declaredConstructors
            .maxBy { it.parameterCount }
        val arguments = constructor.parameterTypes.map { parameter ->
            dependencies.firstOrNull { parameter.isInstance(it) }
                ?: error("No test dependency for repository constructor parameter ${parameter.name}")
        }.toTypedArray()
        return constructor.newInstance(*arguments) as OfflineFirstNotificationsRepository
    }

    private class RecordingScheduler : NotificationSyncScheduler {
        val scheduledUsers = mutableListOf<String>()

        override fun scheduleSync(userId: String) {
            scheduledUsers += userId
        }
    }

    private companion object {
        const val USER_A = "61000000-0000-4000-8000-000000000001"
        const val LOCAL_ID = "local-reminder:v1:abc"
    }
}
