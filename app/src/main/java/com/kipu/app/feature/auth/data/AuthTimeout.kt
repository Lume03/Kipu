package com.kipu.app.feature.auth.data

import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val AUTH_TIMEOUT_MILLIS = 15_000L

/** Only our own deadline becomes a network error; caller cancellation still propagates. */
internal suspend fun <T : Any> withAuthTimeout(block: suspend () -> T): T =
    withIsolatedNetworkCall {
        withTimeoutOrNull(AUTH_TIMEOUT_MILLIS) { block() }
            ?: throw SocketTimeoutException("Tiempo de espera agotado. Revisa tu red e intenta nuevamente.")
    }

internal suspend fun <T> withIsolatedNetworkCall(block: suspend () -> T): T {
    // Cancellation handlers must also run off the caller's main thread.
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val request = scope.async { block() }
    return try {
        request.await()
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            request.cancelAndJoin()
            scope.cancel()
        }
    }
}
