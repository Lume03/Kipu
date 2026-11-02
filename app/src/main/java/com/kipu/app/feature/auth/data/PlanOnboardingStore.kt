package com.kipu.app.feature.auth.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Only an explicit successful registration starts plan onboarding. Missing plan rows do not. */
@Singleton
class PlanOnboardingStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("plan_onboarding", Context.MODE_PRIVATE)
    private fun emailKey(email: String) = "registration:" + MessageDigest.getInstance("SHA-256")
        .digest(email.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
    suspend fun registered(email: String) = withContext(Dispatchers.IO) { check(preferences.edit().putBoolean(emailKey(email), true).commit()) }
    suspend fun bindRegisteredOwner(email: String, owner: String) = withContext(Dispatchers.IO) {
        val key = emailKey(email)
        if (preferences.getBoolean(key, false)) {
            check(preferences.edit().putBoolean("pending:$owner", true).remove(key).commit())
        }
    }
    fun isPending(owner: String): Boolean = preferences.getBoolean("pending:$owner", false)
    suspend fun markPending(owner: String) = withContext(Dispatchers.IO) { check(preferences.edit().putBoolean("pending:$owner", true).commit()) }
    suspend fun completed(owner: String) = withContext(Dispatchers.IO) { check(preferences.edit().remove("pending:$owner").commit()) }

    // Separate from plan selection: saving a plan must not finish the whole initial setup.
    fun isSetupPending(owner: String): Boolean = preferences.getBoolean("setup:$owner", false)
    suspend fun markSetupPending(owner: String) = withContext(Dispatchers.IO) { check(preferences.edit().putBoolean("setup:$owner", true).commit()) }
    suspend fun setupCompleted(owner: String) = withContext(Dispatchers.IO) { check(preferences.edit().remove("setup:$owner").commit()) }
}
