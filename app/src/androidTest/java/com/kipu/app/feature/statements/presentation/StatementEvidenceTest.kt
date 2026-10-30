package com.kipu.app.feature.statements.presentation

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.statements.domain.*
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Draws every statement-import state with synthetic data in the isolated lab host and saves screenshots; never touches the user's package. */
@RunWith(Parameterized::class)
class StatementEvidenceTest(private val mode: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dark get() = mode == "dark"
    private val fontScale get() = if (mode == "large") 1.5f else 1f
    private val owner = "11111111-1111-1111-1111-111111111111"
    private val cardId = "22222222-2222-2222-2222-222222222222"
    private val statement = StatementImport("import", owner, "fp", "BCP", "0771", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 25),
        LocalDate.of(2026, 9, 22), true, true, null, StatementImportStatus.PENDING, 1000, 604801000)
    private val categories = listOf(
        CategoryOption("food", "Alimentación", "restaurant", CategoryType.GENERAL, color = "#D97706"),
        CategoryOption("fun", "Ocio", "movie", CategoryType.GENERAL, color = "#7C3AED"),
        CategoryOption("ride", "Transporte", "directions_car", CategoryType.GENERAL, color = "#2563EB"),
    )
    private fun line(i: Int, kind: StatementLineKind, desc: String, minor: Long, day: Int, category: String? = null, currency: Currency = Currency.PEN,
        duplicate: Boolean = false, selected: Boolean = true) = StatementImportLine("l$i", "import", owner, "f$i", i, kind, LocalDate.of(2026, 8, day), LocalDate.of(2026, 8, day),
        desc, minor, currency, null, selected, category, possibleDuplicate = duplicate)
    private val lines = listOf(
        line(0, StatementLineKind.PURCHASE, "TAMBO DOMINICOS", 1890, 3, "food"),
        line(1, StatementLineKind.PURCHASE, "JOINNUS VFF S", 6000, 3, "fun"),
        line(2, StatementLineKind.PURCHASE, "TAMBO TDA NARANJAL", 389, 3),
        line(3, StatementLineKind.REFUND, "DEVOLUCION TAMBO", 389, 4),
        line(4, StatementLineKind.PURCHASE, "NETFLIX.COM", 1299, 6, currency = Currency.USD),
        line(5, StatementLineKind.UNKNOWN, "RAPPI PE 4821", 4650, 7, duplicate = true),
        line(6, StatementLineKind.PURCHASE, "UBER *TRIP", 1420, 8, "ride"),
        line(7, StatementLineKind.INTEREST, "INTERES COMPENSATORIO", 3815, 25),
    )
    private val card = CreditCard(CardId(cardId), UserId(owner), issuer = "BCP", network = CardNetwork.VISA, lastFourDigits = "0771", currency = Currency.PEN,
        creditLimitMinorUnits = 600000, billingDay = 25, dueDay = 22)
    private fun review(card: Boolean = true, message: String? = null, failures: Map<String, StatementLineFailureReason> = emptyMap(), stmt: StatementImport = statement,
        rows: List<StatementImportLine> = lines) =
        StatementReviewUiState(loading = false, snapshot = StatementImportSnapshot(if (card) stmt.copy(cardId = cardId) else stmt, rows),
            cards = listOf(this.card), categories = categories, message = message, failures = failures)
    private val scan = StatementScanSummary("import", "BCP •••• 0771", 53, "S/ 2,802.23", "US$ 24.49", true,
        lines.map { it.description to statementPlainAmount(it.amountMinor) })

    @Before fun prepareLabHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("com.kipu.app.lab", instrumentation.targetContext.packageName)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true); compose.activity.setTurnScreenOn(true)
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Composable private fun Themed(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { KipuTheme(darkTheme = dark, content = content) }
    }

    @Composable private fun Hub(state: StatementImportUiState) = ImportMovementsContent(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val pending = StatementImport("old", owner, "fp2", "BCP", "0771", LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 22),
        true, true, null, StatementImportStatus.PENDING, 1000, 604801000)

    @Test fun hubStates() {
        var state = androidx.compose.runtime.mutableStateOf(StatementImportUiState(pending = listOf(pending)))
        compose.setContent { Themed { Hub(state.value) } }
        compose.onNodeWithTag("statement_import_entry").assertIsDisplayed()
        compose.onNodeWithTag("statement_pick_pdf").assertExists()
        capture("hub")
        compose.runOnIdle { state.value = StatementImportUiState(premiumLocked = true) }
        compose.onNodeWithText("Ver Premium").assertExists()
        capture("hub-free")
        compose.runOnIdle { state.value = StatementImportUiState(pending = listOf(pending), showPassword = true, fileName = "EstadoCuenta_oct2026.pdf", fileSize = 188_000) }
        compose.onNodeWithTag("statement_password_dialog").assertIsDisplayed()
        capture("password")
        compose.runOnIdle { state.value = state.value.copy(password = "clave123", wrongPassword = true, attemptsLeft = 2) }
        compose.onNodeWithText("Contraseña incorrecta. Te quedan 2 intentos.").assertExists()
        capture("password-wrong")
        compose.runOnIdle { state.value = StatementImportUiState(lockedOut = true, fileName = "EstadoCuenta_oct2026.pdf", fileSize = 188_000) }
        compose.onNodeWithTag("statement_locked").assertExists()
        capture("password-locked")
        compose.runOnIdle { state.value = StatementImportUiState(showConsent = true) }
        compose.onNodeWithTag("statement_consent").assertExists()
        capture("consent")
        compose.runOnIdle { state.value = StatementImportUiState(showPremium = true) }
        compose.onNodeWithTag("statement_premium").assertExists()
        capture("premium")
    }

    @Test fun importNotices() {
        var state = androidx.compose.runtime.mutableStateOf(StatementImportUiState())
        compose.setContent { Themed { Hub(state.value) } }
        listOf(StatementImportNotice.NOT_STATEMENT to "not-statement", StatementImportNotice.UNSUPPORTED_BANK to "unsupported", StatementImportNotice.UNREADABLE to "unreadable",
            StatementImportNotice.ALREADY_IMPORTED to "already").forEach { (notice, name) ->
            compose.runOnIdle { state.value = StatementImportUiState(notice = notice, fileName = "EstadoCuenta_oct2026.pdf", fileSize = 188_000) }
            compose.onNodeWithText("Elegir otro PDF").assertExists()
            capture("notice-$name")
        }
    }

    @Test fun scanAnimationFrames() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Themed { Hub(StatementImportUiState(reading = true, scan = scan, openImportId = "import")) } }
        compose.mainClock.advanceTimeBy(1_900)
        capture("scan-mid")
        compose.mainClock.advanceTimeBy(1_900)
        capture("scan-fold")
        compose.mainClock.advanceTimeBy(1_600)
        capture("scan-done")
    }

    @Test fun reviewStates() {
        val state = androidx.compose.runtime.mutableStateOf(review(card = false))
        compose.setContent { Themed {
            StatementReviewScreen(state.value, {}, {}, { _, _ -> }, {}, { _, _, _ -> }, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("statement_card_required").assertExists()
        compose.onNodeWithTag("statement_confirm").assertIsNotEnabled()
        capture("review-no-card")
        compose.runOnIdle { state.value = review() }
        compose.onNodeWithTag("statement_confirm").assertIsEnabled() // the sheet explains the missing categories
        capture("review-card")
        compose.onNodeWithTag("statement_review_list").performScrollToNode(hasTestTag("statement_filter_review"))
        compose.onNodeWithTag("statement_filter_review").performClick()
        capture("review-filter")
        compose.runOnIdle { state.value = review(stmt = statement.copy(reconciledPen = false), message = "Registraste 3 movimientos. Revisa los que necesitan atención y vuelve a intentarlo.",
            failures = mapOf("l5" to StatementLineFailureReason.ClassificationRequired)) }
        compose.onNodeWithTag("statement_review_list").performScrollToNode(hasTestTag("statement_filter_all"))
        compose.onNodeWithTag("statement_filter_all").performClick()
        capture("review-mismatch-failure")
        compose.runOnIdle { state.value = review(rows = emptyList()) }
        capture("review-empty")
        compose.runOnIdle { state.value = StatementReviewUiState(loading = false, snapshot = StatementImportSnapshot(statement.copy(cardId = cardId, status = StatementImportStatus.COMPLETED),
            lines.map { it.copy(status = StatementImportLineStatus.REGISTERED) }), cards = listOf(card), categories = categories, successCount = 8) }
        compose.onNodeWithTag("statement_success").assertExists()
        capture("review-success")
    }

    @Test fun editConfirmAndDiscard() {
        val ready = lines.map { if (it.kind == StatementLineKind.PURCHASE && it.categoryId == null) it.copy(categoryId = "food") else it }
        compose.setContent { Themed { StatementReviewScreen(review(rows = ready), {}, {}, { _, _ -> }, {}, { _, _, _ -> }, {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("statement_line_l0").performClick()
        compose.onNodeWithTag("statement_save_line").assertExists()
        capture("edit")
        compose.onNodeWithTag("statement_edit_amount").performTextReplacement("0")
        compose.onNodeWithTag("statement_save_line").performClick()
        compose.onNodeWithText("Escribe un importe mayor a 0").assertExists()
        capture("edit-error")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("statement_confirm").assertIsEnabled().performClick()
        compose.onNodeWithTag("statement_confirm_sheet").assertExists()
        capture("confirm")
    }

    private fun capture(name: String) {
        if (compose.mainClock.autoAdvance) compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.filesDir, "statement-evidence").apply { mkdirs() }
        File(directory, "$mode-$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf("light"), arrayOf("dark"), arrayOf("large"))
    }
}
