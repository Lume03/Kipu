package com.kipu.app.feature.categories.presentation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.feature.categories.domain.*
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.presentation.components.MerchantDiscoveryViewModel
import com.kipu.app.feature.categories.presentation.components.MerchantPicker
import com.kipu.app.feature.categories.presentation.components.MerchantPickerState
import com.kipu.app.feature.categories.presentation.components.rememberMerchantDiscovery
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Merchant picker + [MerchantDiscoveryViewModel]: internet search only on tap, add, private creation and errors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w375dp-h812dp")
class MerchantPickerDiscoveryTest {
    @get:Rule val compose = createComposeRule()
    private val catalog = mockk<CategoriesRepository>().also { every { it.searchMerchants(any()) } returns flowOf(emptyList()) }
    private val discovery = mockk<MerchantDiscoveryRepository>()
    private val vm = MerchantDiscoveryViewModel(catalog, SearchMerchants(catalog, discovery), AddMerchantFromSearch(discovery), CreatePrivateMerchant(discovery))
    private var selected: MerchantCatalogEntry? = null

    private fun show(entries: List<MerchantCatalogEntry> = emptyList()) {
        compose.setContent {
            KipuTheme {
                var query by remember { mutableStateOf("") }
                MerchantPicker(
                    state = MerchantPickerState(query = query, catalogEntries = entries),
                    onQueryChange = { query = it },
                    onSelectMerchant = { selected = it; query = "" },
                    onSetProvisionalText = {},
                    onClearSelection = {},
                    allowProvisionalText = false,
                    discovery = rememberMerchantDiscovery(vm),
                )
            }
        }
    }

    @Test fun groupFilterPreservesSearchAndCanReturnToAll() {
        val kfc = MerchantCatalogEntry(MerchantId("kfc"), "KFC", "kfc")
        val netflix = MerchantCatalogEntry(MerchantId("netflix"), "Netflix", "netflix")
        show(listOf(kfc, netflix))
        compose.onNodeWithTag("merchant-filter-button").performClick()
        compose.onNodeWithTag("merchant-filter-restaurants").performClick()
        compose.onNodeWithText("KFC").assertExists()
        compose.onNodeWithText("Netflix").assertDoesNotExist()
        compose.onNodeWithTag("merchant_search_input").performTextInput("KFC")
        compose.onNodeWithTag("merchant_item_kfc").assertExists()
        compose.onNodeWithTag("merchant-filter-button").performClick()
        compose.onNodeWithTag("merchant-filter-all").performClick()
        compose.onNodeWithContentDescription("Limpiar búsqueda").performClick()
        compose.onNodeWithText("Netflix").assertExists()
        compose.onNodeWithText("Netflix").performClick()
        assertEquals(netflix, selected)
    }

    @Test fun searchRowFindsCandidatesAndAddingOneSelectsIt() {
        val candidate = MerchantSearchCandidate("Shop Perú", "shop.pe", "https://img.logo.dev/shop.pe", query = "shop")
        val added = MerchantCatalogEntry(MerchantId("new"), "Shop Perú", "shop peru")
        coEvery { discovery.searchInternet("Shop") } returns listOf(candidate)
        coEvery { discovery.add(candidate) } returns added
        show()
        compose.onNodeWithTag("merchant_search_input").performTextInput("Shop")
        compose.onNodeWithText("Buscar “Shop” en internet").assertExists()
        // Typing alone never reaches the server.
        coVerify(exactly = 0) { discovery.searchInternet(any()) }
        compose.onNodeWithTag("merchant_search_internet").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("merchant_candidate_shop.pe").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("shop.pe").assertExists()
        compose.onNodeWithTag("merchant_logo_attribution").assertTextEquals("Logos por Logo.dev")
        compose.onNodeWithTag("merchant_candidate_shop.pe").performClick()
        compose.waitUntil(5_000) { selected != null }
        assertEquals(added, selected)
        compose.waitUntil(5_000) { vm.state.value.addedMerchant == null }
    }

    @Test fun candidateAlreadyInCatalogSelectsTheExistingMerchant() {
        val tambo = MerchantCatalogEntry(MerchantId("tambo"), "Tambo", "tambo")
        coEvery { discovery.searchInternet("Tamb") } returns
            listOf(MerchantSearchCandidate("Tambo", "tambo.pe", alreadyInCatalog = true, merchantId = "tambo", query = "tamb"))
        show(listOf(MerchantCatalogEntry(MerchantId("other"), "Otro", "otro"), tambo))
        compose.onNodeWithTag("merchant_search_input").performTextInput("Tamb")
        compose.onNodeWithTag("merchant_search_internet").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("merchant_candidate_tambo.pe").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("tambo.pe · Ya está en tu catálogo").assertExists()
        compose.onNodeWithTag("merchant_candidate_tambo.pe").performClick()
        assertEquals(tambo, selected)
        coVerify(exactly = 0) { discovery.add(any()) }
    }

    @Test fun createPrivateSelectsTheNewMerchant() {
        val created = MerchantCatalogEntry(MerchantId("private"), "Bodega Lucho", "bodega lucho")
        coEvery { discovery.createPrivate("Bodega Lucho") } returns created
        show()
        compose.onNodeWithTag("merchant_search_input").performTextInput("Bodega Lucho")
        compose.onNodeWithText("Crear “Bodega Lucho” solo para mí").assertExists()
        compose.onNodeWithTag("merchant_create_private").performClick()
        compose.waitUntil(5_000) { selected != null }
        assertEquals(created, selected)
    }

    @Test fun offlineCreationShowsTheErrorAndKeepsThePicker() {
        coEvery { discovery.createPrivate(any()) } throws IllegalStateException("Necesitas conexión a internet para buscar o crear un comercio")
        show()
        compose.onNodeWithTag("merchant_search_input").performTextInput("Bodega")
        compose.onNodeWithTag("merchant_create_private").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("merchant_discovery_error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Necesitas conexión a internet para buscar o crear un comercio").assertExists()
        compose.onNodeWithContentDescription("Cerrar aviso").performClick()
        compose.onNodeWithTag("merchant_discovery_error").assertDoesNotExist()
        assertEquals(null, selected)
    }

    @Test fun manyLocalMatchesHideInternetSearchButKeepPrivateOption() {
        show((1..3).map { MerchantCatalogEntry(MerchantId("p$it"), "Pollería $it", "polleria $it") })
        compose.onNodeWithTag("merchant_search_input").performTextInput("Pollería")
        compose.onNodeWithTag("merchant_search_internet").assertDoesNotExist()
        compose.onNodeWithTag("merchant_create_private").assertExists()
    }
}
