package com.kipu.app.navigation

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.goals.presentation.GoalFormScreen
import com.kipu.app.feature.goals.presentation.GoalDetailScreen
import com.kipu.app.feature.goals.presentation.GoalDetailUiState
import com.kipu.app.feature.goals.domain.GoalProgressCalculator
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.domain.model.GoalBackingAccount
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.Currency
import java.time.Instant
import com.kipu.app.feature.receipts.domain.ReceiptCandidate
import com.kipu.app.feature.receipts.domain.ReceiptCandidateStatus
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CellularCorrectionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun prepareDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        compose.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
        }
    }

    @Test fun newGoalStartsWithoutValidationErrors() {
        compose.setContent { KipuTheme { GoalFormScreen(null, emptyList(), false, null, {}, onSave = { _, _, _, _, _, _ -> }) } }
        compose.onNodeWithText("Ingresa un nombre para tu meta de ahorro").assertDoesNotExist()
        compose.onNodeWithText("El monto objetivo debe ser mayor a 0.00").assertDoesNotExist()
        compose.onNodeWithTag("goal_form_name").assertIsDisplayed()
        capture("nueva-meta")
    }

    @Test fun bottomNavigationKeepsEveryLabelVisibleAndPlanningActionWorks() {
        var created = false
        compose.setContent { KipuTheme { KipuNavigationBar(GOALS_LIST_ROUTE, {}, {}, onRegisterClick = { created = true }) } }
        listOf("Dinero", "Movimientos", "Deudas", "Planificación").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("btn_root_register").performClick()
        compose.runOnIdle { assertEquals(true, created) }
        capture("barra-inferior")
    }

    @Test fun lightGoalFormMatchesTheReferenceWithoutInitialErrors() {
        compose.setContent { KipuTheme(darkTheme = false) { GoalFormScreen(null, emptyList(), false, null, {}, onSave = { _, _, _, _, _, _ -> }) } }
        compose.onNodeWithText("Ingresa un nombre para tu meta de ahorro").assertDoesNotExist()
        capture("nueva-meta-claro")
    }

    @Test fun narrowNavigationSupportsLargerSystemText() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                KipuTheme(darkTheme = false) {
                    KipuNavigationBar(GOALS_LIST_ROUTE, {}, {}, modifier = Modifier.width(360.dp), onRegisterClick = {})
                }
            }
        }
        listOf("Dinero", "Movimientos", "Deudas", "Planificación").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        capture("barra-texto-grande")
    }

    @Test fun lightGoalDetailGroupsProgressAndBackingIntoCards() {
        val goal = Goal(id = "fixture", userId = "fixture-owner", name = "Fondo de viaje",
            target = Money(100000, Currency.PEN), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val balance = Money(100000, Currency.PEN)
        val zero = Money(0, Currency.PEN)
        val state = GoalDetailUiState(goal = goal, progress = GoalProgressCalculator.calculate(goal, emptyList()),
            backingAccounts = listOf(GoalBackingAccount("bank", "Cuenta de prueba", Currency.PEN, balance, zero, balance, zero)), isLoading = false)
        compose.setContent { KipuTheme(darkTheme = false) { GoalDetailScreen(state, {}, {}, { _, _, _ -> }, { _, _, _ -> }, {}) } }
        compose.onNodeWithText("Respaldo real").assertIsDisplayed()
        compose.onNodeWithText("Avance 0%").assertIsDisplayed()
        capture("detalle-meta-claro")
    }

    @Test fun receiptDatesUseACalendarAndDirectionsRemainSelectable() {
        val candidate = ReceiptCandidate("fixture", "fixture-owner", amountMinor = 8000,
            occurredAt = 1792455840000, recipient = "Contraparte de prueba", reference = "05862147",
            referenceFingerprint = null, imageFingerprint = "fixture-image", status = ReceiptCandidateStatus.PENDING,
            createdAt = 0, updatedAt = 0, isIncome = false)
        compose.setContent { KipuTheme { ReceiptMetadataEditor(candidate, false, { _, _ -> }) } }
        compose.onNodeWithText("Envié · Gasto").performClick()
        capture("comprobante-gasto")
        compose.onNodeWithText("Recibí · Ingreso").performClick()
        capture("comprobante-ingreso")
        compose.onNodeWithTag("receipt_date").performClick()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.DatePicker::class.java))
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button2))
            .perform(androidx.test.espresso.action.ViewActions.click())
    }

    @Test fun rootTabsDoNotRestoreAReceiptOrAccountHistoryOverTheSelectedRoot() {
        compose.runOnUiThread {
            val nav = TestNavHostController(compose.activity).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            nav.graph = nav.createGraph(startDestination = ACCOUNTS_DASHBOARD_ROUTE) {
                composable(ACCOUNTS_DASHBOARD_ROUTE) {}
                composable(MOVEMENTS_HISTORY_ROUTE) {}
                composable(GOALS_LIST_ROUTE) {}
                composable("receipts") {}
            }
            repeat(3) {
                nav.navigate("receipts")
                nav.navigate(MOVEMENTS_HISTORY_ROUTE)
                nav.navigateRootTab(GOALS_LIST_ROUTE)
                assertEquals(GOALS_LIST_ROUTE, nav.currentDestination?.route)
                nav.navigateRootTab(ACCOUNTS_DASHBOARD_ROUTE)
                assertEquals(ACCOUNTS_DASHBOARD_ROUTE, nav.currentDestination?.route)
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/$name.png")
        file.parentFile?.mkdirs()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
