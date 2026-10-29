package com.kipu.app.feature.auth.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class AuthSessionVerifier @Inject constructor(
    @ApplicationContext context: Context,
    private val client: SupabaseClient,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun isOnline(): Boolean = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    // Auth, rather than decoding JWT locally, verifies the signature/header and returns the user.
    suspend fun retrieveUser(accessToken: String): UserInfo = client.auth.retrieveUser(accessToken)

    fun whenOnline(action: suspend () -> Unit): Job = scope.launch {
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) trySend(Unit)
                }
            }
            connectivity.registerDefaultNetworkCallback(callback)
            if (isOnline()) trySend(Unit)
            awaitClose { connectivity.unregisterNetworkCallback(callback) }
        }.first()
        action()
    }
}
