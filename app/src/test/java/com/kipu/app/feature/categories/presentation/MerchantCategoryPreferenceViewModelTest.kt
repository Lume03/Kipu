package com.kipu.app.feature.categories.presentation

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.*
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.categories.domain.usecase.*
import com.kipu.app.feature.categories.presentation.merchantrules.MerchantCategoryPreferenceViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MerchantCategoryPreferenceViewModelTest {
    @Test fun `refresh preserves an unsaved choice and follows remote preference after save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val owner = UserId.generate()
            val first = Category(CategoryId.generate(), owner, null, CategoryOrigin.CUSTOM, true, categoryType=CategoryType.EXPENSE)
            val draft = first.copy(id=CategoryId.generate())
            val merchant = MerchantCatalogEntry(MerchantId.generate(), "Shop", "shop")
            val preference = MerchantCategoryPreference(owner, merchant.id, first.id)
            val preferences = MutableStateFlow(listOf(preference))
            val repository = mockk<CategoriesRepository>()
            every { repository.observeMerchantCatalog() } returns flowOf(listOf(merchant))
            every { repository.observeCategories(owner) } returns flowOf(listOf(first,draft))
            every { repository.observeCategoryPresentations(owner) } returns flowOf(emptyList())
            every { repository.observeMerchantCategoryPreferences(owner) } returns preferences
            val session = mockk<SessionCoordinator>()
            every { session.localAccess } returns MutableStateFlow(LocalAccess.Available(owner.value,RemoteSession.Absent))
            val save = mockk<SetMerchantCategoryPreference>()
            coEvery { save(owner,merchant.id,draft.id) } returns Result.success(preference.copy(categoryId=draft.id))
            val vm = MerchantCategoryPreferenceViewModel(repository,session,save,mockk(relaxed=true))
            runCurrent()
            vm.onSelectMerchant(merchant)
            vm.onSelectCategory(draft.id)
            preferences.value = listOf(preference.copy(revision=2))
            runCurrent()
            assertEquals(draft.id,vm.uiState.value.selectedCategoryId)
            vm.onSave()
            runCurrent()
            preferences.value = listOf(preference.copy(categoryId=draft.id,revision=3))
            runCurrent()
            assertEquals(draft.id,vm.uiState.value.selectedCategoryId)
            preferences.value = listOf(preference.copy(revision=4))
            runCurrent()
            assertEquals(first.id,vm.uiState.value.selectedCategoryId)
        } finally { Dispatchers.resetMain() }
    }
}
