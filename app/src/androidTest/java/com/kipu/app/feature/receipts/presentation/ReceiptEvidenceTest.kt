package com.kipu.app.feature.receipts.presentation

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.receipts.domain.*
import com.kipu.app.navigation.ReceiptMetadataEditor
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.rememberCalmEmeraldColors
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Saves only synthetic lab UI; refuses to capture or run against the user's package. */
@RunWith(Parameterized::class)
class ReceiptEvidenceTest(private val mode: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dark get() = mode == "dark"
    private val fontScale get() = if (mode == "large") 1.5f else 1f
    private val at = LocalDateTime.of(2026, 10, 24, 12, 41).atZone(ZoneId.of("America/Lima")).toInstant().toEpochMilli()
    private val expense = ReceiptCandidate("expense", "synthetic-owner", amountMinor = 3550, occurredAt = at,
        recipient = "Lucía Torres P.", reference = "05862147", referenceFingerprint = "synthetic-ref",
        imageFingerprint = "synthetic-image", status = ReceiptCandidateStatus.PENDING,
        createdAt = 3, updatedAt = 3, isIncome = false)
    private val income = expense.copy(id = "income", recipient = "Carlos Ríos M.", amountMinor = 12000, isIncome = true, createdAt = 2)
    private val incomplete = expense.copy(id = "incomplete", recipient = null, amountMinor = null, occurredAt = null, createdAt = 1)
    private val pending get() = listOf(expense, income, incomplete)

    @Before fun prepareLabHost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals("com.kipu.app.lab", instrumentation.targetContext.packageName)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Composable private fun EvidenceTheme(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            KipuTheme(darkTheme = dark, content = content)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun InboxFrame(
        rows: List<ReceiptCandidate> = pending,
        registered: List<RegisteredYapeReceipt> = emptyList(),
        snackbar: SnackbarHostState = remember { SnackbarHostState() },
        newId: String? = null,
    ) {
        val palette = rememberCalmEmeraldColors()
        var footerHeight by remember { mutableStateOf(0.dp) }
        Scaffold(containerColor = palette.background, snackbarHost = { SnackbarHost(snackbar,
            Modifier.padding(bottom = if (rows.isNotEmpty()) footerHeight else 0.dp)) }, topBar = {
            TopAppBar(title = { Text("Comprobantes Yape") },
                navigationIcon = { IconButton({}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { if (rows.isNotEmpty() || registered.isNotEmpty()) IconButton({}) { Icon(Icons.Filled.HelpOutline, "Ayuda para compartir desde Yape") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background))
        }) { padding ->
            ReceiptReviewScreen(rows, {}, { _, _ -> }, {}, {}, modifier = Modifier.padding(padding),
                registered = registered, newCandidateId = newId, onBottomBarHeightChanged = { footerHeight = it })
        }
    }

    @Test fun captureGuideFinalScenesAndFooter() {
        compose.setContent { EvidenceTheme { ReceiptShareGuide({}, reducedMotion = true) } }
        compose.onNodeWithText("Volver a comprobantes").assertIsDisplayed()
        android.os.SystemClock.sleep(900)
        capture("guide-step-1")
        compose.onNodeWithTag("receipt_guide_step_2").performScrollTo().performClick()
        capture("guide-step-2")
        compose.onNodeWithTag("receipt_guide_step_3").performScrollTo().performClick()
        compose.onNodeWithText("Registrado").assertExists()
        capture("guide-step-3")
        if (mode == "large") {
            compose.onNodeWithText("¿Tienes una captura?").performScrollTo()
            capture("guide-step-3-footer")
        }
    }

    @Test fun captureInboxThreeStates() {
        var screen by mutableIntStateOf(0)
        compose.setContent { EvidenceTheme { key(screen) {
            when (screen) {
                0 -> InboxFrame(rows = emptyList())
                1 -> InboxFrame()
                else -> InboxFrame(rows = emptyList(), registered = listOf(
                    RegisteredYapeReceipt(expense.copy(status = ReceiptCandidateStatus.CONFIRMED), "movement-expense"),
                    RegisteredYapeReceipt(income.copy(status = ReceiptCandidateStatus.CONFIRMED), "movement-income")))
            }
        } } }
        capture("inbox-empty")
        
        compose.runOnIdle { screen = 1 }
        capture("inbox-pending")
        compose.runOnIdle { screen = 2 }
        capture("inbox-caught-up")
        
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun captureReviewQueueAndIncompleteForm() {
        val queue = ReceiptReviewQueue(pending.map { it.id })
        compose.setContent { EvidenceTheme {
            val palette = rememberCalmEmeraldColors()
            Scaffold(containerColor = palette.background, topBar = {
                TopAppBar(title = { Text("Revisar comprobante") }, actions = { ReceiptQueuePosition(queue) },
                    navigationIcon = { IconButton({}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background))
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    ReceiptQueueSegments(queue)
                    ReceiptMetadataEditor(expense, false, { _, _ -> }, modifier = Modifier.weight(1f), onDiscard = {})
                }
            }
        } }
        compose.onNodeWithText("1 de 3").assertIsDisplayed()
        capture("review-1-of-3")
        compose.onNodeWithTag("receipt_reference").performScrollTo()
        capture("review-form-bottom")
    }

    @Test fun captureAddedSnackbar() {
        val snackbar = SnackbarHostState()
        compose.mainClock.autoAdvance = false
        compose.setContent { EvidenceTheme {
            InboxFrame(snackbar = snackbar, newId = expense.id)
            LaunchedEffect(Unit) { snackbar.showSnackbar("Comprobante agregado", "Ver", duration = SnackbarDuration.Indefinite) }
        } }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Comprobante agregado").assertIsDisplayed()
        capture("inbox-added-snackbar")
    }

    @Test fun captureAllImportNotices() {
        var notice by mutableStateOf(ReceiptImportNotice.NOT_YAPE)
        compose.setContent { EvidenceTheme {
            InboxFrame()
            key(notice) { ReceiptImportNoticeSheet(notice, {}, {}, {}, {}, {}, expense, "captura_yape.png", 1_258_291) }
        } }
        ReceiptImportNotice.entries.forEach { value ->
            compose.runOnIdle { notice = value }
            compose.onNodeWithText("Elegir otra imagen").assertExists()
            capture("notice-${value.name.lowercase()}")
        }
    }

    private fun capture(name: String) {
        if (compose.mainClock.autoAdvance) compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Semantics can settle before SurfaceFlinger presents the activity's new frame.
        // Allow the device compositor to commit it before taking a full-window screenshot.
        android.os.SystemClock.sleep(250)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.filesDir, "receipts-evidence").apply { mkdirs() }
        val file = File(directory, "$mode-$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf("light"), arrayOf("dark"), arrayOf("large"))
    }
}
