package com.kipu.app.feature.auth.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Only an explicit successful registration starts plan onboarding. Missing plan rows do not. */
@Singleton
class PlanOnboardingStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("plan_onboarding", Context.MODE_PRIVATE)
    private fun emailKey(email: String) = "registration:" + MessageDigest.getInstance("SHA-256")
        .digest(email.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
    fun registered(email: String) { check(preferences.edit().putBoolean(emailKey(email), true).commit()) }
    fun bindRegisteredOwner(email: String, owner: String) {
        val key = emailKey(email)
        if (preferences.getBoolean(key, false)) {
            check(preferences.edit().putBoolean("pending:$owner", true).remove(key).commit())
        }
    }
    fun isPending(owner: String): Boolean = preferences.getBoolean("pending:$owner", false)
    fun markPending(owner: String) { check(preferences.edit().putBoolean("pending:$owner", true).commit()) }
    fun completed(owner: String) { check(preferences.edit().remove("pending:$owner").commit()) }
}
