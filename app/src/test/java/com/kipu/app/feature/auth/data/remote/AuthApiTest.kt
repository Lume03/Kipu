package com.kipu.app.feature.auth.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
class AuthApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `register returns success when status is 201 Created`() = runTest {
        val mockEngine = MockEngine(io.ktor.client.engine.mock.MockEngineConfig().apply {
            dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
            addHandler { request ->
            assertEquals("functions/v1/auth-access/register", request.url.encodedPath.trimStart('/'))
            respond(
                content = """{"status":"ACCEPTED","confirmationRequired":true}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            }
        })
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = AuthApi(client)

        val response = api.register(RegisterRequestDto("test@example.com", "Secret123", "captcha"))
        assertTrue(response is ApiResponse.Success)
        val data = (response as ApiResponse.Success).data
        assertEquals("ACCEPTED", data.status)
        assertTrue(data.confirmationRequired)
    }

    @Test
    fun `register returns 409 when account already exists`() = runTest {
        val mockEngine = MockEngine(io.ktor.client.engine.mock.MockEngineConfig().apply {
            dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
            addHandler { _ ->
            respond(
                content = """{"status":409,"title":"Account already exists"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            }
        })
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = AuthApi(client)

        val response = api.register(RegisterRequestDto("existing@example.com", "Secret123", "captcha"))
        assertTrue(response is ApiResponse.Error)
        assertEquals(409, (response as ApiResponse.Error).statusCode)
    }

    @Test
    fun `login returns session envelope on 200 OK`() = runTest {
        val mockEngine = MockEngine(io.ktor.client.engine.mock.MockEngineConfig().apply {
            dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
            addHandler { request ->
            assertEquals("functions/v1/auth-access/login", request.url.encodedPath.trimStart('/'))
            respond(
                content = """{"accessToken":"token123","refreshToken":"refresh123","expiresIn":3600,"tokenType":"bearer","userId":"user-uuid-1","sessionMetadata":{"provider":"email"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            }
        })
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = AuthApi(client)

        val response = api.login(LoginRequestDto("test@example.com", "Secret123", "captcha"))
        assertTrue(response is ApiResponse.Success)
        val data = (response as ApiResponse.Success).data
        assertEquals("token123", data.accessToken)
        assertEquals("user-uuid-1", data.userId)
    }

    @Test
    fun `login returns rate limited error with Retry-After header`() = runTest {
        val mockEngine = MockEngine(io.ktor.client.engine.mock.MockEngineConfig().apply {
            dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
            addHandler { _ ->
            respond(
                content = """{"status":429,"title":"Too Many Requests"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Retry-After" to listOf("30"),
                ),
            )
            }
        })
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val api = AuthApi(client)

        val response = api.login(LoginRequestDto("test@example.com", "Secret123", "captcha"))
        assertTrue(response is ApiResponse.Error)
        val error = response as ApiResponse.Error
        assertEquals(429, error.statusCode)
        assertEquals(30, error.retryAfter)
    }
    @Test fun `blocked login and registration return recoverable timeout`() = runTest {
        val client = HttpClient(MockEngine(io.ktor.client.engine.mock.MockEngineConfig().apply {
            dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
            addHandler { kotlinx.coroutines.awaitCancellation() }
        })) {
            install(ContentNegotiation) { json(json) }
        }
        val api = AuthApi(client)
        assertTrue(api.login(LoginRequestDto("test@example.com", "Secret123", "captcha")) is ApiResponse.NetworkFailure)
        assertTrue(api.register(RegisterRequestDto("test@example.com", "Secret123", "captcha")) is ApiResponse.NetworkFailure)
        assertEquals(30_000L, testScheduler.currentTime)
        client.close()
    }

    @Test fun `caller cancellation is never converted to network failure`() = runTest {
        val client = HttpClient(MockEngine(io.ktor.client.engine.mock.MockEngineConfig().apply {
            dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
            addHandler { kotlinx.coroutines.awaitCancellation() }
        })) {
            install(ContentNegotiation) { json(json) }
        }
        val api = AuthApi(client)
        try {
            kotlinx.coroutines.withTimeout(50) {
                api.login(LoginRequestDto("test@example.com", "Secret123", "captcha"))
            }
            org.junit.Assert.fail("Caller cancellation must propagate")
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            assertEquals(50L, testScheduler.currentTime)
        } finally { client.close() }
    }

}
