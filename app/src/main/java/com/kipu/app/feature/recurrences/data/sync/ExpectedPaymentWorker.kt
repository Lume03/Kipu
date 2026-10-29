package com.kipu.app.feature.recurrences.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.recurrences.domain.GenerateExpectedPayments
import com.kipu.app.feature.recurrences.domain.RecurrenceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ExpectedPaymentScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensurePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("expected-payments", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExpectedPaymentWorker>(6, TimeUnit.HOURS).build())
    }
}

@HiltWorker
class ExpectedPaymentWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val session: SessionCoordinator, private val repository: RecurrenceRepository,
    private val generate: GenerateExpectedPayments,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val owner = session.currentOwner?.verifiedUserId
        if (owner != null) repository.observeAll(owner).first().forEach { generate(it) }
        Result.success()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.retry() }
}
