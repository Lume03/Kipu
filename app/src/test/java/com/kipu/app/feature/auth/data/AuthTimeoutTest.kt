package com.kipu.app.feature.auth.data

import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthTimeoutTest {
    @Test fun `request and cancellation handler run away from caller thread`() = runBlocking {
        val main = Executors.newSingleThreadExecutor { Thread(it, "simulated-main") }.asCoroutineDispatcher()
        try {
            withContext(main) {
                val started = CompletableDeferred<Thread>()
                val cancelled = CompletableDeferred<Thread>()
                val request = launch {
                    withIsolatedNetworkCall {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            continuation.invokeOnCancellation { cancelled.complete(Thread.currentThread()) }
                            started.complete(Thread.currentThread())
                        }
                    }
                }
                assertNotEquals(Thread.currentThread(), started.await())
                request.cancelAndJoin()
                assertTrue(request.isCancelled)
                assertNotEquals(Thread.currentThread(), cancelled.await())
            }
        } finally {
            main.close()
        }
    }

    @Test fun `own timeout becomes socket timeout and caller timeout propagates`() = runTest {
        try {
            withAuthTimeout { awaitCancellation() }
            fail("Expected network timeout")
        } catch (_: SocketTimeoutException) {
            assertEquals(0L, testScheduler.currentTime)
        }
        try {
            withTimeout(50) { withAuthTimeout { awaitCancellation() } }
            fail("Expected caller timeout")
        } catch (_: TimeoutCancellationException) {
            assertEquals(50L, testScheduler.currentTime)
        }
    }
}


