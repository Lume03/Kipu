package com.kipu.app.ui.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.Espresso
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KipuOptionalDateFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectedDayIsIsoAndClearRemovesTheOptionalDate() {
        val value = mutableStateOf("2026-11-10")
        compose.setContent {
            KipuTheme { KipuOptionalDateField(value.value, { value.value = it }, "Fecha (opcional)", "Elegir fecha", "date") }
        }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNode(hasText("15", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithTag("date-confirm").performClick()
        compose.runOnIdle { assertEquals("2026-11-15", value.value) }
        awaitCalendarFocus()
        compose.onNodeWithTag("date-clear").performClick()
        compose.runOnIdle { assertEquals("", value.value) }
    }

    @Test
    fun cancellingAndReopeningDiscardsTheUnconfirmedDay() {
        val value = mutableStateOf("2026-11-10")
        val changes = mutableListOf<String>()
        compose.setContent {
            KipuTheme { KipuOptionalDateField(value.value, { value.value = it; changes += it }, "Fecha (opcional)", "Elegir fecha", "date") }
        }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNode(hasText("15", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithTag("date-cancel").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), changes) }
        awaitCalendarFocus()
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNodeWithTag("date-confirm").performClick()
        compose.runOnIdle { assertEquals(listOf("2026-11-10"), changes) }
    }

    @Test
    fun incompleteInputSurvivesCancelAndEmptyInputDoesNotSelectToday() {
        val value = mutableStateOf("2026-11-")
        compose.setContent {
            KipuTheme { KipuOptionalDateField(value.value, { value.value = it }, "Fecha (opcional)", "Elegir fecha", "date") }
        }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNodeWithTag("date-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("date-cancel").performClick()
        compose.runOnIdle { assertEquals("2026-11-", value.value) }
        compose.onNodeWithTag("date-clear").performClick()
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNodeWithTag("date-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("date-cancel").performClick()
        compose.onNodeWithTag("date").performTextReplacement("2028-02-29")
        compose.runOnIdle { assertEquals("2028-02-29", value.value) }
    }

    @Test
    fun disablingWhileOpenDiscardsTheDraftAndDoesNotReopenLater() {
        val enabled = mutableStateOf(true)
        val changes = mutableListOf<String>()
        compose.setContent {
            KipuTheme { KipuOptionalDateField("2026-11-10", { changes += it }, "Fecha (opcional)", "Elegir fecha", "date", enabled = enabled.value) }
        }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNode(hasText("15", substring = true) and hasClickAction()).performClick()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("date-dialog").assertDoesNotExist()
        compose.onNodeWithTag("date-calendar").assertIsNotEnabled()
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithTag("date-dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), changes) }
    }

    @Test
    fun legacyYearIsPreservedAndYearsOutsideTheMaterialDefaultRemainSelectable() {
        val value = mutableStateOf("0000-01-01")
        compose.setContent {
            KipuTheme { KipuOptionalDateField(value.value, { value.value = it }, "Fecha (opcional)", "Elegir fecha", "date") }
        }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNodeWithTag("date-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("date-cancel").performClick()
        compose.runOnIdle { assertEquals("0000-01-01", value.value) }
        compose.onNodeWithTag("date").performTextReplacement("1800-01-01")
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNodeWithTag("date-confirm").performClick()
        compose.runOnIdle { assertEquals("1800-01-01", value.value) }
    }

    @Test
    fun restoringAnOpenDialogNeverCommitsItsDraft() {
        val restoration = StateRestorationTester(compose)
        val changes = mutableListOf<String>()
        restoration.setContent {
            val value = rememberSaveable { mutableStateOf("2026-11-10") }
            KipuTheme { KipuOptionalDateField(value.value, { value.value = it; changes += it }, "Fecha (opcional)", "Elegir fecha", "date") }
        }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNode(hasText("15", substring = true) and hasClickAction()).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("date-cancel").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), changes) }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNodeWithTag("date-confirm").performClick()
        compose.runOnIdle { assertEquals(listOf("2026-11-10"), changes) }
    }

    @Test
    fun backDiscardsTheDraftAndEnterReopensFromTheRestoredFocus() {
        val changes = mutableListOf<String>()
        compose.setContent {
            KipuTheme { KipuOptionalDateField("2026-11-10", { changes += it }, "Fecha (opcional)", "Elegir fecha", "date") }
        }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNode(hasText("15", substring = true) and hasClickAction()).performClick()
        Espresso.pressBack()
        compose.onNodeWithTag("date-dialog").assertDoesNotExist()
        awaitCalendarFocus()
        compose.onNodeWithTag("date-calendar").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("date-confirm").assertIsDisplayed()
        compose.onNodeWithTag("date-cancel").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), changes) }
    }

    @Test
    fun leapDayIsSelectableAndBrowsingAnotherMonthDoesNotApplyADate() {
        val value = mutableStateOf("2028-02-28")
        compose.setContent {
            KipuTheme { KipuOptionalDateField(value.value, { value.value = it }, "Fecha (opcional)", "Elegir fecha", "date") }
        }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNode(hasText("29", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithTag("date-confirm").performClick()
        compose.runOnIdle { assertEquals("2028-02-29", value.value) }
        compose.onNodeWithTag("date-calendar").performClick()
        compose.onNodeWithContentDescription("Cambiar al mes siguiente").performClick()
        compose.onNodeWithText("Marzo de 2028").assertIsDisplayed()
        compose.onNodeWithTag("date-cancel").performClick()
        compose.runOnIdle { assertEquals("2028-02-29", value.value) }
    }

    private fun awaitCalendarFocus() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onNodeWithTag("date-calendar").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
        }
        compose.onNodeWithTag("date-calendar").assertIsFocused()
    }
}
