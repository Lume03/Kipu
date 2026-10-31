package com.kipu.app.feature.settings.data

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.kipu.app.feature.capture.data.listener.KipuNotificationListenerService
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPermissionSourceGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PermissionConsentDao,
) : PermissionSourceGateway {

    override fun checkDeviceAuthorization(source: PermissionSource): DeviceAuthorization {
        return when (source) {
            PermissionSource.YAPE_RECEIPT_OCR -> DeviceAuthorization.NOT_APPLICABLE
            PermissionSource.OWN_NOTIFICATIONS -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null && notificationManager.areNotificationsEnabled()) {
                    DeviceAuthorization.GRANTED
                } else {
                    DeviceAuthorization.DENIED
                }
            }
            PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> {
                val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
                if (enabledPackages.contains(context.packageName)) {
                    DeviceAuthorization.GRANTED
                } else {
                    DeviceAuthorization.DENIED
                }
            }
        }
    }

    override fun createSettingsIntent(source: PermissionSource): Intent = AndroidPermissionSettings.createIntent(context, source)

    override suspend fun refreshAndSaveDeviceAuthorizations(): Map<PermissionSource, DeviceAuthorization> {
        val results = mutableMapOf<PermissionSource, DeviceAuthorization>()
        for (source in PermissionSource.entries) {
            val auth = checkDeviceAuthorization(source)
            results[source] = auth
            dao.putInstallationPermission(
                InstallationPermissionStateEntity(
                    source = source.name,
                    deviceAuthorization = auth.name,
                    lastCheckedAt = Instant.now(),
                )
            )
        }
        return results
    }
}

/** One Android entry point shared by the gateway and navigation, including OEM fallbacks. */
object AndroidPermissionSettings {
    fun createIntent(context: Context, source: PermissionSource): Intent = when (source) {
        PermissionSource.YAPE_RECEIPT_OCR -> error("El OCR local no requiere ajustes de Android")
        PermissionSource.OWN_NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, KipuNotificationListenerService::class.java).flattenToString(),
            )
        } else listenerListIntent()
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(context: Context, source: PermissionSource): Boolean {
        if (source == PermissionSource.YAPE_RECEIPT_OCR) return false
        if (launch(context, createIntent(context, source))) return true
        return source == PermissionSource.OTHER_APP_NOTIFICATION_CONTENT &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && launch(context, listenerListIntent())
    }

    private fun listenerListIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
