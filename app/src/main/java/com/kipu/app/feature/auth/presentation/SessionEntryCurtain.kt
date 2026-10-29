package com.kipu.app.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the "Hola de nuevo" curtain that covers the first load after signing in. The sign-in screen starts it,
 * the dashboard reports when its data is on screen, and [SessionEntryCurtainHost] decides when to lift it.
 */
@Singleton
class SessionEntryCurtain @Inject constructor() {
    data class State(
        val id: Long,
        val email: String,
        val homeReady: Boolean = false,
        val authenticated: Boolean = false,
        val registering: Boolean = false,
        val confirmationRequired: Boolean = false,
        val destinationRoute: String? = null,
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()
    private var counter = 0L

    fun start(email: String, registering: Boolean = false) {
        _state.value = State(++counter, email.trim(), registering = registering)
    }

    fun authenticated() {
        _state.update { it?.copy(authenticated = true) }
    }

    fun expectDestination(route: String) {
        _state.update { it?.copy(destinationRoute = route) }
    }

    fun requireConfirmation() {
        _state.update { it?.copy(confirmationRequired = true) }
    }

    /** Ignored when no curtain is showing, so screens can report readiness unconditionally. */
    fun markHomeReady() {
        _state.update { it?.copy(homeReady = true) }
    }

    fun dismiss() {
        _state.value = null
    }
}

val LocalSessionEntryCurtain = staticCompositionLocalOf<SessionEntryCurtain?> { null }

/** Decides when the curtain may start closing; kept free of Compose so it can be unit tested. */
internal object SessionEntryTiming {
    /** Long enough for the cords to hang and the logo to settle before the exit starts. */
    const val MIN_VISIBLE_MILLIS = 1_800L

    fun isReady(minElapsed: Boolean, authenticated: Boolean, homeReady: Boolean): Boolean =
        minElapsed && authenticated && homeReady

}

/** Draws the curtain above the navigation host while a sign-in is waiting for the dashboard. */
@Composable
fun SessionEntryCurtainHost(curtain: SessionEntryCurtain, currentRoute: String?, dashboardRoute: String) {
    val entry by curtain.state.collectAsStateWithLifecycle()
    val current = entry ?: return
    val reducedMotion = rememberReducedMotionEnabled()
    var minElapsed by remember(current.id) { mutableStateOf(false) }
    LaunchedEffect(current.id, reducedMotion) {
        if (!reducedMotion) delay(SessionEntryTiming.MIN_VISIBLE_MILLIS)
        minElapsed = true
    }
    // Non-dashboard destinations (plan onboarding or explicit reminders) resolve behind the same opaque curtain.
    LaunchedEffect(current.id, current.destinationRoute, currentRoute) {
        if (current.destinationRoute != null && current.destinationRoute != dashboardRoute &&
            currentRoute == current.destinationRoute) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            curtain.markHomeReady()
        }
    }
    val ready = SessionEntryTiming.isReady(
        minElapsed, current.authenticated,
        current.homeReady && currentRoute == (current.destinationRoute ?: dashboardRoute),
    )
    key(current.id) {
        if (current.registering && (current.authenticated || current.confirmationRequired)) {
            AccountCreatedScreen(current.email, onContinue = curtain::dismiss,
                homeReady = current.confirmationRequired || ready,
                confirmationRequired = current.confirmationRequired,
                autoContinue = current.authenticated)
        } else {
            WelcomeBackCurtain(current.email, ready, onFinished = curtain::dismiss,
                registering = current.registering)
        }
    }
}
