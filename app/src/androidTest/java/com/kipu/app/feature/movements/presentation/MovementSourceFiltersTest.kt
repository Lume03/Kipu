package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementSource
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MovementSourceFiltersTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectingAndRemovingSourceOnlyChangesDraftUntilApply() {
        var applied: AdvancedFiltersState? = null
        compose.setContent { KipuTheme { MovementFiltersSheet(MovementHistoryUiState(ownerId = "owner"), {},
            { it.validate().also { result -> applied = result.filters } }) } }
        compose.onNodeWithTag("chip_filter_source_ocr").performScrollTo().performClick()
        compose.onNodeWithTag("chip_filter_source_notification").performScrollTo().performClick()
        compose.onNodeWithTag("chip_filter_source_ocr").performScrollTo().performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithTag("btn_apply_filters").performClick()
        compose.runOnIdle { assertEquals(setOf(MovementSource.NOTIFICATION), applied?.sources) }
    }

    @Test fun clearRemovesPreviouslyAppliedSourceSelection() {
        var applied: AdvancedFiltersState? = null
        compose.setContent { KipuTheme { MovementFiltersSheet(MovementHistoryUiState(ownerId = "owner",
            appliedFilters = AdvancedFiltersState(sources = setOf(MovementSource.OCR))), {},
            { it.validate().also { result -> applied = result.filters } }) } }
        compose.onNodeWithTag("chip_filter_source_ocr").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("btn_reset_advanced_filters").performClick()
        compose.onNodeWithTag("chip_filter_source_ocr").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("btn_apply_filters").performClick()
        compose.runOnIdle { assertTrue(applied?.sources.orEmpty().isEmpty()) }
    }

    @Test fun appliedSourceChipEmitsIndividualRemovalKey() {
        var removed: String? = null
        compose.setContent { KipuTheme { MovementAppliedFilters(MovementHistoryUiState(ownerId = "owner",
            appliedFilters = AdvancedFiltersState(sources = setOf(MovementSource.OCR, MovementSource.MANUAL))), { removed = it }) } }
        compose.onNodeWithTag("applied_filter_source:OCR").performClick()
        compose.runOnIdle { assertEquals("source:OCR", removed) }
    }

    @Test fun freeShowsLockedSourceInsteadOfEditableChips() {
        compose.setContent { KipuTheme { MovementFiltersSheet(MovementHistoryUiState(ownerId = "owner",
            accessStatus = MovementHistoryAccessDecision.PremiumRequired(MovementHistoryQuery())), {}, { it.validate() }) } }
        compose.onNodeWithTag("chip_filter_source_ocr").assertDoesNotExist()
        compose.onNodeWithTag("chip_filter_source_manual").assertDoesNotExist()
        compose.onNodeWithText("Fuente", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}
