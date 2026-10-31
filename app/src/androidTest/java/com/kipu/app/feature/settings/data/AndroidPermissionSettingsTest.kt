package com.kipu.app.feature.settings.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.kipu.app.feature.capture.data.listener.KipuNotificationListenerService
import com.kipu.app.feature.settings.domain.model.PermissionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class AndroidPermissionSettingsTest {
    private class RecordingContext(private var failuresLeft: Int = 0) : ContextWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
    ) {
        val attempts = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            attempts += intent
            if (failuresLeft > 0) {
                failuresLeft--
                throw ActivityNotFoundException("Settings activity unavailable")
            }
        }
    }

    @Test fun listener_settings_targets_Kipu_component() {
        val context = RecordingContext()
        val intent = AndroidPermissionSettings.createIntent(context, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT)
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, intent.action)
        assertEquals(ComponentName(context, KipuNotificationListenerService::class.java).flattenToString(),
            intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun unavailable_detail_settings_falls_back_to_listener_list() {
        val context = RecordingContext(failuresLeft = 1)
        assertTrue(AndroidPermissionSettings.open(context, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT))
        assertEquals(listOf(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            context.attempts.map { it.action })
    }

    @Test fun unavailable_settings_returns_failure_without_crashing() {
        val context = RecordingContext(failuresLeft = 2)
        assertFalse(AndroidPermissionSettings.open(context, PermissionSource.OTHER_APP_NOTIFICATION_CONTENT))
        assertEquals(2, context.attempts.size)
    }

    @Test fun own_notifications_keep_app_notification_settings() {
        val context = RecordingContext()
        val intent = AndroidPermissionSettings.createIntent(context, PermissionSource.OWN_NOTIFICATIONS)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun local_ocr_has_no_Android_settings_to_launch() {
        val context = RecordingContext()
        assertFalse(AndroidPermissionSettings.open(context, PermissionSource.YAPE_RECEIPT_OCR))
        assertTrue(context.attempts.isEmpty())
    }
}
