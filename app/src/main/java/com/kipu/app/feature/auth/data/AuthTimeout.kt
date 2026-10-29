package com.kipu.app.feature.auth.data

import java.net.SocketTimeoutException
import kotlinx.coroutines.withTimeoutOrNull

internal const val AUTH_TIMEOUT_MILLIS = 15_000L

/** Only our own deadline becomes a network error; caller cancellation still propagates. */
internal suspend fun <T : Any> withAuthTimeout(block: suspend () -> T): T =
    withTimeoutOrNull(AUTH_TIMEOUT_MILLIS) { block() }
        ?: throw SocketTimeoutException("Tiempo de espera agotado. Revisa tu red e intenta nuevamente.")
