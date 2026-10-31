package com.kipu.app.feature.accounts.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kipu.app.core.finance.domain.model.*
import com.kipu.app.feature.accounts.domain.model.*
import com.kipu.app.feature.accounts.presentation.components.CreditPurchaseDraft
import com.kipu.app.feature.accounts.presentation.instruments.*
import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.categories.presentation.components.*
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.*
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests production Route wiring to the same merchant/category sheets as quick movement. */
class InstallmentSimulatorSelectorsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sharedSelectorsPassChosenMerchantCategoryAndExactTermsToConfirmation() {
        val card = CreditCard(CardId.generate(), UserId.generate(), "Visa", "BCP", CardNetwork.VISA, "1234", Currency.PEN,
            creditLimitMinorUnits = 500000, billingDay = 26, dueDay = 19)
        val candidate = PurchaseCandidate("candidate", card.id, Money(120000, Currency.PEN), "Compra", Instant.parse("2026-06-25T12:00:00Z"), 6)
        val category = CategoryOption("food", "Comida", "restaurant", CategoryType.EXPENSE)
        val merchant = MerchantCatalogEntry(MerchantId("merchant"), "Comercio elegido", "comercio elegido")
        val draft = CreditPurchaseDraft(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, "6", "45", PurchaseRateSource.USER_ENTERED,
            initializedCardId = card.id.value)
        val state = MutableStateFlow(InstallmentSimulatorUiState(draft = draft, merchantName = "Compra", categories = listOf(category),
            categoriesLoading = false, referenceLoading = false))
        val vm = mockk<InstallmentSimulatorViewModel>(relaxed = true)
        every { vm.state } returns state
        every { vm.updateDraft(any()) } answers { state.value = state.value.copy(draft = firstArg()) }
        every { vm.selectMerchant(any()) } answers { state.value = state.value.copy(merchantName = firstArg<MerchantCatalogEntry>().name, merchantId = firstArg<MerchantCatalogEntry>().id.value) }
        every { vm.selectCategory(any()) } answers { state.value = state.value.copy(categoryId = firstArg<CategoryOption>().id) }
        val picker = mockk<MerchantPickerViewModel>(relaxed = true)
        every { picker.uiState } returns MutableStateFlow(MerchantPickerState(catalogEntries = listOf(merchant)))
        var confirmed: PurchaseCandidate? = null; var terms: PurchaseTerms? = null; var merchantId: String? = null
        compose.setContent { KipuTheme {
            InstallmentSimulatorRoute(candidate, card, null, { purchase, accepted, selectedMerchant ->
                confirmed = purchase; terms = accepted; merchantId = selectedMerchant
            }, {}, viewModel = vm, merchantPickerViewModel = picker)
        } }
        compose.onNodeWithTag("credit_simulator_confirm").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("input_merchant").performScrollTo().performClick()
        compose.onNodeWithTag("merchant_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("merchant_item_merchant").performScrollTo().performClick()
        compose.onNodeWithTag("category_picker_open").performScrollTo().performClick()
        compose.onNodeWithTag("pastel_category_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("category_root_food").performClick()
        compose.onNodeWithTag("credit_simulator_confirm").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("Comercio elegido", confirmed?.merchant)
            assertEquals("food", confirmed?.categoryId)
            assertEquals("merchant", merchantId)
            assertEquals(PurchaseTerms(PurchaseType.INSTALLMENTS, Financing.WITH_INTEREST, 6, 4500, PurchaseRateSource.USER_ENTERED), terms)
            assertEquals(candidate.amount, confirmed?.amount)
            assertEquals(candidate.occurredAt, confirmed?.occurredAt)
        }
    }
}
