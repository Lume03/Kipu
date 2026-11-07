package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovementDetailCorrectionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activeManualCashExpenseKeepsTitleAndOmitsAbsentMerchant() {
        val item = TransactionItem(Transaction(
            id = "UX-detail", userId = "UX-owner", type = MovementType.EXPENSE,
            amountMinor = 12345L, currency = "PEN", sourceAccountId = "UX-cash",
            categoryId = "UX-category", occurredAt = 1_791_000_000_000L,
            status = TransactionStatus.ACTIVE, syncStatus = MovementSyncStatus.SYNCED,
            captureSource = MovementSource.MANUAL,
        ), sourceAccountAlias = "UX-Efectivo", categoryName = "UX-Comida")
        compose.setContent { KipuTheme {
            MovementDetailSheet(item, emptyList(), false, false, {}, {}, {})
        } }
        val title = compose.onNodeWithText("Detalle del movimiento")
        title.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        // Check the painted line extent; multiParagraph.width can retain the wider parent constraint.
        assertTrue("Title must fit: right=${layout.getLineRight(0)}, bounds=${layout.size.width}",
            layout.getLineRight(0) <= layout.size.width + 1f)
        assertFalse("Title must not be ellipsized", layout.isLineEllipsized(0))

        compose.onNodeWithText("COMERCIO").assertDoesNotExist()
        compose.onNodeWithText("Método de registro").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Manual").assertIsDisplayed()
    }
}
