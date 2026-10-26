package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.ui.theme.KipuTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class MovementFiltersCatalogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun globalMultiSelectionChangesOnlyTheDraftUntilApplied() {
        val id = "11111111-1111-4111-8111-111111111111"
        val account = Account(
            id = AccountId(id), userId = UserId("22222222-2222-4222-8222-222222222222"),
            alias = "[QA] Filtro", type = AccountType.CASH, currency = Currency.PEN,
            initialBalance = Money(0, Currency.PEN), openedAt = Instant.EPOCH,
        )
        var applied: AdvancedFiltersState? = null
        compose.setContent {
            KipuTheme {
                MovementFiltersSheet(
                    state = MovementHistoryUiState(ownerId = account.userId.value),
                    onDismiss = {}, onApply = { it.validate().also { result -> applied = result.filters } },
                    catalog = MovementFiltersCatalog(accounts = listOf(account)),
                )
            }
        }
        compose.onNodeWithTag("btn_reference_Cuentas").performScrollTo().performClick()
        compose.onNodeWithTag("account_selector_sheet").assertExists()
        compose.onNodeWithTag("account_item_$id").performClick()
        compose.onNodeWithTag("btn_confirm_selection").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithTag("btn_apply_filters").performClick()
        compose.runOnIdle { assertEquals(setOf(id), applied?.accountIds) }
    }
}
