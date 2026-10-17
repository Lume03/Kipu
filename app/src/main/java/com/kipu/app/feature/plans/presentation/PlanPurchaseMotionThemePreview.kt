package com.kipu.app.feature.plans.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun PurchasePriceShimmerThemePreview() {
    KipuTheme {
        Column(Modifier.padding(16.dp)) {
            PurchasePriceShimmer(reduceMotion = false)
        }
    }
}
