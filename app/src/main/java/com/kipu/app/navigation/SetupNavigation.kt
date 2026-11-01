package com.kipu.app.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.kipu.app.feature.auth.presentation.AuthNavigationEvent
import com.kipu.app.feature.setup.SetupAccountFormContent
import com.kipu.app.feature.setup.SetupAccountKind
import com.kipu.app.feature.setup.SetupAccountTypeContent
import com.kipu.app.feature.setup.SetupCategoriesContent
import com.kipu.app.feature.setup.OTP_VERIFIED_HOLD_MILLIS
import com.kipu.app.feature.setup.SetupEvent
import com.kipu.app.feature.setup.SetupOtpContent
import com.kipu.app.feature.setup.SetupPlanContent
import com.kipu.app.feature.setup.SetupSummaryContent
import com.kipu.app.feature.setup.SetupViewModel

const val SETUP_GRAPH_ROUTE = "setup"
const val SETUP_ACCOUNT_TYPE_ROUTE = "setup/account-type"
const val SETUP_ACCOUNT_FORM_ROUTE = "setup/account-form"
const val SETUP_CATEGORIES_ROUTE = "setup/categories"
const val SETUP_PLAN_ROUTE = "setup/plan"
const val SETUP_SUMMARY_ROUTE = "setup/summary"
const val SETUP_OTP_ROUTE = "setup/otp"

/** Verification precedes authenticated setup; only the summary persists the choices. */
fun NavGraphBuilder.setupDestinations(
    navController: NavController,
    onAuthenticated: (AuthNavigationEvent.NavigateToHome) -> Unit,
) {
    navigation(startDestination = SETUP_ACCOUNT_TYPE_ROUTE, route = SETUP_GRAPH_ROUTE) {
        composable(SETUP_ACCOUNT_TYPE_ROUTE) { entry ->
            val vm = entry.setupViewModel(navController)
            SetupAccountTypeContent(
                onKind = { kind -> vm.chooseKind(kind); navController.navigate(SETUP_ACCOUNT_FORM_ROUTE) },
                onBack = null,
            )
        }
        composable(SETUP_ACCOUNT_FORM_ROUTE) { entry ->
            val vm = entry.setupViewModel(navController)
            val draft by vm.draft.collectAsStateWithLifecycle()
            var showErrors by remember { mutableStateOf(false) }
            SetupAccountFormContent(
                account = draft.account, showErrors = showErrors,
                onPreset = vm::choosePreset, onSubtype = vm::chooseSubtype, onAlias = vm::onAlias,
                onBalance = vm::onBalance, onCurrency = vm::chooseCurrency,
                onContinue = {
                    if (draft.account.isComplete) navController.navigate(SETUP_CATEGORIES_ROUTE) else showErrors = true
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETUP_CATEGORIES_ROUTE) { entry ->
            val vm = entry.setupViewModel(navController)
            val draft by vm.draft.collectAsStateWithLifecycle()
            var minimumNotice by remember { mutableStateOf(false) }
            SetupCategoriesContent(
                catalog = vm.categoryCatalog, hidden = draft.hiddenCategoryIds, minimumNotice = minimumNotice,
                onToggle = { minimumNotice = !vm.toggleCategory(it) },
                onAll = { active -> minimumNotice = false; vm.setAllCategories(active) },
                onContinue = { navController.navigate(SETUP_PLAN_ROUTE) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETUP_PLAN_ROUTE) { entry ->
            val vm = entry.setupViewModel(navController)
            val draft by vm.draft.collectAsStateWithLifecycle()
            SetupPlanContent(
                selected = draft.plan, onSelect = vm::choosePlan,
                onContinue = { navController.navigate(SETUP_SUMMARY_ROUTE) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETUP_SUMMARY_ROUTE) { entry ->
            val vm = entry.setupViewModel(navController)
            val draft by vm.draft.collectAsStateWithLifecycle()
            val save by vm.save.collectAsStateWithLifecycle()
            androidx.activity.compose.BackHandler(save.loading) {}
            LaunchedEffect(vm) {
                vm.events.collect { event ->
                    if (event is SetupEvent.Completed) onAuthenticated(AuthNavigationEvent.NavigateToHome(event.userId))
                }
            }
            SetupSummaryContent(
                draft = draft, catalog = vm.categoryCatalog, save = save,
                onEditAccount = { navController.navigate(SETUP_ACCOUNT_FORM_ROUTE) },
                onEditCategories = { navController.navigate(SETUP_CATEGORIES_ROUTE) },
                onEditPlan = { navController.navigate(SETUP_PLAN_ROUTE) },
                onConfirm = vm::confirmAll,
                onChange = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable(SETUP_OTP_ROUTE) {
        val vm: SetupViewModel = hiltViewModel()
        val draft by vm.draft.collectAsStateWithLifecycle()
        val otp by vm.otp.collectAsStateWithLifecycle()
        LaunchedEffect(vm) { vm.beginConfirmation() }
        LaunchedEffect(draft.email) { if (draft.email.isBlank()) navController.navigateToAuthLogin() }
        LaunchedEffect(vm) {
            vm.events.collect { event ->
                if (event is SetupEvent.Verified) {
                    // Let the green "Verificado" cells land before leaving the screen.
                    kotlinx.coroutines.delay(OTP_VERIFIED_HOLD_MILLIS)
                    onAuthenticated(AuthNavigationEvent.NavigateToHome(event.userId))
                }
            }
        }
        SetupOtpContent(
            email = draft.email, state = otp,
            onCode = vm::onCodeChanged, onSubmit = vm::verify, onResend = vm::resend,
            onChangeEmail = { navController.popBackStack() },
        )
    }
}

@androidx.compose.runtime.Composable
private fun NavBackStackEntry.setupViewModel(navController: NavController): SetupViewModel {
    val parent = navController.getBackStackEntry(SETUP_GRAPH_ROUTE)
    return androidx.hilt.navigation.compose.hiltViewModel(parent)
}
