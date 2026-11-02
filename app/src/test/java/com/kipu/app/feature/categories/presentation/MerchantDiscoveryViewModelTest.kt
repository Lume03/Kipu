package com.kipu.app.feature.categories.presentation

import com.kipu.app.feature.categories.domain.*
import com.kipu.app.feature.categories.domain.model.*
import com.kipu.app.feature.categories.presentation.components.MerchantDiscoveryViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MerchantDiscoveryViewModelTest {
    @Test fun `typing searches local and explicit button searches internet`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val catalog = mockk<CategoriesRepository>()
            val discovery = mockk<MerchantDiscoveryRepository>()
            every { catalog.searchMerchants(any()) } returns flowOf(emptyList())
            coEvery { discovery.searchInternet("Shop") } returns listOf(MerchantSearchCandidate("Shop","shop.test",query="shop"))
            val vm=MerchantDiscoveryViewModel(catalog,SearchMerchants(catalog,discovery),AddMerchantFromSearch(discovery),CreatePrivateMerchant(discovery))
            vm.onQueryChanged("Shop")
            runCurrent()
            coVerify(exactly=0) { discovery.searchInternet(any()) }
            vm.searchInternet()
            runCurrent()
            assertEquals("shop.test",vm.state.value.candidates.single().domain)
            vm.onQueryChanged("Other")
            runCurrent()
            assertTrue(vm.state.value.candidates.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `save publishes selection and duplicate taps do not duplicate add`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val catalog=mockk<CategoriesRepository>()
            val discovery=mockk<MerchantDiscoveryRepository>()
            val candidate=MerchantSearchCandidate("Shop","shop.test",query="shop")
            val merchant=MerchantCatalogEntry(MerchantId("id"),"Shop","shop")
            coEvery { discovery.add(candidate) } coAnswers { kotlinx.coroutines.delay(100); merchant }
            val vm=MerchantDiscoveryViewModel(catalog,SearchMerchants(catalog,discovery),AddMerchantFromSearch(discovery),CreatePrivateMerchant(discovery))
            vm.addCandidate(candidate)
            runCurrent()
            vm.addCandidate(candidate)
            advanceUntilIdle()
            assertEquals(merchant,vm.state.value.addedMerchant)
            assertFalse(vm.state.value.isSaving)
            coVerify(exactly=1) { discovery.add(candidate) }
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `private use case validates before repository and trims accepted names`() = runTest {
        val discovery=mockk<MerchantDiscoveryRepository>()
        val create=CreatePrivateMerchant(discovery)
        assertTrue(runCatching { create(" ") }.isFailure)
        coVerify(exactly=0) { discovery.createPrivate(any()) }
        coEvery { discovery.createPrivate("Shop") } returns MerchantCatalogEntry(MerchantId("id"),"Shop","shop")
        assertEquals("Shop",create(" Shop ").name)
    }
}
