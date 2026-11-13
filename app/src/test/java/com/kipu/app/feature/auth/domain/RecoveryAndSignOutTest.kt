package com.kipu.app.feature.auth.domain

import com.kipu.app.core.session.PendingChangesRepositoryImpl
import com.kipu.app.core.session.PendingChangesSource
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryAndSignOutTest {

    @Test
    fun `pending changes count sums outboxes across features`() = runTest {
        val ownerA = UUID.randomUUID()
        val ownerB = UUID.randomUUID()

        val planPreferencesDao = mockk<PlanPreferencesDao>()
        val profilePreferencesDao = mockk<ProfilePreferencesDao>()
        val instrumentSyncDao = mockk<InstrumentSyncDao>()
        val customSource = object : PendingChangesSource {
            override suspend fun count(userId: UUID): Int = if (userId == ownerA) 4 else 0
            override suspend fun markWaitingForAuth(userId: UUID) {}
        }

        coEvery { planPreferencesDao.pendingCount(ownerA) } returns 1
        coEvery { profilePreferencesDao.pendingOutboxCount(ownerA) } returns 2
        coEvery { instrumentSyncDao.getPendingCount(ownerA.toString()) } returns 3

        coEvery { planPreferencesDao.pendingCount(ownerB) } returns 0
        coEvery { profilePreferencesDao.pendingOutboxCount(ownerB) } returns 0
        coEvery { instrumentSyncDao.getPendingCount(ownerB.toString()) } returns 0

        val repo = PendingChangesRepositoryImpl(
            planPreferencesDao = planPreferencesDao,
            profilePreferencesDao = profilePreferencesDao,
            instrumentSyncDao = instrumentSyncDao,
            customSources = setOf(customSource),
        )

        assertEquals(10, repo.count(ownerA))
        assertEquals(0, repo.count(ownerB))
    }

    @Test
    fun `password reset rejects passwords not meeting complexity rules`() {
        val shortResult = PasswordValidator.validatePassword("P1")
        assertFalse(shortResult.isValid)

        val noNumberResult = PasswordValidator.validatePassword("PasswordOnly")
        assertFalse(noNumberResult.isValid)

        val validResult = PasswordValidator.validatePassword("ValidPassword123")
        assertTrue(validResult.isValid)
    }
}
