package com.kipu.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteCallTest {
    @Test fun `returns the block result`() = runTest {
        assertEquals("ok", remoteCall(onFailure = { "failed" }) { "ok" })
    }

    @Test fun `maps ordinary failures`() = runTest {
        assertEquals("failed: boom", remoteCall(onFailure = { "failed: ${it.message}" }) { throw IllegalStateException("boom") })
    }

    @Test fun `rethrows cancellation instead of mapping it`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { remoteCall(onFailure = { "failed" }) { throw CancellationException("cancelled") } }
        }
    }
}
