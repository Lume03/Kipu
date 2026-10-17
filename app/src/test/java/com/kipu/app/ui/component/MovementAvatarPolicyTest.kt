package com.kipu.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class MovementAvatarPolicyTest {
    @Test
    fun categoryIconWinsOverMerchantInitialsWhenMerchantLogoIsUnavailable() {
        val presentation = movementAvatarPresentation(
            merchantAvatarAllowed = true,
            merchantName = "Restaurante Central",
            hasUsableMerchantLogo = false,
            categoryIcon = "restaurant",
        )

        assertEquals(MovementAvatarPresentation.CATEGORY_ICON, presentation)
    }
}
