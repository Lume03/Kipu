package com.kipu.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a screen that paints under the status bar (a full-bleed teal header, a celebration overlay)
 * choose the icon tint. The activity applies it in place of the tint derived from the app background.
 */
@Stable
class StatusBarIconsOverride {
    /** Mirrors `isAppearanceLightStatusBars`: `true` draws dark icons, `false` light ones, `null` leaves the default. */
    var lightStatusBars: Boolean? by mutableStateOf(null)
}

val LocalStatusBarIconsOverride = staticCompositionLocalOf<StatusBarIconsOverride?> { null }

/** Light status bar icons while the caller is composed, for content that is dark behind the bar. */
@Composable
fun LightStatusBarIcons() {
    val override = LocalStatusBarIconsOverride.current ?: return
    DisposableEffect(override) {
        override.lightStatusBars = false
        onDispose { override.lightStatusBars = null }
    }
}
