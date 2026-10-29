package com.kipu.app.navigation

import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.kipu.app.feature.auth.presentation.LocalSessionEntryCurtain
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.AccountType
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.presentation.dashboard.DashboardScreen
import com.kipu.app.feature.analysis.presentation.AnalysisScreen
import com.kipu.app.feature.accounts.presentation.detail.AccountDetailScreen
import com.kipu.app.feature.notifications.presentation.NotificationBadgeViewModel
import com.kipu.app.navigation.navigateToNotifications
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.accounts.presentation.instruments.UnifiedInstrumentFormScreen
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogScreen
import com.kipu.app.feature.settings.presentation.SettingsViewModel
import com.kipu.app.feature.movements.domain.model.WalletPaymentChannel

const val ACCOUNTS_DASHBOARD_ROUTE = "accounts/dashboard"
const val ACCOUNT_FORM_ROUTE = "accounts/create"
const val CARD_FORM_ROUTE = "cards/register"
const val RATE_CATALOG_ROUTE = "cards/{cardId}/rates"
const val ACCOUNT_DETAIL_ROUTE = "accounts/instrument/{instrumentId}?isCard={isCard}&startPurchase={startPurchase}"
const val ACCOUNT_DASHBOARD_FEEDBACK_KEY = "account_dashboard_feedback"

internal data class AccountMovementHistoryTarget(
    val accountId: String? = null,
    val paymentChannel: WalletPaymentChannel? = null,
)

internal fun Account.toMovementHistoryTarget(): AccountMovementHistoryTarget? = when (type) {
    AccountType.DIGITAL_WALLET -> when (preset) {
        AccountPreset.YAPE -> AccountMovementHistoryTarget(paymentChannel = WalletPaymentChannel.YAPE)
        AccountPreset.PLIN -> AccountMovementHistoryTarget(paymentChannel = WalletPaymentChannel.PLIN)
        else -> null
    }
    AccountType.CREDIT_LIABILITY -> null
    else -> AccountMovementHistoryTarget(accountId = id.value)
}

