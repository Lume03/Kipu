package com.kipu.app.feature.capture.domain

import com.kipu.app.feature.capture.data.CaptureReviewNotifier
import com.kipu.app.feature.capture.data.CaptureReviewSystemNotifier
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.usecase.SaveMerchantAliasRule
import com.kipu.app.feature.categories.domain.usecase.SetMerchantCategoryPreference
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import io.mockk.*
import java.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CaptureReviewAndLearningTest {
    @Test fun `publication failure after notice insert remains retryable using the same notice identity`() = runTest {
        val candidate = captureCandidate().copy(followupsPending = CaptureFollowup.PUBLISH_REVIEW)
        val store = FakeCaptureCandidateStore(candidate)
        val dao = mockk<AppNotificationDao>()
        val system = mockk<CaptureReviewSystemNotifier>(relaxed = true)
        val notices = mutableListOf<AppNotificationEntity>()
        coEvery { dao.insertIfAbsent(any()) } answers { notices += firstArg<AppNotificationEntity>(); if (notices.size == 1) 1L else -1L }
        coEvery { dao.getById(any(), any()) } answers { notices.first() }
        var attempts = 0
        every { system.publish(any(), any()) } answers { attempts++; if (attempts == 1) error("interrupted Android publish") }
        val notifier = CaptureReviewNotifier(dao, store, FakeCaptureAccessGate(), system, Clock.systemUTC(), TestCaptureTransactions)
        try { notifier.process(candidate); org.junit.Assert.fail("expected failure") } catch (_: IllegalStateException) { }
        org.junit.Assert.assertEquals(CaptureFollowup.PUBLISH_REVIEW, store.get(candidate.ownerId, candidate.id)!!.followupsPending)
        notifier.process(candidate)
        notifier.process(candidate)
        org.junit.Assert.assertEquals(2, attempts)
        org.junit.Assert.assertEquals(1, notices.map { it.id }.distinct().size)
        org.junit.Assert.assertEquals(0, store.get(candidate.ownerId, candidate.id)!!.followupsPending)
    }

    @Test fun `failed preference result is not acknowledged and replay retries learning`() = runTest {
        val owner = "61000000-0000-4000-8000-000000000001"
        val repository = mockk<CategoriesRepository>()
        val alias = mockk<SaveMerchantAliasRule>()
        val preference = mockk<SetMerchantCategoryPreference>()
        val candidate = captureCandidate(owner).copy(status = CaptureCandidateStatus.CONFIRMED,
            resolvedTransactionId = "tx", followupsPending = CaptureFollowup.LEARN,
            userCorrectedFields = listOf(CaptureCorrectedField.CATEGORY.name))
        val store = FakeCaptureCandidateStore(candidate)
        every { repository.observePremiumVerified(com.kipu.app.core.finance.domain.model.UserId(owner)) } returns kotlinx.coroutines.flow.flowOf(true)
        every { repository.observeMerchantCategoryPreferences(com.kipu.app.core.finance.domain.model.UserId(owner)) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { preference(com.kipu.app.core.finance.domain.model.UserId(owner), any(), any()) } returnsMany listOf(Result.failure(IllegalStateException("temporary")), Result.success(mockk()))
        val learner = LearnFromCaptureCorrection(repository, alias, preference, FakeCaptureAccessGate(owner))
        val followups = CaptureResolutionFollowups(store, setOf(learner), emptySet())
        org.junit.Assert.assertFalse(followups.process(candidate.ownerId, candidate.id))
        org.junit.Assert.assertEquals(CaptureFollowup.LEARN, store.get(candidate.ownerId, candidate.id)!!.followupsPending)
        org.junit.Assert.assertTrue(followups.process(candidate.ownerId, candidate.id))
        org.junit.Assert.assertEquals(0, store.get(candidate.ownerId, candidate.id)!!.followupsPending)
        coVerify(exactly = 2) { preference(com.kipu.app.core.finance.domain.model.UserId(owner), any(), any()) }
    }
    @Test fun `review notice is inserted and published once and scoped to owner`() = runTest {
        val candidate = captureCandidate()
        val dao = mockk<AppNotificationDao>()
        val system = mockk<CaptureReviewSystemNotifier>(relaxed = true)
        coEvery { dao.insertIfAbsent(any()) } returnsMany listOf(1L, -1L)
        val notifier = CaptureReviewNotifier(dao, FakeCaptureCandidateStore(candidate), FakeCaptureAccessGate(), system, Clock.systemUTC(), TestCaptureTransactions)
        notifier.process(candidate)
        notifier.process(candidate)
        notifier.process(candidate.copy(ownerId = "other"))
        coVerify(exactly = 2) { dao.insertIfAbsent(match { it.id == CaptureReviewNotifier.key(candidate.ownerId, candidate.id) && it.userId == candidate.ownerId }) }
        verify(exactly = 1) { system.publish(candidate, any<AppNotificationEntity>()) }
    }

    @Test fun `learning ignores unresolved discarded automatic unchanged and other owners`() = runTest {
        val repository = mockk<CategoriesRepository>()
        val alias = mockk<SaveMerchantAliasRule>()
        val preference = mockk<SetMerchantCategoryPreference>()
        val learner = LearnFromCaptureCorrection(repository, alias, preference, FakeCaptureAccessGate())
        val base = captureCandidate()
        val corrected = listOf(CaptureCorrectedField.MERCHANT.name, CaptureCorrectedField.CATEGORY.name)
        learner.onResolved(base.copy(status = CaptureCandidateStatus.PENDING, userCorrectedFields = corrected), "tx")
        learner.onResolved(base.copy(status = CaptureCandidateStatus.DISCARDED, userCorrectedFields = corrected), "tx")
        learner.onResolved(base.copy(status = CaptureCandidateStatus.AUTO_REGISTERED, userCorrectedFields = corrected), "tx")
        learner.onResolved(base.copy(status = CaptureCandidateStatus.CONFIRMED), "tx")
        learner.onResolved(base.copy(ownerId = "other", status = CaptureCandidateStatus.CONFIRMED, userCorrectedFields = corrected), "tx")
        confirmVerified(alias, preference)
    }
    @Test fun `confirmed category correction learns for the owner only while premium`() = runTest {
        val ownerId = "61000000-0000-4000-8000-000000000001"
        val owner = com.kipu.app.core.finance.domain.model.UserId(ownerId)
        val repository = mockk<CategoriesRepository>()
        val alias = mockk<SaveMerchantAliasRule>()
        val preference = mockk<SetMerchantCategoryPreference>()
        io.mockk.every { repository.observePremiumVerified(owner) } returns kotlinx.coroutines.flow.flowOf(true)
        every { repository.observeMerchantCategoryPreferences(owner) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { preference(owner, any(), any()) } returns Result.success(mockk())
        val learner = LearnFromCaptureCorrection(repository, alias, preference, FakeCaptureAccessGate(ownerId))
        val candidate = captureCandidate(ownerId).copy(status = CaptureCandidateStatus.CONFIRMED,
            userCorrectedFields = listOf(CaptureCorrectedField.CATEGORY.name))
        learner.onResolved(candidate, "tx")
        coVerify(exactly = 1) { preference(owner, com.kipu.app.feature.categories.domain.model.MerchantId("merchant-1"), com.kipu.app.feature.categories.domain.model.CategoryId("category-1")) }
        confirmVerified(alias)
    }
}
