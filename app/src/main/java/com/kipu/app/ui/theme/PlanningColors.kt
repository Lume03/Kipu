package com.kipu.app.ui.theme

import androidx.compose.runtime.Composable

/** Planning uses the same primary action color as Edit and the rest of the app. */
@Composable
fun rememberPlanningColors(): CalmEmeraldColors {
    val base = rememberCalmEmeraldColors()
    return base
}
