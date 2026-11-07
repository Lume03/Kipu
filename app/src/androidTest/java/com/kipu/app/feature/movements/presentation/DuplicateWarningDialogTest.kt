package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DuplicateWarningDialogTest {
    @Test fun incomeShowsPositiveAmountAndIncomeActionAndCancelDoesNotConfirm() = typedWarning(
        com.kipu.app.feature.movements.domain.model.MovementType.INCOME,
        "¿Es otro ingreso?", "+S/ 100.00", "Sí, es otro ingreso · guardar",
    )

    @Test fun expenseShowsNegativeAmountAndPurchaseActionAndCancelDoesNotConfirm() = typedWarning(
        com.kipu.app.feature.movements.domain.model.MovementType.EXPENSE,
        "¿Es una compra distinta?", "-S/ 100.00", "Sí, es otra compra · guardar",
    )

    private fun typedWarning(
        type: com.kipu.app.feature.movements.domain.model.MovementType,
        title: String, amount: String, action: String,
    ) {
        var confirmed = false
        var dismissed = false
        val command = com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand(
            idempotencyKey = "UX-duplicate", userId = "UX-owner", type = type,
            amountMinor = 10_000L, currency = "PEN", sourceAccountId = "UX-cash",
        )
        compose.setContent { KipuTheme {
            DuplicateWarningDialog(onConfirm = { confirmed = true }, onDismiss = { dismissed = true }, proposed = command)
        } }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(amount).assertIsDisplayed()
        compose.onNodeWithText(action).assertIsDisplayed()
        compose.onNodeWithTag("btn_cancel_duplicate").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(confirmed) }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cancelClosesWarningWithoutConfirming() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            KipuTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    DuplicateWarningDialog(
                        onConfirm = { confirmed = true },
                        onDismiss = {
                            dismissed = true
                            showDialog = false
                        },
                    )
                }
            }
        }

        compose.onNodeWithText("¿Es una compra distinta?").assertIsDisplayed()
        compose.onNodeWithTag("btn_cancel_duplicate").performClick()

        compose.onNodeWithTag("dialog_duplicate_warning").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertFalse(confirmed)
        }
    }

    @Test
    fun confirmKeepsTheUserChosenDuplicate() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            KipuTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    DuplicateWarningDialog(
                        onConfirm = {
                            confirmed = true
                            showDialog = false
                        },
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        compose.onNodeWithText(
            "Ya registraste un gasto parecido hace poco. Dos compras reales iguales son posibles; solo queremos evitar descontarlo dos veces."
        ).assertIsDisplayed()
        compose.onNodeWithTag("btn_confirm_duplicate").performClick()

        compose.onNodeWithTag("dialog_duplicate_warning").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(confirmed)
            assertFalse(dismissed)
        }
    }
}
