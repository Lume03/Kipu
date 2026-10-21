package com.kipu.app.ui.component

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.presentation.*
import com.kipu.app.feature.goals.domain.model.Goal
import com.kipu.app.feature.goals.presentation.GoalFormScreen
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Production forms and their nullable date callbacks, with synthetic models only. */
class OptionalDateFormsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun payableCalendarAndClearReachSave() = checkSave(Scene.PAYABLE)
    @Test fun receivableCalendarAndClearReachSave() = checkSave(Scene.RECEIVABLE)
    @Test fun debtEditCalendarAndClearReachSave() = checkSave(Scene.DEBT_EDIT)
    @Test fun newGoalAllowsNoDateAndCalendarAndClearReachSave() = checkSave(Scene.GOAL_NEW)
    @Test fun goalEditCalendarAndClearReachSave() = checkSave(Scene.GOAL_EDIT)

    @Test fun payableDateFieldOpensCalendarWhenTapped() = checkFieldTap(Scene.PAYABLE)
    @Test fun receivableDateFieldOpensCalendarWhenTapped() = checkFieldTap(Scene.RECEIVABLE)

    private fun checkFieldTap(scene: Scene) {
        compose.setContent { KipuTheme { FixtureForm(scene) } }
        compose.onNodeWithTag(scene.field).performScrollTo().performClick()
        compose.onNodeWithTag("${scene.field}-dialog").assertIsDisplayed()
        compose.onNodeWithTag("${scene.field}-cancel").performClick()
        compose.onNodeWithTag(scene.field).assertTextContains("10/11/2026")
    }

    private fun checkSave(scene: Scene) {
        val saved = mutableListOf<LocalDate?>()
        compose.setContent { KipuTheme { FixtureForm(scene, onSave = { saved += it }) } }
        if (scene == Scene.GOAL_NEW) {
            compose.onNodeWithTag("goal_form_name").performTextInput("Meta de ejemplo")
            compose.onNodeWithTag("goal_form_target").performTextInput("100.00")
            save(scene)
            compose.runOnIdle { assertEquals(listOf<LocalDate?>(null), saved); saved.clear() }
            compose.onNodeWithTag(scene.field).performScrollTo().performTextReplacement("2026-11-10")
        }
        compose.onNodeWithTag("${scene.field}-calendar").performScrollTo().performClick()
        compose.onNode(hasText("15", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithTag("${scene.field}-confirm").performClick()
        save(scene)
        compose.runOnIdle { assertEquals(listOf(LocalDate.parse("2026-11-15")), saved) }
        compose.onNodeWithTag("${scene.field}-clear").performScrollTo().performClick()
        save(scene)
        compose.runOnIdle { assertEquals(listOf(LocalDate.parse("2026-11-15"), null), saved) }
        if (scene == Scene.GOAL_NEW || scene == Scene.GOAL_EDIT) {
            compose.onNodeWithTag(scene.field).performScrollTo().performTextReplacement("2026-02-30")
            compose.onNodeWithTag(scene.save).assertIsNotEnabled()
        }
    }

    private fun save(scene: Scene) {
        val button = compose.onNodeWithTag(scene.save)
        button.performClick()
    }

    @Test fun largeTextKeepsDateActionsAndSaveReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                KipuTheme { FixtureForm(Scene.GOAL_EDIT) }
            }
        }
        compose.onNodeWithTag("goal_form_date-calendar").performScrollTo().performClick()
        compose.onNodeWithTag("goal_form_date-cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("goal_form_date-clear").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("goal_save_button").assertIsDisplayed()
    }

    @Test fun captureSyntheticFormsInBothThemes() {
        val scene = mutableStateOf(Scene.PAYABLE)
        val dark = mutableStateOf(false)
        compose.setContent {
            KipuTheme(darkTheme = dark.value) {
                Box(Modifier.fillMaxSize().testTag("date-evidence-root")) { FixtureForm(scene.value) }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "issue-71").apply { mkdirs() }
        for (mode in listOf(false, true)) {
            for (screen in Scene.entries) {
                compose.runOnIdle { scene.value = screen; dark.value = mode }
                compose.onNodeWithTag(screen.field).performScrollTo().assertIsDisplayed()
                capture("date-evidence-root", File(directory, "${screen.name.lowercase()}-${if (mode) "dark" else "light"}.png"))
                compose.onNodeWithTag("${screen.field}-calendar").performScrollTo().performClick()
                compose.onNodeWithTag("${screen.field}-cancel").assertIsDisplayed()
                if (screen == Scene.PAYABLE) {
                    capture("${screen.field}-dialog", File(directory, "calendar-${if (mode) "dark" else "light"}.png"))
                }
                compose.onNodeWithTag("${screen.field}-cancel").performClick()
            }
        }
    }

    private fun capture(tag: String, file: File) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        check(file.length() > 0)
    }
}

