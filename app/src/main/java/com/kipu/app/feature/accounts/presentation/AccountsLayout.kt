package com.kipu.app.feature.accounts.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

/** Use compact layouts at fontScale ≥ 1.3× or window widths below 360dp across Dinero. */
@Composable
internal fun isCompactAccountsLayout(): Boolean =
    LocalDensity.current.fontScale >= 1.3f || LocalConfiguration.current.screenWidthDp < 360
