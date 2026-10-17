package com.kipu.app.feature.categories.presentation.components

import androidx.compose.runtime.Composable
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews

@KipuThemePreviews
@Composable
private fun MerchantPickerThemePreview() {
    KipuTheme {
        MerchantPicker(
            state = MerchantPickerState(
                catalogEntries = listOf(
                    MerchantCatalogEntry(
                        id = MerchantId("00000000-0000-0000-0001-000000000012"),
                        name = "Bembos",
                        normalizedName = "bembos",
                        priority = "A",
                        brandColor = "#E1000F",
                    ),
                    MerchantCatalogEntry(
                        id = MerchantId("00000000-0000-0001-0000-000000000002"),
                        name = "Starbucks",
                        normalizedName = "starbucks",
                        priority = "A",
                        brandColor = "#00704A",
                    ),
                ),
            ),
            onQueryChange = {},
            onSelectMerchant = {},
            onSetProvisionalText = {},
            onClearSelection = {},
        )
    }
}
