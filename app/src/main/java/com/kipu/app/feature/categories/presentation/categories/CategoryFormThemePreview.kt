package com.kipu.app.feature.categories.presentation.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun CategoryFormThemePreview() {
    KipuTheme {
        CategoryFormContent(
            isEditing = true,
            categoryType = CategoryType.GENERAL,
            name = "Alimentación",
            icon = "restaurant",
            color = "#10B981",
            parentId = null,
            availableRoots = emptyList(),
            onNameChange = {},
            onIconChange = {},
            onColorChange = {},
            onParentIdChange = {},
            onDismiss = {},
            onBack = {},
            onConfirm = {},
            modifier = Modifier,
        )
    }
}
