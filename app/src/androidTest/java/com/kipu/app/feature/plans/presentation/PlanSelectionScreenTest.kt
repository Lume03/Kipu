package com.kipu.app.feature.plans.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.kipu.app.feature.plans.domain.model.CommercialOption
import com.kipu.app.feature.plans.domain.model.FreePlanLimits
import com.kipu.app.feature.plans.domain.model.TrialEligibilitySnapshot
import java.time.Instant
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlanSelectionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun annualIsPreselectedAndPeriodsUpdateThePrice() {
        setScreen()
        compose.onNodeWithText("Elige tu plan").assertIsDisplayed()
        compose.onNodeWithTag("plan-tab-PREMIUM").assertIsSelected()
        compose.onNodeWithTag("plan-option-ANNUAL").assertIsSelected()
        compose.onNodeWithText("S/ 29.99").assertIsDisplayed()
        compose.onNodeWithText("Ahorra 50%").assertExists()
        compose.onNodeWithTag("plan-option-MONTHLY").performClick()
        compose.onNodeWithText("S/ 4.99").assertIsDisplayed()
        compose.onNodeWithText("al mes").assertExists()
        compose.onNodeWithTag("plan-option-LIFETIME").performClick()
        compose.onNodeWithText("S/ 49.99").assertIsDisplayed()
        compose.onNodeWithText("Sin prueba y sin renovación.").assertExists()
    }

    @Test
    fun freeTabShowsPolicyLimitsAndChangesTheSingleAction() {
        var confirmations = 0
        setScreen(onFree = { confirmations++ })
        compose.onNodeWithTag("plan-tab-FREE").performClick()
        compose.onNodeWithTag("plan-free-card").assertExists()
        compose.onNodeWithTag("plan-premium-card").assertDoesNotExist()
        compose.onNodeWithText("S/ 0").assertExists()
        compose.onNodeWithText("Para siempre").assertExists()
        listOf("Cuentas y tarjetas", "Categorías", "Deudas", "Metas de ahorro", "Presupuestos", "Registro manual", "Incluido").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("Confirmar plan").assertDoesNotExist()
        compose.onNodeWithText("Continuar con Free").performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun freeCardUsesProvidedQuotasAndReturnsToPreviousPremiumPeriod() {
        setScreen(limits = FreePlanLimits(instruments = 9, customCategories = 8))
        compose.onNodeWithTag("plan-option-LIFETIME").performClick()
        compose.onNodeWithTag("plan-tab-FREE").performClick()
        compose.onNodeWithText("9").assertExists()
        compose.onNodeWithText("8").assertExists()
        compose.onNodeWithText("Premium quita estos límites").performScrollTo().performClick()
        compose.onNodeWithTag("plan-option-LIFETIME").assertIsSelected()
    }

    @Test
    fun largeFontsKeepTheNoticeAndConfirmationAccessible() {
        compose.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KipuTheme { PlanSelectionScreen(PlanSelectionUiState(), {}, {}, {}) }
            }
        }
        compose.onNodeWithText("Por ahora solo guardamos tu preferencia: no hay cobros ni prueba activa.").assertIsDisplayed()
        compose.onNodeWithTag("plan-confirm").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun confirmationIsDisabledWhileEligibilityLoads() {
        compose.setContent {
            KipuTheme {
                PlanSelectionScreen(PlanSelectionUiState(isLoadingPreference = false), {}, {}, {})
            }
        }
        compose.onNodeWithTag("plan-confirm").assertIsNotEnabled()
        compose.onNodeWithText("Prueba 7 días gratis", substring = true).assertDoesNotExist()
    }

    @Test
    fun trialCopyFollowsVerifiedEligibilityAndSelectedPeriod() {
        val state = mutableStateOf(PlanSelectionUiState(isLoadingPreference = false, isLoadingEligibility = false))
        compose.setContent {
            KipuTheme {
                PlanSelectionScreen(state.value, { state.value = state.value.copy(selectedOption = it) }, {}, {})
            }
        }
        compose.onNodeWithText("S/ 29.99 al año.").assertExists()
        compose.runOnIdle {
            state.value = state.value.copy(eligibility = TrialEligibilitySnapshot.eligible(
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"),
            ))
        }
        compose.onNodeWithText("Prueba 7 días gratis, luego S/ 29.99 al año.").assertExists()
        compose.onNodeWithTag("plan-option-MONTHLY").performClick()
        compose.onNodeWithText("7 días gratis, luego S/ 4.99 al mes.").assertExists()
        compose.onNodeWithTag("plan-option-LIFETIME").performClick()
        compose.onNodeWithText("Sin prueba y sin renovación.").assertExists()
    }

    private fun setScreen(limits: FreePlanLimits = FreePlanLimits(), onFree: () -> Unit = {}) {
        val state = mutableStateOf(PlanSelectionUiState(freeLimits = limits, isLoadingPreference = false, isLoadingEligibility = false))
        compose.setContent {
            KipuTheme {
                PlanSelectionScreen(state.value, { option ->
                    state.value = state.value.copy(selectedOption = option, premiumOption = if (option == CommercialOption.FREE) state.value.premiumOption else option)
                }, {}, onFree)
            }
        }
    }
}
