package com.kipu.app.feature.settings.data

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.kipu.app.feature.settings.domain.model.PermissionSource
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidPermissionSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test @Config(sdk = [24]) fun `android 24 opens application details`() {
        val intent = AndroidPermissionSettings.createIntent(context, PermissionSource.OWN_NOTIFICATIONS)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }

    @Test @Config(sdk = [26]) fun `android 26 opens app notification settings`() {
        val intent = AndroidPermissionSettings.createIntent(context, PermissionSource.OWN_NOTIFICATIONS)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}
