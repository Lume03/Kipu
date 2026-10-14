package com.kipu.app.feature.categories.presentation

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantAliasRulesContent
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantAliasRulesUiState
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
    fun aliasExplainsExactMatchAndKeepsSourceAndCanonicalMerchantSeparate() {
        val tambo = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        composeTestRule.setContent {
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

        composeTestRule.onNodeWithText(" IZIPAY*TÁMBO ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Comercio canónico: Tambo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coincidencia exacta normalizada; la puntuación se conserva.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cómo funcionan las reglas").performClick()
        composeTestRule.onNodeWithText("Sin coincidencia, el comercio no se asigna y el texto queda disponible para revisión.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar alias").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
}
