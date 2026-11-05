package com.kipu.app.feature.widgets.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which verified owner the widgets read for. The widgets can be refreshed while the app is closed, when
 * the in-memory session has not been restored yet, so the owner is kept on disk; it is cleared on explicit sign-out.
 * Only an opaque user id is stored, never amounts.
 */
@Singleton
class WidgetSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("kipu_widget_session_v1", Context.MODE_PRIVATE)

    val ownerId: String? get() = preferences.getString(KEY_OWNER, null)

    fun remember(ownerId: String) {
        if (this.ownerId != ownerId) preferences.edit().putString(KEY_OWNER, ownerId).apply()
    }

    fun forget() {
        preferences.edit().remove(KEY_OWNER).apply()
    }

    private companion object {
        const val KEY_OWNER = "owner_id"
    }
}
