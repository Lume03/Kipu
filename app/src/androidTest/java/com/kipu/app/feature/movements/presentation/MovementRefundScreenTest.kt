package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import com.kipu.app.feature.movements.domain.model.TransactionStatus
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovementRefundScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun eligiblePurchaseDetailsExposeTheLinkedRefundAction() {
        var opened = false
        compose.setContent {
            KipuTheme {
                MovementDetailSheet(
                    item = purchaseItem(),
                    revisions = emptyList(),
                    loading = false,
                    error = false,
                    onDismiss = {},
                    onEdit = {},
                    onVoid = {},
                    canRegisterRefund = true,
                    refundRemainingMinor = 6_000L,
                    onRegisterRefund = { opened = true },
                )
            }
        }

        compose.onNodeWithTag("detail_register_refund").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun rejectedRefundShowsARecoverableVoidAction() {
        var voided = false
        val rejected = purchaseItem().copy(
            transaction = Transaction(
                id = "refund-1",
                userId = "owner-1",
                type = MovementType.INCOME,
                amountMinor = 4_000L,
                currency = "PEN",
                sourceAccountId = "cash-1",
                operationKind = "REFUND",
                occurredAt = 1_758_000_000_000L,
                status = TransactionStatus.CONFIRMED,
                syncStatus = MovementSyncStatus.FAILED_PERMANENT,
            ),
        )
        compose.setContent {
            KipuTheme {
                MovementDetailSheet(
                    item = rejected,
                    revisions = emptyList(),
                    loading = false,
                    error = false,
                    onDismiss = {},
                    onEdit = {},
                    onVoid = {},
                    refundRejectionReason = "El servidor rechazó este reembolso. Puedes anularlo.",
                    onVoidRejectedRefund = { voided = true },
                )
            }
        }

        compose.onNodeWithTag("detail_void_rejected_refund").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("La compra original y su fecha permanecerán intactas.").assertIsDisplayed()
        compose.onNodeWithText("Anular reembolso").performClick()
        assertTrue(voided)
    }

    @Test
    fun showsPurchaseAndRefundPeriodsMasksMoneyAndSubmitsMinorUnits() {
        var submittedAmount = 0L
        var submittedDate = 0L
        compose.setContent {
            KipuTheme {
                CompositionLocalProvider(LocalBalanceMasked provides true) {
                    MovementRefundSheet(
                        currency = "PEN",
                        remainingMinor = 6_000L,
                        purchaseOccurredAt = Instant.parse("2026-09-15T12:00:00Z").toEpochMilli(),
                        isSubmitting = false,
                        errorMessage = null,
                        onDismiss = {},
                        onSubmit = { amount, date -> submittedAmount = amount; submittedDate = date },
                    )
                }
            }
        }

        compose.onNodeWithText("Registrar reembolso").assertIsDisplayed()
        compose.onNodeWithText("El dinero volverá a la cuenta o tarjeta de esta compra en la fecha del reembolso.").assertIsDisplayed()
        compose.onNodeWithText("El gasto neto y el presupuesto se ajustarán en el periodo de la compra original.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("refund_remaining_amount").assertContentDescriptionEquals("Monto oculto")
        compose.onNodeWithTag("refund_amount_input").assertContentDescriptionEquals("Importe oculto")
        compose.onNodeWithTag("refund_amount_input").performTextInput("25.00")
        compose.onNodeWithTag("btn_confirm_refund").performClick()

        assertEquals(2_500L, submittedAmount)
        assertTrue(submittedDate > 0L)
    }

    @Test
    fun blocksAmountsAboveRemainderAndShowsRecoverableServerRejection() {
        compose.setContent {
            KipuTheme {
                MovementRefundSheet(
                    currency = "PEN",
                    remainingMinor = 6_000L,
                    purchaseOccurredAt = Instant.parse("2026-09-15T12:00:00Z").toEpochMilli(),
                    isSubmitting = false,
                    errorMessage = "Otro dispositivo reembolsó parte de esta compra. Puedes anular este reembolso.",
                    onDismiss = {},
                    onSubmit = { _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("refund_amount_input").performTextInput("70.00")
        compose.onNodeWithTag("btn_confirm_refund").assertIsNotEnabled()
        compose.onNodeWithText("El importe supera el remanente disponible.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Otro dispositivo reembolsó parte de esta compra. Puedes anular este reembolso.")
            .performScrollTo().assertIsDisplayed()
    }

    private fun purchaseItem() = TransactionItem(
        transaction = Transaction(
            id = "purchase-1",
            userId = "owner-1",
            type = MovementType.EXPENSE,
            amountMinor = 10_000L,
            currency = "PEN",
            sourceAccountId = "cash-1",
            categoryId = "food-1",
            occurredAt = Instant.parse("2026-09-15T12:00:00Z").toEpochMilli(),
            status = TransactionStatus.CONFIRMED,
        ),
        categoryName = "Alimentos",
    )
}