private enum class Scene(val field: String, val save: String) {
    PAYABLE("debt-due-date", "save-debt"),
    RECEIVABLE("debt-due-date", "save-debt"),
    DEBT_EDIT("edit-debt-due-date", "edit-debt-save"),
    GOAL_NEW("goal_form_date", "goal_save_button"),
    GOAL_EDIT("goal_form_date", "goal_save_button"),
}

@Composable
private fun FixtureForm(scene: Scene, onSave: (LocalDate?) -> Unit = {}) {
    // Keying the fixture prevents a previous screen's form state leaking into the next capture.
    androidx.compose.runtime.key(scene) {
        val debt = androidx.compose.runtime.remember { mutableStateOf(DebtFormUiState(
            counterpartyName = "Persona de ejemplo", principalAmount = "50.00",
            openingMode = DebtOpeningMode.HISTORICAL,
            dueDateInput = "2026-11-10", dueDate = LocalDate.parse("2026-11-10"),
        )) }
        val edit = androidx.compose.runtime.remember { mutableStateOf(DebtEditUiState(
            counterpartyName = "Persona de ejemplo", principalAmount = "S/ 50.00",
            currencyCode = "PEN", dueDate = "2026-11-10", revision = 1L,
        )) }
        val dateChanged: (String) -> Unit = { raw -> debt.value = debt.value.copy(
            dueDateInput = raw, dueDate = runCatching { LocalDate.parse(raw) }.getOrNull(),
        ) }
        when (scene) {
            Scene.PAYABLE -> PayableDebtFormScreen(debt.value, {}, {}, {}, {}, {}, dateChanged, {}, { onSave(debt.value.dueDate) }, {})
            Scene.RECEIVABLE -> ReceivableDebtFormScreen(debt.value, {}, {}, {}, {}, {}, dateChanged, {}, { onSave(debt.value.dueDate) }, {})
            Scene.DEBT_EDIT -> DebtEditDetailsScreen(edit.value, {}, { edit.value = edit.value.copy(dueDate = it) }, {}, {
                onSave(edit.value.dueDate.takeIf(String::isNotBlank)?.let(LocalDate::parse))
            }, {})
            Scene.GOAL_NEW, Scene.GOAL_EDIT -> GoalFormScreen(
                existingGoal = if (scene == Scene.GOAL_EDIT) fixtureGoal() else null,
                accounts = emptyList(), isSaving = false, errorMessage = null, onNavigateBack = {},
                onSave = { _, _, _, date, _, _, _ -> onSave(date) },
            )
        }
    }
}

private fun fixtureGoal() = Goal(
    id = "issue-71-goal", userId = "issue-71-fixture", name = "Meta de ejemplo",
    target = Money(10_000L, Currency.PEN), targetDate = LocalDate.parse("2026-11-10"),
    createdAt = Instant.parse("2026-10-01T00:00:00Z"), updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
)
