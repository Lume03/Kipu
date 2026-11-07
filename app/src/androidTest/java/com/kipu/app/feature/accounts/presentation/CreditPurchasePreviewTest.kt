package com.kipu.app.feature.accounts.presentation

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.domain.usecase.SimulateInstallments
import com.kipu.app.feature.accounts.presentation.components.CreditPurchasePreview
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class CreditPurchasePreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hundredInThreeShowsActualFirstAndFollowingPayments() {
        show(simulation(10_000L))
        compose.onNodeWithText("3 cuotas desde").assertIsDisplayed()
        compose.onAllNodesWithText("S/ 33.33").assertCountEquals(2)
        compose.onNodeWithText("Primera:").assertIsDisplayed()
        compose.onNodeWithText("S/ 33.34").assertIsDisplayed()
        compose.onNodeWithText("Siguientes:").assertIsDisplayed()
        compose.onNodeWithText("3 ×").assertDoesNotExist()
    }

    @Test fun equalPaymentsUseMultiplicationWithoutRoundingLabels() {
        show(simulation(9_900L))
        compose.onNodeWithText("3 ×").assertIsDisplayed()
        compose.onNodeWithText("S/ 33.00").assertIsDisplayed()
        compose.onNodeWithText("3 cuotas desde").assertDoesNotExist()
        compose.onNodeWithText("Primera:").assertDoesNotExist()
        compose.onNodeWithText("Siguientes:").assertDoesNotExist()
    }

    @Test fun lastPaymentAdjustmentIsLabelledWithItsNumber() {
        show(simulation(10_000L, RoundingPolicy.LAST_INSTALLMENT_RESIDUAL))
        compose.onNodeWithText("3 cuotas desde").assertIsDisplayed()
        compose.onNodeWithText("Cuotas 1–2:").assertIsDisplayed()
        compose.onNodeWithText("Cuota 3:").assertIsDisplayed()
        compose.onNodeWithText("S/ 33.34").assertIsDisplayed()
        compose.onNodeWithText("Siguientes:").assertDoesNotExist()
    }

    @Test fun twoDistinctPaymentsLabelSecondByNumber() {
        show(simulation(10_001L, RoundingPolicy.LAST_INSTALLMENT_RESIDUAL, count = 2))
        compose.onNodeWithText("2 cuotas desde").assertIsDisplayed()
        compose.onNodeWithText("Primera:").assertIsDisplayed()
        compose.onNodeWithText("Cuota 2:").assertIsDisplayed()
        compose.onNodeWithText("S/ 50.01").assertIsDisplayed()
        compose.onNodeWithText("Siguientes:").assertDoesNotExist()
    }

    @Test fun hundredInThreeKeepsAmountOnOneLineInNarrowDialogWithLargeText() {
        show(simulation(10_000L), narrowLargeText = true)
        assertSingleLineAmount("S/ 33.33")
        compose.onNodeWithText("3 cuotas desde").assertIsDisplayed()
    }

    @Test fun thirtySixPaymentsKeepLargeAmountOnOneLineWithLargeText() {
        show(simulation(10_000_000L, count = 36), narrowLargeText = true)
        assertSingleLineAmount("S/ 2,777.77")
        compose.onNodeWithText("36 cuotas desde").assertIsDisplayed()
    }

    private fun assertSingleLineAmount(amount: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(amount).onFirst().performScrollTo()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("The financial figure must remain intact", 1, layout.lineCount)
        assertTrue("The financial figure must fit horizontally: right=${layout.getLineRight(0)}, bounds=${layout.size.width}",
            layout.getLineRight(0) <= layout.size.width + 1f)
        assertFalse("The financial figure must not be truncated", layout.isLineEllipsized(0))
    }

    private fun show(simulation: InstallmentSimulation, narrowLargeText: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,
                if (narrowLargeText) 1.3f else density.fontScale)) {
                KipuTheme {
                    Column((if (narrowLargeText) Modifier.width(248.dp) else Modifier)
                        .verticalScroll(rememberScrollState())) { CreditPurchasePreview(simulation) }
                }
            }
        }
    }

    private fun simulation(amount: Long, rounding: RoundingPolicy = RoundingPolicy.FIRST_INSTALLMENT_RESIDUAL, count: Int = 3): InstallmentSimulation {
        val cardId = CardId.generate()
        val card = CreditCard(id = cardId, userId = UserId.generate(), alias = "UX-Tarjeta",
            issuer = "BCP", network = CardNetwork.VISA, lastFourDigits = "1234", currency = Currency.PEN,
            creditLimitMinorUnits = 500_000L, billingDay = 15, dueDay = 5)
        val candidate = PurchaseCandidate(id = "UX-preview", cardId = cardId,
            amount = Money(amount, Currency.PEN), merchant = "UX-Comercio",
            occurredAt = Instant.parse("2026-11-06T12:00:00Z"), suggestedInstallments = count)
        return SimulateInstallments()(candidate, card, zeroInterestPromotion = true,
            terms = PurchaseTerms.interestFree(count).copy(roundingPolicy = rounding))
    }
}
