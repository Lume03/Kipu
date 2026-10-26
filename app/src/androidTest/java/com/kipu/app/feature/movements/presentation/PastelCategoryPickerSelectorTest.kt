package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PastelCategoryPickerSelectorTest {
    @get:Rule
    val compose = createComposeRule()

    private val categories = listOf(
        CategoryOption(id = "food", name = "Alimentación", icon = "restaurant"),
        CategoryOption(id = "food-super", name = "Supermercados", icon = "shopping_cart", parentCategoryId = "food", parentName = "Alimentación"),
        CategoryOption(id = "food-cafe", name = "Cafeterías", icon = "local_cafe", parentCategoryId = "food", parentName = "Alimentación"),
        CategoryOption(id = "transport", name = "Transporte", icon = "directions_car"),
    )

    @Test
    fun currentSubcategoryOpensItsAccordionAndIsTaggedActual() {
        compose.setContent {
            KipuTheme {
                PastelCategoryPickerBottomSheet(
                    categories = categories,
                    selectedCategoryId = "food-super",
                    onSelect = {},
                    onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                    onDismiss = {},
                    directSelectionMode = true,
                )
            }
        }

        compose.onNodeWithTag("category_subcategory_food-super").assertIsDisplayed()
        compose.onNodeWithText("Actual").assertIsDisplayed()
        compose.onNodeWithText("2 Subc").assertIsDisplayed()
    }

    @Test
    fun emptySearchOffersCreationWithTheReferenceCopy() {
        compose.setContent {
            KipuTheme {
                PastelCategoryPickerBottomSheet(
                    categories = categories,
                    selectedCategoryId = null,
                    onSelect = {},
                    onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                    onDismiss = {},
                    directSelectionMode = true,
                )
            }
        }

        compose.onNodeWithTag("category_search_input").performTextInput("zzz")
        compose.onNodeWithText("No encontramos “zzz”").assertIsDisplayed()
        compose.onNodeWithTag("category_empty_create_button").assertIsDisplayed()
    }

    @Test
    fun filterModeConfirmsSeveralCategoriesWithoutCreateAction() {
        var confirmed: Set<String>? = null
        compose.setContent {
            KipuTheme {
                PastelCategoryPickerBottomSheet(
                    categories = categories,
                    selectedCategoryId = null,
                    onSelect = {},
                    onCreateCategory = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
                    onDismiss = {},
                    allowCreate = false,
                    multiSelect = true,
                    onConfirmMultiple = { confirmed = it },
                )
            }
        }

        compose.onNodeWithText("Crear nueva categoría").assertDoesNotExist()
        compose.onNodeWithTag("category_root_transport").performClick()
        compose.onNodeWithTag("category_expand_food").performClick()
        compose.onNodeWithTag("category_subcategory_food-cafe").performClick()
        compose.onNodeWithTag("confirm_category_button").performClick()

        compose.runOnIdle { assertEquals(setOf("transport", "food-cafe"), confirmed) }
    }
}
