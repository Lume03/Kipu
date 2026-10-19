package com.kipu.app.feature.categories.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantCategoryChoice
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantCategoryPreferenceContent
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantCategoryPreferenceUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MerchantCategoryPreferenceScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun freeUserSeesFutureOnlyPreferenceAndEligibleChoices() {
        val owner = UserId.generate()
        val merchant = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        val category = category(owner, "Alimentación")

        composeTestRule.setContent {
            MerchantCategoryPreferenceContent(
                state = MerchantCategoryPreferenceUiState(
                    merchants = listOf(merchant),
                    selectedMerchant = merchant,
                    eligibleCategories = listOf(category),
                    selectedCategoryId = category.category.id,
                ),
                onSelectMerchant = {},
                onSelectCategory = {},
                onSave = {},
                onClear = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Volver").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Categoría por comercio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disponible también en Free.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Solo afecta operaciones futuras.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tambo ✓").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cambiar").assertHasClickAction()
        composeTestRule.onNodeWithText("Filtrar categorías").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alimentación").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Categoría principal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar preferencia").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun largeFontScaleKeepsRecoveryAndSaveActionsVisibleAndScrollable() {
        val owner = UserId.generate()
        val merchant = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        val eligible = category(owner, "Otra categoría")
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                MerchantCategoryPreferenceContent(
                    state = MerchantCategoryPreferenceUiState(
                        merchants = listOf(merchant),
                        selectedMerchant = merchant,
                        eligibleCategories = listOf(eligible),
                        needsNewChoice = true,
                        message = "La categoría anterior ya no está disponible. Elige otra.",
                    ),
                    onSelectMerchant = {},
                    onSelectCategory = {},
                    onSave = {},
                    onClear = {},
                )
            }
        }

        composeTestRule.onNodeWithText("La categoría anterior ya no está disponible. Elige otra.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disponible también en Free.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Otra categoría").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Guardar preferencia").assertIsDisplayed().assertIsNotEnabled()
        assertTrue(composeTestRule.onNodeWithText("Guardar preferencia").fetchSemanticsNode().boundsInRoot.height > 0f)
    }

    @Test
    fun categoryFilterKeepsTheBottomSaveActionAvailable() {
        val owner = UserId.generate()
        val merchant = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        val food = category(owner, "Alimentación")
        val transport = category(owner, "Transporte")
        composeTestRule.setContent {
            MerchantCategoryPreferenceContent(
                state = MerchantCategoryPreferenceUiState(
                    selectedMerchant = merchant,
                    eligibleCategories = listOf(food, transport),
                    selectedCategoryId = food.category.id,
                ),
                onSelectMerchant = {}, onSelectCategory = {}, onSave = {}, onClear = {},
            )
        }

        val categoryFilter = composeTestRule.onAllNodes(hasSetTextAction())
        categoryFilter.assertCountEquals(1)
        categoryFilter[0].performTextInput("trans")
        composeTestRule.onNodeWithText("Transporte").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Alimentación").assertCountEquals(0)
        composeTestRule.onNodeWithText("La categoría seleccionada no coincide con el filtro.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar preferencia").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
    }

    private fun category(owner: UserId, name: String) = MerchantCategoryChoice(
        category = Category(
            id = CategoryId.generate(), ownerId = owner, parentId = null,
            origin = CategoryOrigin.CUSTOM, isActive = true, categoryType = CategoryType.GENERAL,
        ),
        displayName = name,
    )
}
