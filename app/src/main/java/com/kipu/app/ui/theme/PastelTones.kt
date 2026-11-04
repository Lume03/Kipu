package com.kipu.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Soft pastel palette shared by Análisis and the planning empty states.
 *
 * One soft color family: [fill] paints chart segments and bars, [tile] is the pale square behind an icon and
 * [ink] is the readable color for icons and numbers drawn over [tile].
 */
@Immutable
data class PastelTone(val fill: Color, val tile: Color, val ink: Color)

private val LightTones = listOf(
    PastelTone(Color(0xFF8FD3C4), Color(0xFFDDF3EE), Color(0xFF0B5F58)), // menta
    PastelTone(Color(0xFF9DBDF2), Color(0xFFE3ECFB), Color(0xFF244E9C)), // cielo
    PastelTone(Color(0xFFF5BE92), Color(0xFFFCEBDD), Color(0xFF8A4B14)), // durazno
    PastelTone(Color(0xFFBFAEEB), Color(0xFFEDE7FA), Color(0xFF5A3FA8)), // lavanda
    PastelTone(Color(0xFFF2A9BA), Color(0xFFFBE5EA), Color(0xFF9A2A4A)), // rosa
    PastelTone(Color(0xFFEAD283), Color(0xFFFAF2D3), Color(0xFF7A5C00)), // mantequilla
    PastelTone(Color(0xFFAAB8C6), Color(0xFFE8EDF2), Color(0xFF3C4B5C)), // pizarra
)

private val DarkTones = listOf(
    PastelTone(Color(0xFF5FB3A3), Color(0xFF1D3A36), Color(0xFFA8E6DA)),
    PastelTone(Color(0xFF6E96DB), Color(0xFF1E2B45), Color(0xFFB3CBF5)),
    PastelTone(Color(0xFFD99A6B), Color(0xFF3E2C1D), Color(0xFFF5CFAE)),
    PastelTone(Color(0xFF9985D6), Color(0xFF2D2647), Color(0xFFCFC3F2)),
    PastelTone(Color(0xFFD97E95), Color(0xFF42232C), Color(0xFFF5BAC8)),
    PastelTone(Color(0xFFCDB45A), Color(0xFF3A3318), Color(0xFFEFDD9C)),
    PastelTone(Color(0xFF8496A8), Color(0xFF262E37), Color(0xFFC5D1DD)),
)

/** The pastel set for the current theme; the order is stable so the same position keeps the same color. */
@Composable
fun rememberPastelTones(): List<PastelTone> {
    val dark = rememberCalmEmeraldColors().isDark
    return remember(dark) { if (dark) DarkTones else LightTones }
}

fun List<PastelTone>.at(index: Int): PastelTone = this[Math.floorMod(index, size)]
