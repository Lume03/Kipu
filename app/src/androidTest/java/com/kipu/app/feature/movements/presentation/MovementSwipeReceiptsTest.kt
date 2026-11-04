package com.kipu.app.feature.movements.presentation

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.movements.data.MovementDisplayPreferences
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.feature.receipts.domain.*
import com.kipu.app.feature.receipts.presentation.ReceiptReviewScreen
import com.kipu.app.navigation.ReceiptMovementEditorHost
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class MovementSwipeReceiptsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun row(id: String = "miniso", status: TransactionStatus = TransactionStatus.ACTIVE) = TransactionItem(
        Transaction(id = id, userId = "fixture", type = MovementType.EXPENSE, amountMinor = 1500,
            currency = "PEN", sourceAccountId = "cash", categoryId = "food", occurredAt = 1, status = status), merchantName = "MINISO")
    @Before fun permissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        // Window/Dialog enter animation runs outside Compose's test clock.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "movement-swipe").apply { mkdirs() }
        File(dir, name).outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun swipeVoidedCancelAndConfirmKeepRowUntilSuccess() {
        var removed = false
        compose.setContent {
            KipuTheme(darkTheme = false) {
                var selected by remember { mutableStateOf(false) }
                var shown by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                    if (shown) DeletableTransactionRow(row(status = TransactionStatus.VOIDED), { selected = true }, {})
                    if (selected) DeleteMovementDialog(row(status = TransactionStatus.VOIDED), false, null,
                        { selected = false }, { removed = true; selected = false; shown = false })
                }
            }
        }
        compose.onNodeWithTag("tx_row_miniso").performTouchInput { swipeLeft() }
        compose.onNodeWithText("¿Eliminar movimiento?").assertIsDisplayed()
        capture("voided-confirm-light.png")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("tx_row_miniso").assertIsDisplayed()
        assertFalse(removed)
        compose.onNodeWithTag("tx_row_miniso").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("movement_delete_confirm").performClick()
        compose.onNodeWithTag("tx_row_miniso").assertDoesNotExist()
        assertTrue(removed)
    }
    @Test fun partialSwipeRevealsDeleteActionAndReturnsAfterCancel() {
        var requests = 0
        compose.setContent { KipuTheme(darkTheme = false) {
            Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                DeletableTransactionRow(row(), { requests++ }, {})
            }
        } }
        compose.onNodeWithTag("tx_row_miniso").performTouchInput {
            down(centerRight)
            moveTo(centerRight - androidx.compose.ui.geometry.Offset(width * 0.25f, 0f), delayMillis = 120)
        }
        capture("swipe-action-light.png")
        compose.onNodeWithTag("tx_row_miniso").performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(0, requests)
        compose.onNodeWithTag("tx_row_miniso").assertIsDisplayed()
    }
    @Test fun verticalScrollAndSpecializedSwipeDoNotDelete() {
        var requests = 0
        compose.setContent { KipuTheme(darkTheme = false) {
            LazyColumn(Modifier.fillMaxSize().systemBarsPadding()) {
                items((0..25).toList(), key = { it }) { index ->
                    val item = row(index.toString()).let { if (index == 0) it.copy(transaction = it.transaction.copy(operationKind = "DEBT_PAYMENT")) else it }
                    DeletableTransactionRow(item, { requests++ }, {}, Modifier.padding(8.dp))
                }
            }
        } }
        compose.onNodeWithTag("tx_row_0").performTouchInput { swipeLeft() }
        assertEquals(0, requests)
        compose.onNodeWithTag("tx_row_1").performTouchInput { swipeUp() }
        assertEquals(0, requests)
        compose.onNodeWithTag("tx_row_0").assertDoesNotExist()
    }
    @Test fun activeErrorOffersRetryAndCancellationInDarkTheme() {
        var confirms = 0
        compose.setContent { KipuTheme(darkTheme = true) {
            DeleteMovementDialog(row(), false, "No se pudo guardar. Reintenta.", {}, { confirms++ })
        } }
        compose.onNodeWithText("Se anulará este movimiento y se revertirá su efecto en el saldo. Luego se ocultará del historial en este dispositivo.").assertIsDisplayed()
        compose.onNodeWithTag("movement_delete_error").assertIsDisplayed()
        capture("active-retry-dark.png")
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, confirms)
    }
    @Test fun busyDialogCannotCancelOrRepeat() {
        var actions = 0
        compose.setContent { KipuTheme(darkTheme = false) { DeleteMovementDialog(row(), true, null, { actions++ }, { actions++ }) } }
        compose.onNodeWithTag("movement_delete_confirm").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").assertIsNotEnabled()
        assertEquals(0, actions)
    }
    @Test fun visibilityPersistsAcrossInstancesAndStaysOwnerScoped() = runBlocking {
        val owner = "fixture-${System.nanoTime()}"
        val first = MovementDisplayPreferences(instrumentation.targetContext)
        first.hide(owner, "miniso")
        val restored = MovementDisplayPreferences(instrumentation.targetContext)
        assertTrue("miniso" in restored.snapshot(owner))
        assertFalse("miniso" in restored.snapshot("another-$owner"))
        first.hide(owner, "miniso")
        assertEquals(setOf("miniso"), restored.snapshot(owner))
    }
    @Test fun realReceiptContentRemainsUnderRealEditorAndReturnsAfterCloseAndSave() {
        var compositions = 0
        val receipt = ReceiptCandidate("receipt-fixture", "fixture", amountMinor = 1500, occurredAt = 1,
            recipient = "MINISO", reference = "TEST", referenceFingerprint = "test", imageFingerprint = "test",
            status = ReceiptCandidateStatus.CONFIRMED, createdAt = 1, updatedAt = 1, isIncome = false)
        compose.setContent { KipuTheme(darkTheme = false) {
            ReceiptMovementEditorHost(editor = { _, close ->
                MovementEditorSheet(MovementEditorUiState(isLoading = false, amountText = "15.00", merchantName = "MINISO"),
                    {}, {}, {}, {}, {}, {}, {}, close, close, close, {}, {}, {})
            }) { open ->
                DisposableEffect(Unit) { compositions++; onDispose { compositions-- } }
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Text("Comprobantes Yape")
                    ReceiptReviewScreen(emptyList(), {}, { _, _ -> }, {}, {},
                        registered = listOf(RegisteredYapeReceipt(receipt, "movement")), onMovement = open)
                }
            }
        } }
        compose.onNodeWithTag("receipt_candidate_receipt-fixture").performClick()
        compose.onNodeWithTag("editor_sheet").assertIsDisplayed()
        assertEquals(1, compositions)
        compose.onNodeWithTag("editor_sheet").performTouchInput {
            down(topCenter + androidx.compose.ui.geometry.Offset(0f, 40f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, height * 0.3f), delayMillis = 250)
        }
        capture("receipts-editor-background.png")
        compose.onNodeWithTag("editor_sheet").performTouchInput { cancel() }
        compose.waitForIdle()
        compose.onNodeWithTag("editor_sheet").assertDoesNotExist()
        compose.onNodeWithTag("receipt_candidate_receipt-fixture").assertIsDisplayed()
        compose.onNodeWithTag("receipt_candidate_receipt-fixture").performClick()
        compose.onNodeWithTag("btn_discard_changes").performClick()
        compose.onNodeWithTag("editor_sheet").assertDoesNotExist()
        compose.onNodeWithTag("receipt_candidate_receipt-fixture").assertIsDisplayed()
        assertEquals(1, compositions)
        capture("receipts-return.png")
        compose.onNodeWithTag("receipt_candidate_receipt-fixture").performClick()
        // Host uses the same completion for save and close; the real editor invokes onSave here.
        compose.onNodeWithTag("editor_btn_save").performClick()
        compose.onNodeWithTag("editor_sheet").assertDoesNotExist()
        compose.onNodeWithTag("receipt_candidate_receipt-fixture").assertIsDisplayed()
    }
}
