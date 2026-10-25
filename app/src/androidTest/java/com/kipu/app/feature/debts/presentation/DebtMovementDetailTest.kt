package com.kipu.app.feature.debts.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.presentation.MovementDetailSheet
import com.kipu.app.feature.movements.presentation.VoidMovementDialog
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebtMovementDetailTest {
    @get:Rule val compose = createComposeRule()
    private val payment = TransactionItem(Transaction(id = "payment", userId = "owner", type = MovementType.EXPENSE,
        amountMinor = 15_000L, currency = "PEN", sourceAccountId = "account", occurredAt = 1_791_000_000_000L,
        categoryId = "debt-principal", operationKind = "DEBT_PAYMENT"))

    @Test fun debtPaymentIsLabelledCorrectlyAndCanBeVoidedWhileFinancialEditingStaysBlocked() {
        var voids = 0
        compose.setContent { KipuTheme { MovementDetailSheet(payment, emptyList(), false, false, {}, {}, { voids++ }) } }
        compose.onNodeWithText("OPERACIÓN DE DEUDA").assertIsDisplayed()
        compose.onNodeWithTag("detail_void").performScrollTo().performClick()
        compose.onNodeWithTag("detail_edit").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, voids) }
    }

    @Test fun legacyDebtPrincipalUsesTheDebtLabelAndTheSupportedVoidFlow() {
        val legacy = payment.copy(transaction = payment.transaction.copy(operationKind = null, legacyKind = "DEBT_PAYMENT"))
        compose.setContent { KipuTheme { MovementDetailSheet(legacy, emptyList(), false, false, {}, {}, {}) } }
        compose.onNodeWithText("OPERACIÓN DE DEUDA").assertIsDisplayed()
        compose.onNodeWithTag("detail_void").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail_edit").assertIsNotEnabled()
    }

    @Test fun voidingDebtRequiresExplicitConfirmationAndExplainsTheGroupedReversal() {
        var confirmed = 0
        compose.setContent { KipuTheme { VoidMovementDialog(payment, {}, { confirmed++ }) } }
        compose.onNodeWithTag("text_void_consequences").assertTextContains("capital y el interés", substring = true)
        compose.runOnIdle { assertEquals(0, confirmed) }
        compose.onNodeWithTag("btn_confirm_void").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }
}
