package com.kipu.app.feature.categories.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantCategoryChoice
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantCategoryPreferenceContent
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantCategoryPreferenceUiState
import com.kipu.app.feature.movements.presentation.toCategoryOption
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MerchantCategoryPreferenceScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun freeUserChoosesAnEligibleRootAndSavesAFutureOnlyPreference() {
        val owner = UserId.generate()
        val merchant = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        val category = category(owner, "Alimentación")
        var saved = false
        var state by mutableStateOf(
            MerchantCategoryPreferenceUiState(
                selectedMerchant = merchant,
                eligibleCategories = listOf(category),
            ),
        )

        composeTestRule.setContent {
            KipuTheme {
                MerchantCategoryPreferenceContent(
                    state = state,
                    onSelectMerchant = {},
                    onSelectCategory = { state = state.copy(selectedCategoryId = it) },
                    onSave = { saved = true },
                    onClear = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Volver").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("Categoría por comercio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disponible también en Free.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Solo afecta operaciones futuras.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tambo ✓").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cambiar").assertHasClickAction()
        composeTestRule.onAllNodesWithText("Filtrar categorías").assertCountEquals(0)
        composeTestRule.onNodeWithText("Guardar preferencia").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("merchant_category_picker_open").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("category_empty_create_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("category_root_${category.option.id}").assertHasClickAction().performClick()
        composeTestRule.onNodeWithTag("pastel_category_picker_sheet").assertDoesNotExist()
        composeTestRule.onNodeWithText("Alimentación").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar preferencia").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        composeTestRule.runOnIdle {
            assertEquals(category.category.id, state.selectedCategoryId)
            assertTrue(saved)
        }
    }

    @Test
    fun largeFontScaleKeepsRecoveryAndSaveActionsVisibleAndScrollable() {
        val owner = UserId.generate()
        val merchant = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        val eligible = category(owner, "Otra categoría")
        composeTestRule.setContent {
            KipuTheme {
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
        }

        composeTestRule.onNodeWithText("Disponible también en Free.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("merchant_category_picker_open").performScrollTo().assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithText("La categoría anterior ya no está disponible. Elige otra.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar preferencia").assertIsDisplayed().assertIsNotEnabled()
        assertTrue(composeTestRule.onNodeWithText("Guardar preferencia").fetchSemanticsNode().boundsInRoot.height > 0f)
    }

    @Test
    fun pickerSelectsASubcategoryAndShowsItsHierarchyBeforeSaving() {
        val owner = UserId.generate()
        val merchant = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        val food = category(owner, "Alimentación")
        val cafe = category(owner, "Cafeterías", parent = food)
        var saved = false
        var state by mutableStateOf(
            MerchantCategoryPreferenceUiState(
                selectedMerchant = merchant,
                eligibleCategories = listOf(food, cafe),
                selectedCategoryId = food.category.id,
            ),
        )
        composeTestRule.setContent {
            KipuTheme {
                MerchantCategoryPreferenceContent(
                    state = state,
                    onSelectMerchant = {},
                    onSelectCategory = { state = state.copy(selectedCategoryId = it) },
                    onSave = { saved = true },
                    onClear = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("merchant_category_picker_open").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("category_expand_${food.option.id}").performClick()
        composeTestRule.onNodeWithTag("category_subcategory_${cafe.option.id}").performClick()
        composeTestRule.onNodeWithTag("pastel_category_picker_sheet").assertDoesNotExist()
        composeTestRule.onNodeWithText("Alimentación › Cafeterías").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar preferencia").assertIsDisplayed().assertIsEnabled().performClick()
        composeTestRule.runOnIdle {
            assertEquals(cafe.category.id, state.selectedCategoryId)
            assertTrue(saved)
        }
    }

    @Test
    fun unavailableCategoryKeepsSaveDisabledAndDoesNotAppearInPicker() {
        val owner = UserId.generate()
        val merchant = MerchantCatalogEntry(MerchantId.generate(), "Tambo", "tambo")
        val unavailable = category(owner, "No disponible")
        composeTestRule.setContent {
            KipuTheme {
                MerchantCategoryPreferenceContent(
                    state = MerchantCategoryPreferenceUiState(
                        selectedMerchant = merchant,
                        selectedCategoryId = unavailable.category.id,
                    ),
                    onSelectMerchant = {}, onSelectCategory = {}, onSave = {}, onClear = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Guardar preferencia").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Elige una categoría").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("No hay categorías elegibles. Activa una categoría disponible o elige otro comercio más tarde.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("merchant_category_picker_open").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("category_root_${unavailable.option.id}").assertDoesNotExist()
        composeTestRule.onNodeWithTag("category_empty_create_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("category_picker_close_button").performClick()
        composeTestRule.onNodeWithText("Guardar preferencia").assertIsNotEnabled()
    }

    private fun category(owner: UserId, name: String, parent: MerchantCategoryChoice? = null): MerchantCategoryChoice {
        val category = Category(
            id = CategoryId.generate(), ownerId = owner, parentId = parent?.category?.id,
            origin = CategoryOrigin.CUSTOM, isActive = true, categoryType = CategoryType.GENERAL,
        )
        val item = CategoryItem(category, CategoryPresentation(category.id, owner, name, "restaurant", "#FED7AA"))
        val parentItem = parent?.let {
            CategoryItem(it.category, CategoryPresentation(it.category.id, owner, it.displayName, it.option.icon, it.option.color.orEmpty()))
        }
        return MerchantCategoryChoice(category, item.toCategoryOption(parentItem))
    }
}
