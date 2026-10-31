package com.kipu.app.feature.accounts.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.presentation.components.*
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CreditPurchaseTermsSectionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun directAndInstallmentControlsSupportCustomCountAndExplicitFinancing() {
        var draft by mutableStateOf(CreditPurchaseDraft())
        compose.setContent { KipuTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CreditPurchaseTermsSection(draft, validateCreditPurchaseDraft(draft), null, { draft = it })
        } } }
        compose.onNodeWithTag("credit_purchase_type_DIRECT").assertIsSelected()
        compose.onNodeWithTag("credit_purchase_installments_count").assertDoesNotExist()
        compose.onNodeWithTag("credit_purchase_type_INSTALLMENTS").performClick()
        compose.onNodeWithText("24 cuotas").performScrollTo().performClick()
        compose.onNodeWithTag("credit_purchase_installments_count").assertTextContains("24").performTextReplacement("36")
        compose.onNodeWithTag("credit_purchase_financing_WITH_INTEREST").performScrollTo().performClick()
        compose.onNodeWithTag("credit_purchase_tea").performScrollTo().performTextReplacement("45,00")
        compose.runOnIdle {
            val terms = requireNotNull(validateCreditPurchaseDraft(draft).terms)
            assertEquals(36, terms.installmentsCount); assertEquals(4500, terms.teaBps)
            assertEquals(Financing.WITH_INTEREST, terms.financing); assertEquals(PurchaseRateSource.USER_ENTERED, terms.rateSource)
        }
    }
    @Test fun invalidCountAndRateDisplaySpanishValidationAndRemainEditable() {
        var draft by mutableStateOf(CreditPurchaseDraft(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, "37", "0"))
        compose.setContent { KipuTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CreditPurchaseTermsSection(draft, validateCreditPurchaseDraft(draft), null, { draft = it })
        } } }
        compose.onAllNodesWithText("Ingresa una cantidad de cuotas entre 2 y 36").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("credit_purchase_installments_count").performTextReplacement("6")
        compose.onAllNodesWithText("Ingresa una TEA mayor a 0% y de hasta 500%, con un máximo de dos decimales").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("credit_purchase_tea").performTextReplacement("45")
        compose.runOnIdle { assertNotNull(validateCreditPurchaseDraft(draft).terms) }
    }
}
