package com.kipu.app.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
    data class State(val id: Long, val email: String, val homeReady: Boolean = false)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()
    private var counter = 0L

    fun start(email: String) {
        _state.value = State(++counter, email.trim())
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

    /** The curtain never traps the user if the destination does not report back. */
    const val MAX_VISIBLE_MILLIS = 9_000L

    fun isReady(minElapsed: Boolean, timedOut: Boolean, homeReady: Boolean, destinationIsOtherScreen: Boolean): Boolean =
        timedOut || (minElapsed && (homeReady || destinationIsOtherScreen))

    /** Destinations other than the dashboard (plan selection, a reminder) have nothing to wait for. */
    fun isOtherScreen(route: String?, dashboardRoute: String): Boolean =
        route != null && !route.startsWith("auth/") && route != dashboardRoute
}

/** Draws the curtain above the navigation host while a sign-in is waiting for the dashboard. */
@Composable
fun SessionEntryCurtainHost(curtain: SessionEntryCurtain, currentRoute: String?, dashboardRoute: String) {
    val entry by curtain.state.collectAsStateWithLifecycle()
    val current = entry ?: return
    val reducedMotion = rememberReducedMotionEnabled()
    var minElapsed by remember(current.id) { mutableStateOf(false) }
    var timedOut by remember(current.id) { mutableStateOf(false) }
    LaunchedEffect(current.id, reducedMotion) {
        if (!reducedMotion) delay(SessionEntryTiming.MIN_VISIBLE_MILLIS)
        minElapsed = true
    }
    LaunchedEffect(current.id) {
        delay(SessionEntryTiming.MAX_VISIBLE_MILLIS)
        timedOut = true
    }
    val ready = SessionEntryTiming.isReady(
        minElapsed, timedOut, current.homeReady, SessionEntryTiming.isOtherScreen(currentRoute, dashboardRoute),
    )
    WelcomeBackCurtain(current.email, ready, onFinished = curtain::dismiss)
}
