package com.kipu.app.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Material3 1.4 applies bar properties only when creating its dialog window. */
@Composable
internal fun KipuModalSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val darkTheme = LocalKipuDarkTheme.current
    SideEffect {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}

/** A sheet owns another Android window and must receive the app preference explicitly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun kipuBottomSheetProperties() = ModalBottomSheetProperties(
    isAppearanceLightStatusBars = !LocalKipuDarkTheme.current,
    isAppearanceLightNavigationBars = !LocalKipuDarkTheme.current,
)

/** Keep native window icons and scrims in sync with the effective app preference. */
@Composable
internal fun KipuSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() ?: return
    SideEffect {
        val statusStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
        // API 24/25 cannot draw dark navigation icons; keep their required dark scrim.
        val navigationStyle = SystemBarStyle.auto(Color.TRANSPARENT, KipuDarkBackground.toArgb()) { darkTheme }
        activity.enableEdgeToEdge(statusBarStyle = statusStyle, navigationBarStyle = navigationStyle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
