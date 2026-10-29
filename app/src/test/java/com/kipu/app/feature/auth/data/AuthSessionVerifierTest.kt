package com.kipu.app.feature.auth.data

import android.content.Context
import android.net.ConnectivityManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthSessionVerifierTest {
    @Test fun `remote verification has finite deadline and preserves caller cancellation`() = runTest {
        val auth = mockk<Auth>()
        val client = mockk<SupabaseClient>()
        val context = mockk<Context>()
        every { context.getSystemService(ConnectivityManager::class.java) } returns mockk()
        every { client.pluginManager } returns mockk {
            every { installedPlugins } returns mapOf(Auth.key to auth)
        }
        coEvery { auth.retrieveUser(any()) } coAnswers { awaitCancellation() }
        val verifier = AuthSessionVerifier(context, client)
        try {
            verifier.retrieveUser("token")
            fail("Expected network timeout")
        } catch (_: java.net.SocketTimeoutException) {
            assertEquals(AUTH_TIMEOUT_MILLIS, testScheduler.currentTime)
        }
        try {
            withTimeout(50) { verifier.retrieveUser("token") }
            fail("Expected caller cancellation")
        } catch (_: TimeoutCancellationException) {
            assertEquals(AUTH_TIMEOUT_MILLIS + 50, testScheduler.currentTime)
        }
    }
}
