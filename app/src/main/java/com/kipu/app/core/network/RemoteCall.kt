package com.kipu.app.core.network

import kotlinx.coroutines.CancellationException

/**
 * Runs a suspended remote call mapping failures to [onFailure], but never turns a coroutine
 * cancellation into a network failure: it is rethrown so callers stop instead of retrying.
 */
suspend inline fun <T> remoteCall(onFailure: (Throwable) -> T, block: () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
    }
