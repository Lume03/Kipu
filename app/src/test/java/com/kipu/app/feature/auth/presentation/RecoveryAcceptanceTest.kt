package com.kipu.app.feature.auth.presentation

import com.kipu.app.core.security.KeystoreEncryptedSessionStorage
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.auth.data.AuthSessionGate
import com.kipu.app.feature.auth.data.RecoverySessionInstaller
import com.kipu.app.feature.auth.domain.CompletePasswordReset
import com.kipu.app.navigation.AuthDeepLinkHandler
import com.kipu.app.navigation.DeepLinkResult
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real installer/use case/SDK; only the Auth provider HTTP boundary is simulated. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class, io.github.jan.supabase.annotations.SupabaseInternal::class)
class RecoveryAcceptanceTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = "128b288e-5ef1-4bca-a8ad-9eaa32aa9d82"
    private val userJson get() = """{"id":"$owner","aud":"authenticated","email":"owner@example.test"}"""
    private var storedPassword = "OldPassword123"
    private var providerError: String? = null
    private var rejectUpdate = false
    private var updates = 0
    private val engine = MockEngine { request ->
        val path = request.url.encodedPath
        val body = (request.body as? TextContent)?.text.orEmpty()
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            path.endsWith("/user") && request.method == HttpMethod.Get -> {
                if (providerError != null) respond("""{"code":401,"error_code":"$providerError","msg":"Recovery token rejected"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                else respond(userJson, HttpStatusCode.OK, jsonHeaders)
            }
            path.endsWith("/user") && request.method == HttpMethod.Put -> {
                updates++
                if (rejectUpdate) respond("""{"code":401,"error_code":"otp_expired","msg":"Recovery expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                else {
                    storedPassword = Json.parseToJsonElement(body).jsonObject.getValue("password").jsonPrimitive.content
                    respond(userJson, HttpStatusCode.OK, jsonHeaders)
                }
            }
            path.endsWith("/logout") -> respond("{}", HttpStatusCode.OK, jsonHeaders)
            path.endsWith("/token") -> {
                val submitted = Json.parseToJsonElement(body).jsonObject.getValue("password").jsonPrimitive.content
                if (submitted == storedPassword) respond("""{"access_token":"login-token","refresh_token":"refresh","expires_in":3600,"token_type":"bearer","user":$userJson}""", HttpStatusCode.OK, jsonHeaders)
                else respond("""{"code":400,"error_code":"invalid_credentials","msg":"Invalid credentials"}""", HttpStatusCode.BadRequest, jsonHeaders)
            }
            else -> error("Unexpected Auth request: ${request.method} $path")
        }
    }
    private val client = createSupabaseClient("https://provider.example.test", "public-test-key") {
        httpEngine = engine
        coroutineDispatcher = dispatcher
        install(Auth) { minimalConfig(); autoSetupPlatform = false }
    }
    private val installer = RecoverySessionInstaller(client, mockk<KeystoreEncryptedSessionStorage>(relaxed = true),
        mockk<SessionCoordinator>(relaxed = true), AuthSessionGate())

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() = kotlinx.coroutines.runBlocking {
        client.close()
        Dispatchers.resetMain()
    }

    private fun callback(token: String) = "https://kipu.app/auth/recovery#access_token=$token&refresh_token=refresh&expires_in=3600&token_type=bearer&type=recovery"
    private fun viewModel() = RecoveryViewModel(mockk(), CompletePasswordReset(client, installer), installer)
    private suspend fun login(password: String) = runCatching {
        client.auth.signInWith(Email) { email = "owner@example.test"; this.password = password }
    }

    @Test fun validLinkUpdatesPasswordAndNewPasswordSignsIn() = runTest(dispatcher) {
        val handler = AuthDeepLinkHandler()
        val link = handler.handleDeepLink(callback("valid-token")) as DeepLinkResult.ResetPassword
        assertTrue(installer.install(link.callbackUrl).isSuccess)
        assertTrue(installer.isReady())
        val vm = viewModel()
        vm.showInvalidRecoveryLinkIfNeeded()
        assertNull(vm.uiState.value.errorMessage)
        vm.onNewPasswordChanged("NewPassword456")
        vm.submitNewPassword()
        vm.uiState.first { !it.isLoading }

        assertTrue(vm.uiState.value.isPasswordResetSuccess)
        assertEquals("", vm.uiState.value.newPassword)
        assertEquals(1, updates)
        assertFalse(installer.isReady())
        assertTrue(handler.handleDeepLink(link.callbackUrl) is DeepLinkResult.InvalidOrConsumed)
        assertTrue(CompletePasswordReset(client, installer)("AnotherPassword789").isFailure)
        assertEquals(1, updates)
        assertTrue(login("OldPassword123").isFailure)
        assertTrue(login("NewPassword456").isSuccess)
        assertEquals(owner, client.auth.currentUserOrNull()?.id)
    }

    @Test fun expiredRecoveryLinkShowsErrorAndPreservesPassword() = runTest(dispatcher) { rejectedLink("otp_expired") }
    @Test fun usedRecoveryLinkShowsErrorAndPreservesPassword() = runTest(dispatcher) { rejectedLink("token_already_used") }
    @Test fun alteredRecoveryLinkShowsErrorAndPreservesPassword() = runTest(dispatcher) { rejectedLink("bad_jwt") }

    private suspend fun TestScope.rejectedLink(error: String) {
        providerError = error
        val link = AuthDeepLinkHandler().handleDeepLink(callback("rejected-$error")) as DeepLinkResult.ResetPassword
        assertTrue(installer.install(link.callbackUrl).isFailure)
        assertFalse(installer.isReady())
        val vm = viewModel()
        vm.showInvalidRecoveryLinkIfNeeded()
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("enlace de recuperación"))
        vm.onNewPasswordChanged("UnauthorizedPassword456")
        vm.submitNewPassword()
        vm.uiState.first { !it.isLoading }
        assertFalse(vm.uiState.value.isPasswordResetSuccess)
        assertNotNull(vm.uiState.value.errorMessage)
        assertEquals(0, updates)
        assertEquals("OldPassword123", storedPassword)
        assertTrue(login("UnauthorizedPassword456").isFailure)
        assertTrue(login("OldPassword123").isSuccess)
    }

    @Test fun providerRejectsExpiredSessionDuringUpdateWithoutChangingPassword() = runTest(dispatcher) {
        assertTrue(installer.install(callback("initially-valid")).isSuccess)
        rejectUpdate = true
        val vm = viewModel()
        vm.onNewPasswordChanged("NewPassword456")
        vm.submitNewPassword()
        vm.uiState.first { !it.isLoading }
        assertFalse(vm.uiState.value.isPasswordResetSuccess)
        assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("enlace de recuperación"))
        assertEquals("OldPassword123", storedPassword)
        assertEquals(1, updates)
        assertTrue(login("OldPassword123").isSuccess)
    }
}
