package com.kipu.app.feature.plans.domain.model

sealed interface Capability {
    data object FreeCore : Capability
    data object YapeReceiptOcr : Capability
    data object CardStatementImport : Capability
    data object Instruments : Capability
    data object CustomCategories : Capability
    data object Debts : Capability
    data object Goals : Capability
    data object Budgets : Capability
    data object PremiumOnly : Capability
}
