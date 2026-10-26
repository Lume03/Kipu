package com.kipu.app.ui.component

internal enum class MovementAvatarPresentation {
    MERCHANT_AVATAR,
    WALLET_LOGO,
    CATEGORY_ICON,
    MERCHANT_INITIALS,
    TYPE_ICON,
}

internal fun movementAvatarPresentation(
    merchantAvatarAllowed: Boolean,
    merchantName: String?,
    hasUsableMerchantLogo: Boolean,
    categoryIcon: String?,
    paidWithWallet: Boolean = false,
): MovementAvatarPresentation = when {
    merchantAvatarAllowed && hasUsableMerchantLogo -> MovementAvatarPresentation.MERCHANT_AVATAR
    // A Yape/Plin payment is recognized by its wallet before its category or the payee's initials.
    merchantAvatarAllowed && paidWithWallet -> MovementAvatarPresentation.WALLET_LOGO
    !categoryIcon.isNullOrBlank() -> MovementAvatarPresentation.CATEGORY_ICON
    merchantAvatarAllowed && !merchantName.isNullOrBlank() -> MovementAvatarPresentation.MERCHANT_INITIALS
    else -> MovementAvatarPresentation.TYPE_ICON
}
