package com.kipu.app.feature.plans.data.sync

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.plans.data.entitlement.PlanEntitlementRefresh
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Application-owned session observer; foreground events arrive from MainActivity's existing hook. */
@Singleton
class PlanEntitlementRefreshCoordinator @Inject constructor(
    private val session: SessionCoordinator,
    private val refresh: Lazy<PlanEntitlementRefresh>,
    private val scheduler: PlanEntitlementRefreshScheduler,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var scheduledOwner: String? = null
            session.localAccess.collect { access ->
                val owner = when (access) {
                    is LocalAccess.Available -> access.userId
                    is LocalAccess.Protected -> access.userId
                    LocalAccess.NoOwner -> null
                }
                if (owner != scheduledOwner) {
                    if (scheduledOwner != null) scheduler.cancel()
                    owner?.let(scheduler::ensurePeriodic)
                    scheduledOwner = owner
                }
                // Do not cancel persisted work on the initial NoOwner before cold-start restoration.
                if (access is LocalAccess.Available) scope.launch { refresh.get().refreshIfNeeded(access.userId) }
                if (access is LocalAccess.NoOwner && session.currentOwner?.explicitlySignedOut == true) scheduler.cancel()
            }
        }
    }

    suspend fun onForeground() {
        if (session.localAccess.value is LocalAccess.Available) refresh.get().refreshIfNeeded()
    }
}
