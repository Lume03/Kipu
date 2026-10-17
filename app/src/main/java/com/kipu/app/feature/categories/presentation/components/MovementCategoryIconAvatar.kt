package com.kipu.app.feature.categories.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon

@Composable
internal fun MovementCategoryIconAvatar(
    iconKey: String,
    background: Color,
    tint: Color,
    shape: Shape,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = shape,
        color = background,
        modifier = modifier.size(size),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = resolveCategoryIcon(iconKey),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
