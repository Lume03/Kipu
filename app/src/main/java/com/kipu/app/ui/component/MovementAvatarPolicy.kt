package com.kipu.app.ui.component

internal enum class MovementAvatarPresentation {
    MERCHANT_AVATAR,
    CATEGORY_ICON,
    MERCHANT_INITIALS,
    TYPE_ICON,
}

internal fun movementAvatarPresentation(
    merchantAvatarAllowed: Boolean,
    merchantName: String?,
    hasUsableMerchantLogo: Boolean,
    categoryIcon: String?,
): MovementAvatarPresentation = when {
    merchantAvatarAllowed && hasUsableMerchantLogo -> MovementAvatarPresentation.MERCHANT_AVATAR
    !categoryIcon.isNullOrBlank() -> MovementAvatarPresentation.CATEGORY_ICON
    merchantAvatarAllowed && !merchantName.isNullOrBlank() -> MovementAvatarPresentation.MERCHANT_INITIALS
    else -> MovementAvatarPresentation.TYPE_ICON
}
