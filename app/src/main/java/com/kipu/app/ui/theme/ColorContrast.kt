package com.kipu.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** WCAG contrast for a foreground composited on its opaque effective background. */
fun colorContrastRatio(foreground: Color, background: Color): Float {
    val foregroundLuminance = foreground.compositeOver(background).luminance()
    val backgroundLuminance = background.luminance()
    return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
        (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
}

/** Used on user-selected colors and fixed brand surfaces, without changing stored colors. */
fun readableContentColor(background: Color): Color {
    val ink = KipuOnSurface
    return when {
        colorContrastRatio(Color.White, background) >= 4.5f -> Color.White
        colorContrastRatio(ink, background) >= 4.5f -> ink
        else -> Color.Black
    }
}
