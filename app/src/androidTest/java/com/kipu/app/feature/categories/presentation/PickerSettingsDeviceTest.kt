package com.kipu.app.feature.categories.presentation

import android.graphics.Bitmap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.presentation.components.MerchantPickerBottomSheet
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import com.kipu.app.feature.settings.presentation.ProfileSettingsScreen
import com.kipu.app.feature.settings.presentation.SettingsUiState
import com.kipu.app.feature.settings.presentation.SettingsViewModel
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class PickerSettingsDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        compose.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        val dir = File(inst.targetContext.getExternalFilesDir(null), "block2-validation").apply { mkdirs() }
        File(dir, name).outputStream().use { check(inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun nestedFilterSearchClearAndSelect() {
        val kfc = MerchantCatalogEntry(MerchantId("kfc"), "KFC", "kfc")
        val netflix = MerchantCatalogEntry(MerchantId("netflix"), "Netflix", "netflix")
        var selected: MerchantCatalogEntry? = null
        compose.setContent {
            KipuTheme {
                var query by remember { mutableStateOf("") }
                MerchantPickerBottomSheet(
                    state = MerchantPickerState(query = query, catalogEntries = listOf(kfc, netflix)),
                    onQueryChange = { query = it }, onSelectMerchant = { selected = it },
                    onSetProvisionalText = {}, onClearSelection = {}, onDismiss = {}, dismissOnSelect = false,
                )
            }
        }
        compose.onNodeWithTag("merchant_picker_sheet").assertIsDisplayed()
        capture("picker.png")
        compose.onNodeWithTag("merchant-filter-button").performClick()
        compose.onNodeWithTag("merchant-filter-restaurants").assertIsDisplayed()
        compose.onNodeWithTag("merchant-filter-entertainment").assertIsDisplayed()
        capture("filter-sheet.png")
        compose.onNodeWithTag("merchant-filter-restaurants").performClick()
        compose.onNodeWithTag("merchant_item_kfc").assertIsDisplayed()
        compose.onNodeWithTag("merchant_item_netflix").assertDoesNotExist()
        compose.onNodeWithTag("merchant_search_input").performTextInput("KFC")
        compose.onNodeWithTag("merchant_item_kfc").assertExists()
        compose.onNodeWithContentDescription("Limpiar búsqueda").performClick()
        compose.onNodeWithTag("merchant-filter-button").performClick()
        compose.onNodeWithTag("merchant-filter-all").performClick()
        compose.onNodeWithTag("merchant_item_netflix").performClick()
        assertEquals(netflix, selected)
    }

    @Test fun settingsActionsAreVisible() {
        val vm = mockk<SettingsViewModel>(relaxed = true) { every { uiState } returns MutableStateFlow(SettingsUiState()) }
        compose.setContent { KipuTheme { ProfileSettingsScreen(vm, {}, onNavigateToDataPrivacy = {}, onNavigateToPermissions = {}, onNavigateToBiometrics = {}, onNavigateToCategories = {}, onNavigateToReceipts = {}, onNavigateToCaptureInbox = {}) } }
        compose.onNodeWithText("Importar movimientos").performScrollTo().assertIsDisplayed()
        capture("settings-actions.png")
        compose.onNodeWithText("Bloqueo local").performScrollTo().assertIsDisplayed()
        capture("settings-privacy.png")
        compose.onNodeWithText("Capturas por revisar").assertExists()
        compose.onNodeWithText("Configurar Captura").assertExists()
        compose.onNodeWithText("Datos y privacidad").assertExists()
    }
}
