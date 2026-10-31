package com.kipu.app.feature.categories.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantAliasRulesContent
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantAliasRulesUiState
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Rule
import org.junit.Test

class MerchantAliasRulesScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun freeUserSeesCompactPremiumBenefitInsteadOfBlockedAliasForm() {
        val tambo = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        var openedPremium = false
        composeTestRule.setContent {
            MerchantAliasRulesContent(
                state = MerchantAliasRulesUiState(
                    isPremiumVerified = false,
                    merchants = listOf(tambo),
                ),
                onSourceTextChange = {},
                onMerchantQueryChange = {},
                onSelectMerchant = {},
                onSave = {},
                onDelete = {},
                onNavigateToPremium = { openedPremium = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Volver").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Reglas de alias").assertIsDisplayed()
        composeTestRule.onNodeWithText("PREMIUM").assertIsDisplayed()
        composeTestRule.onNodeWithText("Automatiza tus comercios frecuentes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ver Premium").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        composeTestRule.runOnIdle { assert(openedPremium) }
        composeTestRule.onAllNodesWithText("Texto fuente exacto").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Guardar alias").assertCountEquals(0)
        composeTestRule.onNodeWithText("Sin reglas de alias aún").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aliasExplainsNormalizationAndExactMatchWithLargeTextAndKeepsCanonicalMerchantSeparate() {
        val tambo = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        composeTestRule.setContent {
            KipuTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                    MerchantAliasRulesContent(
                        state = MerchantAliasRulesUiState(
                            isPremiumVerified = true,
                            sourceText = " IZIPAY*TÁMBO ",
                            merchantQuery = "Tambo",
                            merchants = listOf(tambo),
                            selectedMerchant = tambo,
                        ),
                        onSourceTextChange = {},
                        onMerchantQueryChange = {},
                        onSelectMerchant = {},
                        onSave = {},
                        onDelete = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(" IZIPAY*TÁMBO ").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Comercio canónico: Tambo").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("alias_rule_details").assertDoesNotExist()
        composeTestRule.onNodeWithTag("alias_rule_details_toggle").performScrollTo()
            .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        composeTestRule.onNodeWithText("« Izipay* TÁMBO »").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("izipay* tambo").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("«  BEMBOS  »").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("bembos").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Se normaliza como").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Comercio canónico de ejemplo").assertCountEquals(2)
        composeTestRule.onNodeWithText("1. Se normaliza el texto").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Se unifican mayúsculas, tildes y espacios. La puntuación se conserva y el texto original permanece separado.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("2. Coincidencia exacta").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Si el texto normalizado coincide exactamente con tu regla y el comercio está activo, se reconoce ese comercio.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("El alias no asigna un comercio. El texto fuente se conserva para revisión.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Si el mismo texto normalizado apunta a comercios distintos, se requiere revisión manual.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Si Premium o el consentimiento no están vigentes, puedes registrar el movimiento manualmente.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("alias_rule_details_toggle").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("alias_rule_details").assertDoesNotExist()
        composeTestRule.onNodeWithText("Guardar alias").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
}
