package com.kipu.app.feature.debts.presentation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * Keeps the standard AnimatedVisibility layout transition unless the user has
 * requested reduced motion at the Android system level.
 */
internal fun reducedMotionEnterTransition(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) EnterTransition.None else fadeIn() + expandVertically()

internal fun reducedMotionExitTransition(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) ExitTransition.None else fadeOut() + shrinkVertically()
