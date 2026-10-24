package com.kipu.app.feature.budgets.data

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.domain.BudgetAlert
import com.kipu.app.feature.budgets.domain.BudgetAlertNotifier
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BudgetCenterAlertNotifier @Inject constructor(
    private val notificationDao: AppNotificationDao,
    private val sessionCoordinator: SessionCoordinator,
    private val clock: Clock,
) : BudgetAlertNotifier {
    override suspend fun notify(alert: BudgetAlert) {
        if ((sessionCoordinator.localAccess.value as? LocalAccess.Available)?.userId != alert.key.ownerId) return
        notificationDao.insertIfAbsent(
            AppNotificationEntity(
                id = alert.key.dedupeKey,
                userId = alert.key.ownerId,
                title = "Alerta de presupuesto",
                body = "${alert.budgetName}: alcanzaste el ${alert.percentBps / 100}% del límite.",
                notificationType = TYPE_BUDGET_ALERT,
                referenceEntityType = "BUDGET",
                referenceEntityId = alert.key.budgetId,
                isRead = false,
                createdAt = Instant.ofEpochMilli(clock.millis()),
                deletedAt = null,
                eventPayloadJson = json.encodeToString(
                    BudgetAlertPayload(
                        dedupeKey = alert.key.dedupeKey,
                        thresholdBps = alert.key.thresholdBps,
                        periodId = alert.key.periodId,
                    ),
                ),
            ),
        )
    }

    @Serializable
    private data class BudgetAlertPayload(
        val dedupeKey: String,
        val thresholdBps: Int,
        val periodId: String,
    )

    companion object {
        const val TYPE_BUDGET_ALERT = "BUDGET_ALERT"
        private val json = Json { encodeDefaults = true }
    }
}