internal fun NavController.returnToAccountsDashboard(message: String) {
    val dashboardEntry = runCatching { getBackStackEntry(ACCOUNTS_DASHBOARD_ROUTE) }.getOrNull()
    if (dashboardEntry != null) {
        dashboardEntry.savedStateHandle[ACCOUNT_DASHBOARD_FEEDBACK_KEY] = message
        popBackStack(ACCOUNTS_DASHBOARD_ROUTE, inclusive = false)
    } else {
        navigate(ACCOUNTS_DASHBOARD_ROUTE) {
            popUpTo(graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
        currentBackStackEntry?.savedStateHandle?.set(ACCOUNT_DASHBOARD_FEEDBACK_KEY, message)
    }
}

fun NavController.navigateToAccountForm() {
    navigate(ACCOUNT_FORM_ROUTE)
}

fun NavController.navigateToCardForm() {
    navigate(CARD_FORM_ROUTE)
}

fun NavController.navigateToRateCatalog(cardId: String) {
    navigate("cards/$cardId/rates")
}

fun NavController.navigateToAccountDetail(accountId: String) {
    navigateToInstrumentDetail(accountId, isCard = false, startPurchase = false)
}

fun NavController.navigateToCardDetail(cardId: String, startPurchase: Boolean = false) {
    navigateToInstrumentDetail(cardId, isCard = true, startPurchase = startPurchase)
}

private fun NavController.navigateToInstrumentDetail(instrumentId: String, isCard: Boolean, startPurchase: Boolean) {
    navigate("accounts/instrument/${Uri.encode(instrumentId)}?isCard=$isCard&startPurchase=$startPurchase")
}

fun NavGraphBuilder.accountsDestinations(navController: NavController) {
    // One destination hosts every bottom-bar page, so switching pages never rebuilds a screen.
    composable(ACCOUNTS_DASHBOARD_ROUTE) { backStackEntry ->
        val handle = backStackEntry.savedStateHandle
        val selectedTabIndex = handle.getStateFlow(ROOT_TAB_KEY, RootTab.MONEY).collectAsStateWithLifecycle()
        val moneySelected = remember { derivedStateOf { selectedTabIndex.value == RootTab.MONEY } }
        val movementsSelected = remember { derivedStateOf { selectedTabIndex.value == RootTab.MOVEMENTS } }
        BackHandler(enabled = selectedTabIndex.value != RootTab.MONEY) { handle[ROOT_TAB_KEY] = RootTab.MONEY }
        RetainedRootTabs(selectedTabIndex = selectedTabIndex, pageCount = RootTab.COUNT) {
            RetainedRootPage(active = moneySelected.value) {
                AccountsRootContent(navController, backStackEntry, moneySelected)
            }
            RetainedRootPage(active = movementsSelected.value) {
                MovementHistoryContent(navController, backStackEntry, movementsSelected)
            }
            val planningSelected = selectedTabIndex.value == RootTab.PLANNING
            RetainedRootPage(active = planningSelected) {
                PlanningRootContent(navController, backStackEntry, active = planningSelected)
            }
            RetainedRootPage(active = selectedTabIndex.value == RootTab.ANALYSIS) {
                AnalysisScreen()
            }
        }
    }

    composable(
        route = ACCOUNT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("instrumentId") { type = NavType.StringType },
            navArgument("isCard") { type = NavType.BoolType; defaultValue = false },
            navArgument("startPurchase") { type = NavType.BoolType; defaultValue = false },
        ),
    ) { backStackEntry ->
        val dashboardBackStackEntry = navController.previousBackStackEntry
            ?.takeIf { it.destination.route == ACCOUNTS_DASHBOARD_ROUTE }
        val viewModel: AccountsViewModel = dashboardBackStackEntry
            ?.let { hiltViewModel(it) }
            ?: hiltViewModel(backStackEntry)
        val instrumentId = backStackEntry.arguments?.getString("instrumentId").orEmpty()
        val isCard = backStackEntry.arguments?.getBoolean("isCard") ?: false
        val startPurchaseOnOpen = backStackEntry.arguments?.getBoolean("startPurchase") ?: false
        AccountDetailScreen(
            instrumentId = instrumentId,
            isCard = isCard,
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onReturnToDashboard = { message ->
                navController.returnToAccountsDashboard(message)
            },
            onNavigateToRateCatalog = { cardId -> navController.navigateToRateCatalog(cardId) },
            onNavigateToMovements = { account ->
                account.toMovementHistoryTarget()?.let { target ->
                    navController.navigateToMovementHistory(
                        accountId = target.accountId,
                        paymentChannel = target.paymentChannel?.name,
                    )
                }
            },
            startPurchaseOnOpen = startPurchaseOnOpen,
            onNavigateToLinkedAccount = navController::navigateToAccountDetail,
        )
    }

    composable(ACCOUNT_FORM_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        UnifiedInstrumentFormScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { message -> navController.returnToAccountsDashboard(message) },
            onNavigateToCardDetail = { card ->
                navController.returnToAccountsDashboard("Tarjeta creada.")
                navController.navigateToCardDetail(card.id.value)
            },
            onNavigateToRecordConsumption = { card ->
                navController.returnToAccountsDashboard("Tarjeta creada.")
                navController.navigateToCardDetail(card.id.value, startPurchase = true)
            },
            onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
        )
    }

    composable(CARD_FORM_ROUTE) {
        val viewModel: AccountsViewModel = hiltViewModel()
        UnifiedInstrumentFormScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onSaveSuccess = { message -> navController.returnToAccountsDashboard(message) },
            onNavigateToCardDetail = { card ->
                navController.returnToAccountsDashboard("Tarjeta creada.")
                navController.navigateToCardDetail(card.id.value)
            },
            onNavigateToRecordConsumption = { card ->
                navController.returnToAccountsDashboard("Tarjeta creada.")
                navController.navigateToCardDetail(card.id.value, startPurchase = true)
            },
            initialCreditCard = true,
            onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
        )
    }

    composable(
        route = RATE_CATALOG_ROUTE,
        arguments = listOf(
            navArgument("cardId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
    ) { backStackEntry ->
        val cardIdStr = backStackEntry.arguments?.getString("cardId")
        val cardId = cardIdStr?.let {
            runCatching { CardId(it) }.getOrNull()
        }
        val viewModel: AccountsViewModel = hiltViewModel()
        val products = viewModel.creditProductCatalog.collectAsStateWithLifecycle().value
        val catalogError = viewModel.creditCatalogError.collectAsStateWithLifecycle().value
        val catalogLoading = viewModel.creditCatalogLoading.collectAsStateWithLifecycle().value
        val instruments = viewModel.instrumentsUiState.collectAsStateWithLifecycle().value
        val creditCard = (instruments.activeCards + instruments.archivedCards)
            .filterIsInstance<CreditCard>()
            .firstOrNull { it.id == cardId }
        RateCatalogScreen(
            creditCard = creditCard,
            products = products,
            catalogError = catalogError,
            catalogLoading = catalogLoading,
            cardLoading = instruments.isLoading,
            events = viewModel.events,
            onLoadCatalog = viewModel::loadCreditProductCatalog,
            onUpdatePersonalTea = viewModel::updatePersonalTea,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

@Composable
private fun AccountsRootContent(navController: NavController, backStackEntry: NavBackStackEntry, selected: State<Boolean>) {
        val viewModel: AccountsViewModel = hiltViewModel()
        val settingsViewModel: SettingsViewModel = hiltViewModel()
        val notificationBadgeViewModel: NotificationBadgeViewModel = hiltViewModel()
        // Tells the "Hola de nuevo" curtain, if one is waiting after a sign-in, that the data is on screen.
        val entryCurtain = LocalSessionEntryCurtain.current
        val dashboardState by viewModel.dashboardUiState.collectAsStateWithLifecycle()
        val instrumentsState by viewModel.instrumentsUiState.collectAsStateWithLifecycle()
        val notificationsReady by viewModel.creditNotificationsSettled.collectAsStateWithLifecycle()
        val dashboardLoaded = !dashboardState.isLoading && !instrumentsState.isLoading && notificationsReady
        LaunchedEffect(dashboardLoaded) {
            if (dashboardLoaded) {
                // Let Compose lay out the resolved data/error state before the iris can expose it.
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }
                entryCurtain?.markHomeReady()
            }
        }
        val unreadNotificationCount by notificationBadgeViewModel.unreadCount.collectAsStateWithLifecycle()
        val feedback by backStackEntry.savedStateHandle
            .getStateFlow<String?>(ACCOUNT_DASHBOARD_FEEDBACK_KEY, null)
            .collectAsStateWithLifecycle()
        val openRegisterMovement by backStackEntry.savedStateHandle
            .getStateFlow("open_register_movement", false)
            .collectAsStateWithLifecycle()
        DashboardScreen(
            viewModel = viewModel,
            onToggleMasked = { settingsViewModel.toggleHideBalances() },
            onNavigateToNewAccount = { navController.navigateToAccountForm() },
            onNavigateToNewCard = { navController.navigateToCardForm() },
            onAccountClick = { accountId -> navController.navigateToAccountDetail(accountId) },
            onCardClick = { cardId -> navController.navigateToCardDetail(cardId) },
            onNavigateToSettings = { navController.navigate(PROFILE_SETTINGS_ROUTE) },
            onNavigateToNotifications = navController::navigateToNotifications,
            onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
            unreadNotificationCount = unreadNotificationCount,
            feedbackMessage = feedback,
            onFeedbackConsumed = { backStackEntry.savedStateHandle[ACCOUNT_DASHBOARD_FEEDBACK_KEY] = null },
            openRegisterMovement = openRegisterMovement && selected.value,
            onConsumeRegisterMovement = { backStackEntry.savedStateHandle["open_register_movement"] = false },
            onNavigateToGoals = { navController.navigateToPlanning(PlanningTab.GOALS) },
            onNavigateToCalendar = { navController.navigateToCalendar() },
        )
}
