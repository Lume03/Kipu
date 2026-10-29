package com.kipu.app.feature.capture.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.capture.domain.CaptureCandidateRecord
import com.kipu.app.feature.capture.domain.CaptureCandidateStatus
import com.kipu.app.feature.capture.domain.CaptureConfidenceLevel
import com.kipu.app.feature.capture.domain.CaptureDirection
import com.kipu.app.feature.capture.domain.CaptureInstrumentKind
import com.kipu.app.feature.capture.domain.CaptureProvider
import com.kipu.app.feature.capture.domain.CaptureReason
import com.kipu.app.feature.capture.domain.CreditMode
import com.kipu.app.feature.capture.domain.ExpectedMatchState
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Device-rendered evidence uses synthetic state and never changes the user's capture data. */
@RunWith(Parameterized::class)
class CaptureFlowScreenshotTest(private val scenario: String, private val dark: Boolean) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureScenario() {
        val candidates = candidates()
        val review = scenario.startsWith("review-")
        compose.setContent {
            KipuTheme(darkTheme = dark) {
                if (review) {
                    CaptureReviewContent(
                        state = reviewState(candidates),
                        onBack = {}, onDirection = {}, onAmount = {}, onCurrency = {},
                        onDate = {}, onMerchant = {}, onCategory = {}, onInstrument = {},
                        onCreditMode = {}, onInstallments = {}, onConfirm = {},
                        onConfirmDespiteSimilar = {}, onDismissSimilar = {},
                        onConvertToManual = {}, onDiscard = {}, onOpenPermissions = {},
                        merchantPickerState = MerchantPickerState(), onMerchantQueryChanged = {},
                    )
                } else {
                    CaptureInboxScreen(
                        state = CaptureInboxUiState(
                            isLoading = false,
                            candidates = if (scenario == "inbox-empty") emptyList() else candidates,
                            pendingAssociations = if (scenario == "inbox-empty") emptyList() else listOf(
                                candidates.last().copy(
                                    id = "association", status = CaptureCandidateStatus.CONFIRMED,
                                    expectedMatchState = ExpectedMatchState.PENDING_CHOICE,
                                    resolvedTransactionId = "synthetic-transaction",
                                ),
                            ),
                            automation = when (scenario) {
                                "inbox-empty" -> CaptureAutomationStatus.SETUP_REQUIRED
                                "inbox-paused" -> CaptureAutomationStatus.PREMIUM_REQUIRED
                                else -> CaptureAutomationStatus.ACTIVE
                            },
                        ),
                        onBack = {}, onOpenCandidate = {}, onDiscard = {},
                        onOpenPermissions = {}, onDismissMessage = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        if (scenario == "inbox-empty") {
            compose.onNodeWithText("Por revisar").assertDoesNotExist()
            compose.onAllNodesWithText("Configurar captura").assertCountEquals(1)
        }
        if (scenario == "discard-dialog") {
            compose.onNodeWithTag("capture_discard_plaza-credit").performScrollTo().performClick()
            compose.waitForIdle()
        }
        save(scenario, dialog = scenario == "discard-dialog")
        if (review) {
            // Repeated gestures reach the footer of even the installment form on short devices.
            repeat(4) {
                compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            }
            save("$scenario-bottom")
        } else if (scenario == "inbox-list" || scenario == "inbox-paused") {
            compose.onNodeWithTag("capture_inbox_list").performScrollToNode(hasTestTag("capture_review_payroll"))
            save("$scenario-bottom")
        }
    }

    private fun save(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "capture-flow-evidence").apply { mkdirs() }
        val file = File(directory, "$name-${if (dark) "dark" else "light"}.png")
        val root = if (dialog) compose.onNodeWithTag("capture_discard_dialog") else compose.onRoot()
        file.outputStream().use { output ->
            assertTrue(root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue(file.length() > 0)
    }

    private val instruments = listOf(
        CaptureInstrumentChoice(CaptureInstrumentKind.CREDIT_CARD, "bcp-credit", "Visa Clásica Qore ····1264", "PEN", brandKey = "BCP"),
        CaptureInstrumentChoice(CaptureInstrumentKind.DEBIT_ACCOUNT, "bcp-debit", "Ahorro BCP ····8350", "PEN", accountId = "bcp-debit", brandKey = "BCP"),
        CaptureInstrumentChoice(CaptureInstrumentKind.DEBIT_ACCOUNT, "interbank-debit", "Débito Interbank ····4021", "PEN", accountId = "interbank-debit", brandKey = "INTERBANK"),
        CaptureInstrumentChoice(CaptureInstrumentKind.CREDIT_CARD, "interbank-credit", "Visa Interbank ····4021", "PEN", brandKey = "Interbank"),
    )
    private val categories = listOf(
        CaptureCategoryChoice("supermarkets", "Alimentación > Supermercados", CategoryType.EXPENSE,
            name = "Supermercados", icon = "shopping_cart", parentId = "food", parentName = "Alimentación"),
        CaptureCategoryChoice("food", "Alimentación", CategoryType.EXPENSE, icon = "restaurant"),
    )

    private fun reviewState(candidates: List<CaptureCandidateRecord>): CaptureReviewUiState {
        val candidate = when (scenario) {
            "review-low" -> candidates[0]
            "review-credit" -> candidates[1]
            "review-ambiguous" -> candidates[3]
            else -> candidates[2]
        }
        return CaptureReviewUiState(
            isLoading = false, candidate = candidate,
            automation = if (scenario == "review-paused-error") CaptureAutomationStatus.PREMIUM_REQUIRED else CaptureAutomationStatus.ACTIVE,
            direction = candidate.direction ?: CaptureDirection.EXPENSE,
            amountText = when (scenario) { "review-low" -> ""; "review-credit" -> "22.00"; "review-ambiguous" -> "38.90"; else -> "17.25" },
            currency = candidate.currency, occurredAt = candidate.occurredAt ?: candidate.createdAt,
            categoryId = candidate.categoryId,
            selectedInstrument = when (scenario) { "review-credit" -> instruments[0]; "review-low", "review-ambiguous" -> null; else -> instruments[1] },
            creditMode = if (scenario == "review-credit") CreditMode.INSTALLMENTS else CreditMode.DIRECT,
            installmentsText = if (scenario == "review-credit") "3" else "",
            instruments = instruments, categories = categories,
            generalError = if (scenario == "review-save-error") "No se pudo guardar. Puedes reintentar sin duplicar la operación." else null,
        )
    }

    private fun candidates(): List<CaptureCandidateRecord> {
        val zone = ZoneId.of("America/Lima")
        val today = LocalDate.now(zone).atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
        val yesterday = LocalDate.now(zone).minusDays(1).atTime(18, 20).atZone(zone).toInstant().toEpochMilli()
        val base = CaptureCandidateRecord(
            id = "low", ownerId = "synthetic-owner", sourcePackage = "com.bcp.banking",
            provider = CaptureProvider.BCP, sourceEventFingerprint = "synthetic-low",
            status = CaptureCandidateStatus.PENDING, direction = CaptureDirection.EXPENSE,
            amountMinor = null, currency = null, occurredAt = today, occurredAtInferred = true,
            merchantSourceText = null, merchantId = null, categoryId = null, cardLast4 = null,
            instrumentKind = null, accountId = null, cardId = null, instrumentAmbiguous = false,
            confidence = CaptureConfidenceLevel.LOW,
            confidenceReasons = listOf(CaptureReason.AMOUNT_MISSING, CaptureReason.CURRENCY_MISSING,
                CaptureReason.MERCHANT_UNRESOLVED, CaptureReason.INSTRUMENT_UNRESOLVED, CaptureReason.DATE_INFERRED),
            creditMode = null, installmentCount = null, resolvedTransactionId = null, resolutionKey = null,
            evidenceSnippet = "Aviso de operación BCP. Revisa los datos de la captura.", evidenceExpiresAt = null,
            userCorrectedFields = emptyList(), createdAt = today, updatedAt = today, resolvedAt = null,
        )
        return listOf(
            base,
            base.copy(id = "plaza-credit", amountMinor = 2200, currency = "PEN", merchantSourceText = "PLAZA VEA",
                confidence = CaptureConfidenceLevel.MEDIUM, instrumentKind = CaptureInstrumentKind.CREDIT_CARD,
                cardId = "bcp-credit", cardLast4 = "1264", categoryId = "supermarkets",
                confidenceReasons = listOf(CaptureReason.CREDIT_CARD_REQUIRES_REVIEW, CaptureReason.DATE_INFERRED)),
            base.copy(id = "plaza-duplicate", amountMinor = 1725, currency = "PEN", merchantSourceText = "PLAZA VEA",
                confidence = CaptureConfidenceLevel.MEDIUM, instrumentKind = CaptureInstrumentKind.DEBIT_ACCOUNT,
                accountId = "bcp-debit", categoryId = "supermarkets", occurredAtInferred = false,
                confidenceReasons = listOf(CaptureReason.POSSIBLE_DUPLICATE)),
            base.copy(id = "tambo", provider = CaptureProvider.INTERBANK, sourcePackage = "pe.interbank",
                amountMinor = 3890, currency = "PEN", merchantSourceText = "TAMBO", confidence = CaptureConfidenceLevel.MEDIUM,
                occurredAt = yesterday, occurredAtInferred = false, instrumentAmbiguous = true, cardLast4 = "4021",
                confidenceReasons = listOf(CaptureReason.INSTRUMENT_AMBIGUOUS)),
            base.copy(id = "payroll", provider = CaptureProvider.BBVA, sourcePackage = "com.bbva",
                amountMinor = 245000, currency = "PEN", merchantSourceText = "Abono · Planilla", direction = CaptureDirection.INCOME,
                confidence = CaptureConfidenceLevel.MEDIUM, occurredAt = yesterday, occurredAtInferred = false,
                confidenceReasons = listOf(CaptureReason.CATEGORY_UNRESOLVED)),
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-dark={1}")
        fun scenarios(): List<Array<Any>> = listOf(
            "inbox-list", "inbox-paused", "inbox-empty", "discard-dialog", "review-low",
            "review-credit", "review-ambiguous", "review-paused-error", "review-save-error",
        ).flatMap { name -> listOf(arrayOf<Any>(name, false), arrayOf<Any>(name, true)) }
    }
}
