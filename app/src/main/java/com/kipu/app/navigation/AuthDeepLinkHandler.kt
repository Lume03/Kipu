package com.kipu.app.navigation

import android.net.Uri
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DeepLinkResult {
    data object ConfirmEmail : DeepLinkResult
    data class ResetPassword(val callbackUrl: String) : DeepLinkResult
    data object InvalidOrConsumed : DeepLinkResult
}

@Singleton
class AuthDeepLinkHandler @Inject constructor() {

    private val consumedTokens = mutableSetOf<String>()
    private val recoveryInFlight = mutableSetOf<String>()

    @Synchronized
    fun finishRecovery(callbackUrl: String, success: Boolean) {
        val key = replayKey(callbackUrl) ?: return
        recoveryInFlight.remove(key)
        if (success) consumedTokens.add(key)
    }

    private fun replayKey(callbackUrl: String): String? {
        val uri = java.net.URI(callbackUrl)
        val params = listOf(uri.rawQuery, uri.rawFragment).filterNotNull().flatMap { it.split("&") }
            .mapNotNull { val parts = it.split("=", limit = 2); if (parts.size == 2) parts[0] to parts[1] else null }.toMap()
        val token = params["code"] ?: params["token_hash"] ?: params["access_token"] ?: return null
        return MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses and validates deep links according to auth-session-contract.
     * Accepted paths: /auth/confirm and /auth/recovery.
     * Replays and unexpected paths are rejected.
     */
    fun handleDeepLink(uri: Uri?): DeepLinkResult {
        if (uri == null) return DeepLinkResult.InvalidOrConsumed
        return handleDeepLink(uri.toString())
    }

    @Synchronized
    fun handleDeepLink(uriString: String?): DeepLinkResult {
        if (uriString.isNullOrBlank()) return DeepLinkResult.InvalidOrConsumed
        return try {
            val javaUri = java.net.URI(uriString)
            if (javaUri.scheme != "https" || javaUri.host != "kipu.app") {
                return DeepLinkResult.InvalidOrConsumed
            }
            val path = javaUri.path?.trimEnd('/') ?: return DeepLinkResult.InvalidOrConsumed
            val queryParams = javaUri.rawQuery.orEmpty().split("&").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
            val fragmentParams = javaUri.rawFragment.orEmpty().split("&").mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
            val replayToken = queryParams["code"] ?: queryParams["token_hash"]
                ?: fragmentParams["access_token"]
            val replayKey = replayToken?.let { token ->
                MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte -> "%02x".format(byte) }
            }

            when (path) {
                "/auth/confirm" -> {
                    if (replayKey.isNullOrEmpty() || !consumedTokens.add(replayKey)) {
                        DeepLinkResult.InvalidOrConsumed
                    } else {
                        DeepLinkResult.ConfirmEmail
                    }
                }
                "/auth/recovery" -> {
                    val hasRecoverySession = queryParams["code"]?.isNotBlank() == true ||
                        (fragmentParams["type"] == "recovery" &&
                            fragmentParams["access_token"]?.isNotBlank() == true &&
                            fragmentParams["refresh_token"]?.isNotBlank() == true)
                    if (!hasRecoverySession || replayKey.isNullOrEmpty() || replayKey in consumedTokens || !recoveryInFlight.add(replayKey)) {
                        DeepLinkResult.InvalidOrConsumed
                    } else {
                        DeepLinkResult.ResetPassword(uriString)
                    }
                }
                else -> DeepLinkResult.InvalidOrConsumed
            }
        } catch (_: Exception) {
            DeepLinkResult.InvalidOrConsumed
        }
    }
}
