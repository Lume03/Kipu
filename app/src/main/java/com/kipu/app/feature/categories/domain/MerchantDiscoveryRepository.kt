package com.kipu.app.feature.categories.domain

import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class MerchantSearchCandidate(
    val name: String,
    val domain: String,
    val logoUrl: String? = null,
    val alreadyInCatalog: Boolean = false,
    val merchantId: String? = null,
    val query: String,
)
data class MerchantSearchResult(
    val localResults: List<MerchantCatalogEntry>,
    val candidates: List<MerchantSearchCandidate> = emptyList(),
)
interface MerchantDiscoveryRepository {
    suspend fun searchInternet(query: String): List<MerchantSearchCandidate>
    suspend fun add(candidate: MerchantSearchCandidate): MerchantCatalogEntry
    suspend fun createPrivate(name: String): MerchantCatalogEntry
}
class SearchMerchants @Inject constructor(
    private val catalog: CategoriesRepository,
    private val discovery: MerchantDiscoveryRepository,
) {
    suspend operator fun invoke(query: String, internet: Boolean = false): MerchantSearchResult {
        val local = catalog.searchMerchants(query).first()
        return MerchantSearchResult(local, if (internet) discovery.searchInternet(query) else emptyList())
    }
}
class AddMerchantFromSearch @Inject constructor(private val repository: MerchantDiscoveryRepository) {
    suspend operator fun invoke(candidate: MerchantSearchCandidate) = repository.add(candidate)
}
class CreatePrivateMerchant @Inject constructor(private val repository: MerchantDiscoveryRepository) {
    suspend operator fun invoke(name: String): MerchantCatalogEntry {
        require(name.trim().length in 1..60) { "El nombre debe tener entre 1 y 60 caracteres" }
        return repository.createPrivate(name.trim())
    }
}
