package com.kipu.app.feature.widgets

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.usecase.ObserveFinancialDashboard
import com.kipu.app.feature.capture.data.local.CaptureCandidateDao
import com.kipu.app.feature.debts.domain.usecase.ObserveUpcomingDues
import com.kipu.app.feature.goals.domain.GoalRepository
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.feature.widgets.data.WidgetSessionStore
import com.kipu.app.feature.widgets.ui.BudgetsWidget
import com.kipu.app.feature.widgets.ui.CapturesWidget
import com.kipu.app.feature.widgets.ui.ClimateWidget
import com.kipu.app.feature.widgets.ui.DebtsWidget
import com.kipu.app.feature.widgets.ui.FreeToSpendWidget
import com.kipu.app.feature.widgets.ui.GoalsWidget
import com.kipu.app.feature.widgets.ui.NetWorthWidget
import com.kipu.app.feature.widgets.ui.PaceWidget
import com.kipu.app.feature.widgets.ui.PaymentsWidget
import com.kipu.app.feature.widgets.ui.QuickActionsWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the home screen widgets current. While the app process lives it watches the same sources the widgets read
 * (balances, payments, goals, captures, the hide-amounts preference) and redraws them shortly after any change; a
 * periodic job covers the time the app is closed and the day rolling over.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionCoordinator,
    private val store: WidgetSessionStore,
    private val dashboard: ObserveFinancialDashboard,
    private val dues: ObserveUpcomingDues,
    private val goals: GoalRepository,
    private val captureDao: CaptureCandidateDao,
    private val profileDao: ProfilePreferencesDao,
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch { trackOwner() }
        scope.launch {
            session.localAccess
                .map { (it as? LocalAccess.Available)?.userId }
                .distinctUntilChanged()
                .flatMapLatest { owner -> if (owner == null) flowOf(Unit) else changes(owner) }
                .debounce(DEBOUNCE_MILLIS)
                .catch { if (it is CancellationException) throw it }
                .collect { updateAll() }
        }
        // A dark/light switch changes the palette of every widget; redraw while the process is alive.
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) { scope.launch { updateAll() } }
            @Deprecated("Deprecated in Java") override fun onLowMemory() = Unit
            override fun onTrimMemory(level: Int) = Unit
        })
        ensurePeriodic()
    }

    /** Remember the verified owner so widgets can be redrawn when the app is closed; forget it on sign-out. */
    private suspend fun trackOwner() {
        var seenOwner = false
        session.localAccess.collect { access ->
            when (access) {
                is LocalAccess.Available -> { seenOwner = true; store.remember(access.userId) }
                is LocalAccess.Protected -> { seenOwner = true; store.remember(access.userId) }
                LocalAccess.NoOwner -> if (seenOwner) { seenOwner = false; store.forget(); updateAll() }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun changes(owner: String) = combine(
        dashboard(),
        dues(owner),
        goals.observeGoals(owner),
        captureDao.observePending(owner),
        runCatching { UUID.fromString(owner) }.getOrNull()?.let { profileDao.observeProfile(it) } ?: flowOf(null),
    ) { _, _, _, _, _ -> Unit }

    suspend fun updateAll() {
        FreeToSpendWidget().updateAll(context)
        BudgetsWidget().updateAll(context)
        PaymentsWidget().updateAll(context)
        GoalsWidget().updateAll(context)
        QuickActionsWidget().updateAll(context)
        NetWorthWidget().updateAll(context)
        CapturesWidget().updateAll(context)
        PaceWidget().updateAll(context)
        DebtsWidget().updateAll(context)
        ClimateWidget().updateAll(context)
    }

    private fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "kipu_widgets_refresh"
        const val DEBOUNCE_MILLIS = 1_500L
    }
}
