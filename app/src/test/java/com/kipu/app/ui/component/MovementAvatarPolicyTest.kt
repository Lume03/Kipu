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

    @Test
    fun walletLogoWinsOverCategoryButNotOverMerchantLogo() {
        assertEquals(
            MovementAvatarPresentation.WALLET_LOGO,
            movementAvatarPresentation(true, "Edy Hua*", false, "restaurant", paidWithWallet = true),
        )
        assertEquals(
            MovementAvatarPresentation.MERCHANT_AVATAR,
            movementAvatarPresentation(true, "Wong", true, "restaurant", paidWithWallet = true),
        )
    }
    @Test fun linkedMerchantKeepsPickerInitialsInsteadOfGenericCategory() {
        assertEquals(MovementAvatarPresentation.MERCHANT_INITIALS,
            movementAvatarPresentation(true, "MINISO", false, "shopping_bag", hasLinkedMerchant = true))
        assertEquals(MovementAvatarPresentation.CATEGORY_ICON,
            movementAvatarPresentation(false, "MINISO", false, "shopping_bag", hasLinkedMerchant = true))
    }
}
