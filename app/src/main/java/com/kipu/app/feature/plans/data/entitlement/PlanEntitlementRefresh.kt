package com.kipu.app.feature.plans.data.entitlement

import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes automatic triggers and throttles successful refreshes per signed-in owner. */
@Singleton
class PlanEntitlementRefresh @Inject constructor(
    private val refresh: TestPlanRefresh,
    private val session: SessionCoordinator,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val lastSuccess = mutableMapOf<String, Long>()

    suspend fun refreshIfNeeded(expectedOwner: String? = null): TestPlanRefreshResult = mutex.withLock {
        val owner = (session.localAccess.value as? LocalAccess.Available)?.userId
            ?: return@withLock TestPlanRefreshResult(false, "UNAUTHENTICATED")
        if (expectedOwner != null && expectedOwner != owner) {
            return@withLock TestPlanRefreshResult(false, "SESSION_CHANGED")
        }
        val now = clock.millis()
        val previous = lastSuccess[owner]
        if (previous != null && now >= previous && now - previous < THROTTLE_MILLIS) {
            return@withLock TestPlanRefreshResult(true)
        }
        val result = try {
            refresh.refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TestPlanRefreshResult(false, "REFRESH_UNAVAILABLE")
        }
        if (result.success && (session.localAccess.value as? LocalAccess.Available)?.userId == owner) {
            lastSuccess[owner] = clock.millis()
        }
        result
    }

    companion object {
        const val THROTTLE_MILLIS = 15 * 60 * 1_000L
    }
}
