package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.KipuTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class CardInstallmentsPrivacyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun interestIsRemovedFromTextWhenBalancesAreMasked() {
        var masked by mutableStateOf(false)
        val principal = Money(10_000, Currency.PEN)
        val interest = Money(1_234, Currency.PEN)
        val total = Money(11_234, Currency.PEN)
        val zero = Money(0, Currency.PEN)
        val item = CardInstallment(
            id = "qa95-interest", cardId = CardId("a2000000-0000-4000-8000-000000000095"), cardName = "QA95_CARD",
            purchaseId = "qa95-purchase", merchant = "QA95_SHOP", description = null,
            installmentNumber = 1, installmentsCount = 3, dueDate = LocalDate.now().plusDays(30),
            billingClosingDate = null, principal = principal, interest = interest, total = total,
            paid = zero, refunded = zero, outstanding = total,
            terms = PurchaseTerms(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, 3, 5_000, PurchaseRateSource.USER_ENTERED),
            status = CardInstallmentStatus.PENDING,
        )
        compose.setContent {
            KipuTheme {
                CompositionLocalProvider(LocalBalanceMasked provides masked) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        CardInstallmentsSection("qa95-card", CardInstallmentsUiState(
                            summary = CardInstallmentsSummary.from(listOf(item), Currency.PEN), isLoading = false))
                    }
                }
            }
        }
        compose.onNodeWithText("Interés: S/ 12.34").performScrollTo().assertIsDisplayed()
        captureFixture("interest-visible")
        compose.runOnIdle { masked = true }
        compose.onAllNodesWithText("12.34", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("12.34", substring = true).assertCountEquals(0)
        captureFixture("interest-masked")
    }

    private fun captureFixture(name: String) {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "qa95-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

}
