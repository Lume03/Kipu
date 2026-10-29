package com.kipu.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kipu.app.feature.budgets.data.sync.BudgetMaintenanceScheduler
import com.kipu.app.feature.capture.data.work.EvidencePurgeScheduler
import com.kipu.app.feature.recurrences.data.sync.ExpectedPaymentScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KipuApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var budgetMaintenanceScheduler: BudgetMaintenanceScheduler

    @Inject
    lateinit var evidencePurgeScheduler: EvidencePurgeScheduler

    @Inject lateinit var expectedPaymentScheduler: ExpectedPaymentScheduler

    override fun onCreate() {
        super.onCreate()
        budgetMaintenanceScheduler.ensurePeriodic()
        evidencePurgeScheduler.ensurePeriodic()
        expectedPaymentScheduler.ensurePeriodic()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
