package com.kipu.app.feature.notifications.data

import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.budgets.data.local.BudgetDao
import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationDestination
import com.kipu.app.feature.notifications.domain.NotificationDestinationResolver
import com.kipu.app.feature.notifications.domain.NotificationReferenceType
import javax.inject.Inject
import com.kipu.app.feature.capture.domain.CaptureCandidateStore
import com.kipu.app.feature.capture.domain.CaptureCandidateStatus

class RegisteredNotificationDestinationResolver @Inject constructor(
    private val cardDao: CardDao,
    private val budgetDao: BudgetDao,
    private val candidates: CaptureCandidateStore,
) : NotificationDestinationResolver {
    override suspend fun resolve(userId: String, notification: AppNotification): NotificationDestination {
        if (notification.userId != userId) return NotificationDestination.Unavailable()
        val referenceId = notification.referenceEntityId?.takeIf(String::isNotBlank)
            ?: return NotificationDestination.Unavailable()
        return when (notification.referenceType) {
            NotificationReferenceType.CARD -> {
                if (cardDao.getById(userId, referenceId) == null) {
                    NotificationDestination.Unavailable()
                } else {
                    NotificationDestination.CardDetail(referenceId)
                }
            }
            NotificationReferenceType.BUDGET -> {
                if (budgetDao.getBudget(userId, referenceId) == null) {
                    NotificationDestination.Unavailable()
                } else {
                    NotificationDestination.BudgetDetail(referenceId)
                }
            }
            NotificationReferenceType.CAPTURE_CANDIDATE -> {
                if (candidates.get(userId, referenceId)?.status == CaptureCandidateStatus.PENDING)
                    NotificationDestination.CaptureCandidate(referenceId)
                else NotificationDestination.Unavailable()
            }
            NotificationReferenceType.DEBT,
            NotificationReferenceType.GOAL,
            null -> NotificationDestination.Unavailable()
        }
    }
}
