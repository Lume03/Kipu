package com.kipu.app.feature.receipts.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.receipts.domain.*
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Component tests require no storage/camera permissions; run only in an isolated test host. */
class ReceiptExperienceTest {
    @get:Rule val compose = createComposeRule()
    private val candidate = ReceiptCandidate("one", "owner", amountMinor = 2500, occurredAt = 1000,
        recipient = "Carlos M.", reference = "00123456", referenceFingerprint = "ref", imageFingerprint = "img",
        status = ReceiptCandidateStatus.PENDING, createdAt = 1, updatedAt = 1, isIncome = false)

    @Test fun candidateContentClickAndDiscardSurviveFontScaleChanges() {
        var fontScale by mutableFloatStateOf(1f)
        var clicks = 0
        var discards = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                KipuTheme { ReceiptInboxRow(candidate, isNew = true, onClick = { clicks++ }, onDiscard = { discards++ }) }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { fontScale = scale }
            compose.onAllNodesWithText("Carlos M.").assertCountEquals(1)
            compose.onNodeWithText("Nuevo").assertIsDisplayed()
            compose.onNodeWithText("−S/ 25.00").assertIsDisplayed()
            val row = compose.onNodeWithTag("receipt_candidate_one")
            row.performClick()
            val discard = row.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
            assertEquals("Descartar comprobante", discard.label)
            compose.runOnIdle { discard.action() }
        }
        compose.runOnIdle {
            assertEquals(2, clicks)
            assertEquals(2, discards)
        }
    }

    @Test fun guideCanAdvanceByHandAndShowsFinalFooter() {
        compose.mainClock.autoAdvance = false
        compose.setContent { KipuTheme { ReceiptShareGuide({}, reducedMotion = false) } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Siguiente").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Elige Kipu en la lista").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Siguiente").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Volver a comprobantes").assertIsDisplayed()
        compose.onNodeWithText("Atrás").assertIsDisplayed()
        compose.onNodeWithText("¿Tienes una captura?").performScrollTo().assertIsDisplayed()
    }

    @Test fun autoplayCanPauseResumeAndStopsAtFinalStep() {
        compose.mainClock.autoAdvance = false
        compose.setContent { KipuTheme { ReceiptShareGuide({}, reducedMotion = false) } }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("receipt_guide_play_pause").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithText("Toca «Compartir» en Yape").assertExists()
        compose.onNodeWithContentDescription("Reproducir animación").performClick()
        compose.mainClock.advanceTimeBy(16000)
        compose.onNodeWithText("Volver a comprobantes").assertExists()
        compose.mainClock.advanceTimeBy(20000)
        compose.onNodeWithText("Revisa y confirma").assertExists()
        compose.onNodeWithContentDescription("Reproducir animación").assertExists()
    }

    @Test fun reducedMotionShowsFinishedScenesAndFullFooterWithoutAutoplay() {
        compose.setContent { KipuTheme { ReceiptShareGuide({}, reducedMotion = true) } }
        compose.onNodeWithText("Volver a comprobantes").assertIsDisplayed()
        compose.onNodeWithTag("receipt_guide_play_pause").assertIsNotEnabled()
        compose.onNodeWithTag("receipt_guide_step_3").performScrollTo().performClick()
        compose.onNodeWithText("Registrado").assertExists()
        compose.onNodeWithText("Atrás").assertIsDisplayed()
    }

    @Test fun emptyInboxOffersGuideAndUploadWithoutManualOrHistoryButtons() {
        compose.setContent { KipuTheme { ReceiptReviewScreen(emptyList(), {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("Pasa tus Yapes a Kipu en 3 toques").assertExists()
        compose.onNodeWithText("Subir una imagen").assertIsDisplayed()
        assertRemovedActions()
    }

    @Test fun pendingInboxStartsQueueAndShowsExactBalanceEffect() {
        var started = false
        compose.setContent { KipuTheme { ReceiptReviewScreen(listOf(candidate), {}, { _, _ -> }, {}, {}, onStartReview = { started = true }) } }
        compose.onNodeWithText("Por revisar").assertIsDisplayed()
        compose.onNodeWithText("Si confirmas los 1 listos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("tu saldo cambiaría en").assertIsDisplayed()
        compose.onAllNodesWithText("−S/ 25.00").assertCountEquals(2)
        compose.onNodeWithText("Empezar a revisar").performClick()
        compose.runOnIdle { assertEquals(true, started) }
        compose.onNodeWithText("Subir una imagen").assertIsDisplayed()
        assertRemovedActions()
    }

    @Test fun caughtUpInboxShowsReceiptOriginsAndUpload() {
        compose.setContent { KipuTheme { ReceiptReviewScreen(emptyList(), {}, { _, _ -> }, {}, {},
            registered = listOf(RegisteredYapeReceipt(candidate.copy(status = ReceiptCandidateStatus.CONFIRMED), "movement"))) } }
        compose.onNodeWithText("Estás al día").assertIsDisplayed()
        compose.onNodeWithText("REGISTRADOS DESDE YAPE").assertExists()
        compose.onNodeWithText("Subir una imagen").assertIsDisplayed()
        assertRemovedActions()
    }

    @Test fun queuePositionAdvancesWithSameTotal() {
        var queue by mutableStateOf(ReceiptReviewQueue(listOf("one", "two")))
        compose.setContent { KipuTheme { Column { ReceiptQueuePosition(queue); ReceiptQueueSegments(queue) } } }
        compose.onNodeWithText("1 de 2").assertIsDisplayed()
        compose.runOnIdle { queue = queue.next(setOf("two"))!! }
        compose.onNodeWithText("2 de 2").assertIsDisplayed()
    }

    @Test fun allImportNoticesExposeTheirMatchingActionAndAnotherImage() {
        var notice by mutableStateOf(ReceiptImportNotice.NOT_YAPE)
        compose.setContent { KipuTheme { key(notice) {
            ReceiptImportNoticeSheet(notice, {}, {}, {}, {}, {}, candidate, "captura.png", 2048)
        } } }
        val titles = listOf("No parece un comprobante de Yape", "No pudimos leer la imagen", "Ese archivo no se puede leer",
            "Ese comprobante ya está por revisar", "Ese comprobante ya lo registraste", "Lo descartaste antes")
        ReceiptImportNotice.entries.forEachIndexed { index, value ->
            compose.runOnIdle { notice = value }
            compose.onNodeWithText(titles[index]).assertExists()
            compose.onNodeWithText("Elegir otra imagen").assertExists()
        }
    }

    private fun assertRemovedActions() {
        compose.onNodeWithText("Registro manual").assertDoesNotExist()
        compose.onNodeWithText("Registrar manualmente").assertDoesNotExist()
        compose.onNodeWithText("Ver movimientos").assertDoesNotExist()
    }
}
