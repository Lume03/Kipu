package com.kipu.app.feature.movements.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.usecase.ObserveInstruments
import com.kipu.app.feature.categories.domain.CategoriesRepository
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.usecase.ObserveCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Read-only catalogs for history filters; no registration draft or financial operation is created. */
data class MovementFiltersCatalog(
    val accounts: List<Account> = emptyList(),
    val cards: List<CreditCard> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    val merchants: List<MerchantCatalogEntry> = emptyList(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MovementFiltersCatalogViewModel @Inject constructor(
    instruments: ObserveInstruments,
    categories: ObserveCategories,
    repository: CategoriesRepository,
    session: SessionCoordinator,
) : ViewModel() {
    val state = session.localAccess.map { (it as? LocalAccess.Available)?.userId }
        .distinctUntilChanged().flatMapLatest { owner ->
            if (owner == null) flowOf(MovementFiltersCatalog())
            else combine(
                instruments.observeAccounts(), instruments.observeCards(),
                categories(UserId(owner)), repository.observeMerchantCatalog(),
            ) { accounts, cards, roots, merchants ->
                MovementFiltersCatalog(
                    accounts = accounts.filter { it.userId.value == owner },
                    cards = cards.filterIsInstance<CreditCard>().filter { it.userId.value == owner },
                    categories = roots.flatMap { root ->
                        (listOf(root to null) + root.subcategories.map { it to root }).map { (item, parent) ->
                            CategoryOption(
                                id = item.category.id.value, name = item.displayName, icon = item.icon,
                                categoryType = item.category.categoryType,
                                parentCategoryId = parent?.category?.id?.value,
                                parentName = parent?.displayName, color = item.color,
                            )
                        }
                    },
                    merchants = merchants,
                )
            }.onStart { emit(MovementFiltersCatalog()) }.catch { emit(MovementFiltersCatalog()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovementFiltersCatalog())
}
