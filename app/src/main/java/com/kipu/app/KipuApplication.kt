package com.kipu.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kipu.app.feature.budgets.data.sync.BudgetMaintenanceScheduler
import com.kipu.app.feature.capture.data.work.EvidencePurgeScheduler
import com.kipu.app.feature.recurrences.data.sync.ExpectedPaymentScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.kipu.app.feature.plans.data.sync.PlanEntitlementRefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class KipuApplication : Application(), Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Inject lateinit var planEntitlementRefresh: PlanEntitlementRefreshCoordinator
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var budgetMaintenanceScheduler: BudgetMaintenanceScheduler

    @Inject
    lateinit var evidencePurgeScheduler: EvidencePurgeScheduler

    @Inject lateinit var expectedPaymentScheduler: ExpectedPaymentScheduler

    override fun onCreate() {
        super.onCreate()
        planEntitlementRefresh.start(applicationScope)
        budgetMaintenanceScheduler.ensurePeriodic()
        evidencePurgeScheduler.ensurePeriodic()
        expectedPaymentScheduler.ensurePeriodic()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
