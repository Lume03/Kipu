package com.kipu.app.feature.movements.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementEditorUiStateTest {
    @Test
    fun `changing catalog identity with the same display name is a merchant change`() {
        val original = MovementEditorUiState(
            initialAmountMinor = 500L,
            amountText = "5.00",
            initialMerchantName = "Comercio",
            merchantName = "Comercio",
            initialMerchantId = "catalog-1",
            selectedMerchantId = "catalog-1",
        )
        assertFalse(original.hasUnsavedChanges)
        val changed = original.copy(selectedMerchantId = "catalog-2", note = "Nota")
        assertTrue(changed.hasUnsavedChanges)
        assertFalse(changed.isOnlyNoteChanged)
        assertTrue(original.copy(note = "Nota").isOnlyNoteChanged)
        assertTrue(original.copy(selectedMerchantId = null).hasUnsavedChanges)
    }
}
