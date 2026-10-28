package com.kipu.app.feature.categories.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.kipu.app.BuildConfig
import com.kipu.app.core.logging.SecureLog
import com.kipu.app.core.storage.data.MerchantLogoUrlResolver
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Warms Coil's disk cache with the merchant logos hosted in our own Storage bucket so the
 * catalog renders offline. Already cached images are served from disk without a network call.
 */
@HiltWorker
class MerchantLogoPrefetchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val merchantDao: MerchantCatalogDao,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val urls = MerchantLogoPrefetchPlan.urlsFor(merchantDao.getMerchantsWithRemoteLogos(), BuildConfig.SUPABASE_URL)
        val imageLoader = SingletonImageLoader.get(applicationContext)
        var failed = 0
        for (url in urls) {
            val request = ImageRequest.Builder(applicationContext)
                .data(url)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build()
            // Never retry in a loop: the next catalog sync enqueues this worker again.
            runCatching { imageLoader.execute(request) }
                .onFailure { failed++ }
                .onSuccess { if (it is coil3.request.ErrorResult) failed++ }
        }
        if (failed > 0) SecureLog.w("MerchantLogoPrefetch", "Could not prefetch $failed of ${urls.size} merchant logos")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "prefetch_merchant_logos"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<MerchantLogoPrefetchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Selection logic kept pure so it can be unit tested. */
object MerchantLogoPrefetchPlan {
    fun urlsFor(merchants: List<MerchantCatalogEntity>, supabaseUrl: String): List<String> =
        merchants
            .filter { it.isActive }
            .flatMap { listOf(it.logoPath, it.logoDarkPath) }
            .mapNotNull { MerchantLogoUrlResolver.resolve(supabaseUrl, it) }
            .distinct()

    fun hasRemoteLogos(merchants: List<MerchantCatalogEntity>): Boolean =
        merchants.any { it.isActive && (it.logoPath != null || it.logoDarkPath != null) }
}
