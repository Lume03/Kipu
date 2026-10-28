package com.kipu.app.feature.accounts.domain.model

import com.kipu.app.core.finance.domain.model.FinancialMovement

data class CardMovementDisplay(
    val movement: FinancialMovement,
    val merchantName: String? = null,
    val merchantLogoKey: String? = null,
    val merchantLogoUrl: String? = null,
    val merchantLogoDarkUrl: String? = null,
    val merchantBrandColor: String? = null,
    val categoryIcon: String? = null,
)
