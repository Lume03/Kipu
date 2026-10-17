package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.runtime.Composable
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.KipuThemePreviews
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate
import java.time.Instant
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.accounts.domain.model.PurchaseCandidate

@KipuThemePreviews
@Composable
private fun RateCatalogThemePreview() = KipuTheme {
    RateCatalogScreen(
        creditCard = CreditCard(CardId("e3900000-0000-4000-8000-000000000003"), UserId("e3900000-0000-4000-8000-000000000002"), issuer = "BCP",
            network = CardNetwork.VISA, lastFourDigits = "1234", currency = Currency.PEN,
            creditLimitMinorUnits = 500_000, billingDay = 15, dueDay = 5, personalTeaBps = 4200),
        products = listOf(CreditProductReference(
            id = "preview-reference", institutionCode = "BCP", institutionName = "Entidad de ejemplo",
            productName = "Tarjeta de ejemplo", cardNetwork = "VISA", penTeaMinBps = 3000, penTeaMaxBps = 6000,
            usdTeaMinBps = null, usdTeaMaxBps = null, publishedTeaSummary = "30%–60%",
            publishedTceaSummary = "Referencia de ejemplo", membershipFeePenMinor = 10000,
            membershipFeeUsdMinor = null, membershipCondition = "Condiciones de ejemplo",
            sourceUrl = "https://example.com/rates", verificationStatus = "VERIFIED",
            catalogAsOf = LocalDate.of(2026, 10, 1), effectiveTo = null,
        )), catalogError = null, catalogLoading = false, cardLoading = false, events = emptyFlow(),
        onLoadCatalog = {}, onUpdatePersonalTea = { _, _ -> }, onNavigateBack = {},
    )
}

@KipuThemePreviews
@Composable
private fun InstallmentSimulatorThemePreview() = KipuTheme {
    val card = CreditCard(CardId("e3900000-0000-4000-8000-000000000003"),
        UserId("e3900000-0000-4000-8000-000000000002"), issuer = "BCP", network = CardNetwork.VISA,
        lastFourDigits = "1234", currency = Currency.PEN, creditLimitMinorUnits = 500_000,
        billingDay = 15, dueDay = 5)
    InstallmentSimulatorScreen(PurchaseCandidate("preview-purchase", card.id, Money(10000, Currency.PEN),
        "Comercio de ejemplo", Instant.parse("2026-09-16T12:00:00Z"), suggestedInstallments = 3),
        card, onConfirmPurchase = {}, onRejectPurchase = {})
}
