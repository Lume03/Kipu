package com.kipu.app.feature.categories.presentation.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.feature.categories.domain.*
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MerchantDiscoveryState(
    val query: String = "",
    val localResults: List<MerchantCatalogEntry> = emptyList(),
    val candidates: List<MerchantSearchCandidate> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val addedMerchant: MerchantCatalogEntry? = null,
)
@HiltViewModel
class MerchantDiscoveryViewModel @Inject constructor(
    private val catalog: CategoriesRepository,
    private val search: SearchMerchants,
    private val add: AddMerchantFromSearch,
    private val create: CreatePrivateMerchant,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MerchantDiscoveryState())
    val state = mutableState.asStateFlow()
    private var localJob: Job? = null
    private var internetJob: Job? = null
    fun onQueryChanged(query: String) {
        localJob?.cancel()
        internetJob?.cancel()
        mutableState.update { it.copy(query = query, candidates = emptyList(), error = null, isSearching = false, addedMerchant = null) }
        localJob = viewModelScope.launch {
            catalog.searchMerchants(query).collect { rows -> mutableState.update { it.copy(localResults = rows) } }
        }
    }
    /** Called only by the explicit Internet button; typing never calls the server. */
    fun searchInternet() {
        val query = state.value.query
        if (query.trim().length !in 2..60 || state.value.isSearching) return
        mutableState.update { it.copy(isSearching = true, error = null) }
        internetJob = viewModelScope.launch {
            try {
                val result = search(query, internet = true)
                mutableState.update { it.copy(candidates = result.candidates, isSearching = false) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutableState.update { it.copy(error = e.message ?: "No pudimos buscar comercios", isSearching = false) } }
        }
    }
    fun addCandidate(candidate: MerchantSearchCandidate) = save { add(candidate) }
    fun createPrivate() { val name = state.value.query; save { create(name) } }
    private fun save(action: suspend () -> MerchantCatalogEntry) {
        if (state.value.isSaving) return
        mutableState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val merchant = action()
                mutableState.update { it.copy(isSaving = false, addedMerchant = merchant) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutableState.update { it.copy(isSaving = false, error = e.message ?: "No pudimos guardar el comercio") } }
        }
    }
    fun clearError() { mutableState.update { it.copy(error = null) } }
    fun consumeAddedMerchant() { mutableState.update { it.copy(addedMerchant = null) } }
}
