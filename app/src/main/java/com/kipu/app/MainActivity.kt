package com.kipu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kipu.app.feature.auth.presentation.AuthCallbackViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kipu.app.feature.plans.data.sync.PlanEntitlementRefreshCoordinator
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.kipu.app.core.security.LocalAuthenticatorGateway
import com.kipu.app.core.security.LocalLockCoordinator
import com.kipu.app.core.security.LockScreenOverlay
import com.kipu.app.core.security.model.LocalLockState
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.budgets.data.sync.BudgetMaintenanceScheduler
import com.kipu.app.feature.budgets.data.sync.BudgetSyncScheduler
import com.kipu.app.feature.movements.data.sync.MovementSyncScheduler
import com.kipu.app.feature.auth.domain.AuthRepository
import com.kipu.app.feature.auth.presentation.LocalSessionEntryCurtain
import com.kipu.app.feature.auth.presentation.SessionEntryCurtain
import com.kipu.app.feature.auth.presentation.SessionEntryCurtainHost
import com.kipu.app.feature.auth.domain.model.AuthResult
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
import com.kipu.app.navigation.ACCOUNT_FORM_ROUTE
import com.kipu.app.navigation.ACCOUNTS_DASHBOARD_ROUTE
import com.kipu.app.navigation.AUTH_LOGIN_ROUTE
import com.kipu.app.navigation.AUTH_START_ROUTE
import com.kipu.app.navigation.AUTH_INTRO_ROUTE
import com.kipu.app.feature.auth.data.OnboardingPreferences
import com.kipu.app.navigation.AUTH_RESET_PASSWORD_ROUTE
import com.kipu.app.navigation.AuthDeepLinkHandler
import com.kipu.app.navigation.BIOMETRIC_ROUTE
import com.kipu.app.navigation.CARD_FORM_ROUTE
import com.kipu.app.navigation.DeepLinkResult
import com.kipu.app.navigation.BUDGET_CREATE_ROUTE
import com.kipu.app.navigation.ROOT_TAB_KEY
import com.kipu.app.navigation.RootTab
import com.kipu.app.navigation.dashboardEntryOrNull
import com.kipu.app.navigation.selectRootTab
import com.kipu.app.navigation.appDestinations
import com.kipu.app.feature.debts.data.sync.DebtSyncScheduler
import com.kipu.app.feature.goals.data.sync.GoalsSyncScheduler
import com.kipu.app.navigation.ANALYSIS_ROUTE
import com.kipu.app.navigation.PLANNING_CREATE_REQUEST_KEY
import com.kipu.app.navigation.PLANNING_LIMIT_REACHED_KEY
import com.kipu.app.navigation.PLANNING_ROUTE
import com.kipu.app.navigation.PLANNING_TAB_KEY
import com.kipu.app.navigation.PlanningTab
import com.kipu.app.navigation.KipuNavigationBar
import com.kipu.app.navigation.RootTabMotion
import com.kipu.app.navigation.MOVEMENTS_HISTORY_PATTERN
import com.kipu.app.navigation.MOVEMENTS_HISTORY_ROUTE
import com.kipu.app.navigation.NOTIFICATION_CENTER_ROUTE
import com.kipu.app.navigation.PLAN_PURCHASE_ROUTE
import com.kipu.app.navigation.PLAN_SELECTION_ROUTE
import com.kipu.app.navigation.PROFILE_SETTINGS_ROUTE
import com.kipu.app.navigation.ReceiptShareIntent
import com.kipu.app.navigation.navigateToReceiptImport
import com.kipu.app.navigation.GOAL_CREATE_ROUTE
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuEasingTokens
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.LocalStatusBarIconsOverride
import com.kipu.app.ui.theme.StatusBarIconsOverride
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var planEntitlementRefresh: PlanEntitlementRefreshCoordinator

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var sessionCoordinator: SessionCoordinator

    @Inject
    lateinit var budgetMaintenanceScheduler: BudgetMaintenanceScheduler

    @Inject
    lateinit var budgetSyncScheduler: BudgetSyncScheduler

    @Inject
    lateinit var movementSyncScheduler: MovementSyncScheduler

    @Inject
    lateinit var debtSyncScheduler: DebtSyncScheduler

    @Inject
    lateinit var goalsSyncScheduler: GoalsSyncScheduler

    @Inject
    lateinit var localLockCoordinator: LocalLockCoordinator

    @Inject
    lateinit var localAuthenticatorGateway: LocalAuthenticatorGateway

    @Inject
    lateinit var authDeepLinkHandler: AuthDeepLinkHandler

    @Inject
    lateinit var profileDao: ProfilePreferencesDao

    @Inject
    lateinit var planPreferencesDao: PlanPreferencesDao

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var sessionEntryCurtain: SessionEntryCurtain

    private var pendingDeepLink by mutableStateOf<DeepLinkResult?>(null)
    private var pendingReminderRoute by mutableStateOf<String?>(null)
    private var pendingReceiptUri by mutableStateOf<String?>(null)
    private var restorationJob: Job? = null
    private val authCallbackViewModel: AuthCallbackViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { planEntitlementRefresh.onForeground() }
        (sessionCoordinator.localAccess.value as? LocalAccess.Available)?.let { access ->
            budgetMaintenanceScheduler.scheduleNow(access.userId)
            budgetSyncScheduler.scheduleSync(access.userId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(localLockCoordinator)
        enableEdgeToEdge()

        pendingDeepLink = intent?.data?.let { authDeepLinkHandler.handleDeepLink(it) }
        pendingReminderRoute = reminderRoute(intent)
        pendingReceiptUri = if (savedInstanceState != null) {
            savedInstanceState.getString("pending_receipt_uri")
        } else sharedReceiptUri(intent)

        setContent {
            val scope = rememberCoroutineScope()
            val navController = rememberNavController()

            val accessState by sessionCoordinator.localAccess.collectAsStateWithLifecycle()
            val lockState by localLockCoordinator.lockState.collectAsStateWithLifecycle()

            LaunchedEffect(accessState) {
                (accessState as? LocalAccess.Available)?.let { access ->
                    // Pull remote changes when the session opens; otherwise a fresh
                    // install only hydrates after the first local write.
                    // Interactive sign-in hydrates and awaits this work under the welcome curtain.
                    if (sessionEntryCurtain.state.value == null) movementSyncScheduler.scheduleSync(access.userId)
                    debtSyncScheduler.schedule(access.userId)
                    goalsSyncScheduler.schedule(access.userId)
                    budgetSyncScheduler.scheduleSync(access.userId)
                    budgetMaintenanceScheduler.scheduleNow(access.userId)
                }
            }

            val currentUserId = when (val access = accessState) {
                is LocalAccess.Available -> access.userId
                is LocalAccess.Protected -> access.userId
                is LocalAccess.NoOwner -> null
            }?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            val profileState = remember(currentUserId) {
                if (currentUserId != null) {
                    profileDao.observeProfile(currentUserId)
                } else {
                    flowOf(null)
                }
            }.collectAsState(initial = null)

            val isDark = when (profileState.value?.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            val isMasked = profileState.value?.hideBalances ?: false

            var lastKnownUserId by remember { mutableStateOf(currentUserId) }
            LaunchedEffect(currentUserId) {
                if (lastKnownUserId != null && lastKnownUserId != currentUserId) {
                    navController.clearBackStack(ACCOUNTS_DASHBOARD_ROUTE)
                    navController.clearBackStack(MOVEMENTS_HISTORY_PATTERN)
                    navController.clearBackStack(MOVEMENTS_HISTORY_ROUTE)
                }
                lastKnownUserId = currentUserId

            }

            LaunchedEffect(Unit) {
                restorationJob = currentCoroutineContext()[Job]
                val hasActionableDeepLink = pendingDeepLink is DeepLinkResult.ResetPassword ||
                    pendingDeepLink is DeepLinkResult.ConfirmEmail ||
                    authCallbackViewModel.state.value.handledCallback
                // Recovery installs its own temporary session; never race it with restoration.
                if (hasActionableDeepLink) return@LaunchedEffect
                val restored = authRepository.restoreSession().getOrNull() as? AuthResult.Success
                if (!hasActionableDeepLink) {
                    val destination = if (restored != null) {
                        pendingReminderRoute
                            ?: postAuthDestination(restored.userId) ?: AUTH_LOGIN_ROUTE
                    } else if (onboardingPreferences.checkpoint.first().completed) {
                        AUTH_LOGIN_ROUTE
                    } else AUTH_INTRO_ROUTE
                    navController.currentBackStackEntryFlow
                        .filter { it.destination.route == AUTH_START_ROUTE }.first()
                    navController.navigate(destination) {
                        popUpTo(AUTH_START_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            LaunchedEffect(currentUserId, pendingReminderRoute) {
                val route = pendingReminderRoute
                if (currentUserId != null && route != null && sessionEntryCurtain.state.value == null) {
                    navController.openReminderRoute(route)
                    pendingReminderRoute = null
                }
            }

            LaunchedEffect(pendingDeepLink) {
                authCallbackViewModel.handle(pendingDeepLink, restorationJob)
            }
            val authCallbackState by authCallbackViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(authCallbackState.targetRoute) {
                authCallbackState.targetRoute?.let { destination ->
                    navController.navigate(destination) { popUpTo(0) { inclusive = true } }
                    intent?.data = null
                    pendingDeepLink = null
                    authCallbackViewModel.consumeNavigation()
                }
            }

            KipuTheme(darkTheme = isDark) {
                val background = MaterialTheme.colorScheme.background
                // Full-bleed teal screens (sign-in header, welcome screens) ask for light status bar icons.
                val statusBarOverride = remember { StatusBarIconsOverride() }
                val isLightBackground = background.luminance() > 0.5f
                val lightStatusBars = statusBarOverride.lightStatusBars ?: isLightBackground
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = lightStatusBars
                    insetsController.isAppearanceLightNavigationBars = isLightBackground
                }

                CompositionLocalProvider(
                    LocalBalanceMasked provides isMasked,
                    LocalSessionEntryCurtain provides sessionEntryCurtain,
                    LocalStatusBarIconsOverride provides statusBarOverride,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route
                            // Every bottom-bar page lives in the dashboard entry, which keeps the selected page
                            // and Planning's tab; the bar and the (+) button adapt to them.
                            val dashboardHandle = remember(navBackStackEntry) {
                                navBackStackEntry?.takeIf { it.destination.route == ACCOUNTS_DASHBOARD_ROUTE }?.savedStateHandle
                            }
                            val rootTab = dashboardHandle?.getStateFlow(ROOT_TAB_KEY, RootTab.MONEY)
                                ?.collectAsStateWithLifecycle()?.value ?: RootTab.MONEY
                            val planningTab = PlanningTab.fromIndex(
                                dashboardHandle?.getStateFlow(PLANNING_TAB_KEY, PlanningTab.GOALS.ordinal)
                                    ?.collectAsStateWithLifecycle()?.value,
                            )
                            val planningLimitReached = dashboardHandle?.getStateFlow(PLANNING_LIMIT_REACHED_KEY, false)
                                ?.collectAsStateWithLifecycle()?.value ?: false
                            val rootTabRoute = if (currentRoute == ACCOUNTS_DASHBOARD_ROUTE) {
                                when (rootTab) {
                                    RootTab.MOVEMENTS -> MOVEMENTS_HISTORY_ROUTE
                                    RootTab.PLANNING -> PLANNING_ROUTE
                                    RootTab.ANALYSIS -> ANALYSIS_ROUTE
                                    else -> ACCOUNTS_DASHBOARD_ROUTE
                                }
                            } else {
                                currentRoute
                            }

                            val isRootDestination = currentRoute == ACCOUNTS_DASHBOARD_ROUTE ||
                                currentRoute == MOVEMENTS_HISTORY_PATTERN ||
                                currentRoute == MOVEMENTS_HISTORY_ROUTE ||
                                currentRoute?.startsWith("movements/history") == true

                            // Wait for auth/onboarding and the local lock before presenting private data.
                            LaunchedEffect(pendingReceiptUri, currentRoute, accessState, lockState) {
                                val uri = pendingReceiptUri
                                val canOpenReceipt = isRootDestination || currentRoute?.startsWith("receipts") == true
                                if (uri != null && canOpenReceipt && accessState is LocalAccess.Available &&
                                    lockState != LocalLockState.LOCKED
                                ) {
                                    navController.navigateToReceiptImport(uri)
                                    pendingReceiptUri = null
                                }
                            }

                            val isUserAuthenticated = accessState is LocalAccess.Available || accessState is LocalAccess.Protected
                            val shouldShowBottomBar = isRootDestination && isUserAuthenticated

                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                bottomBar = {
                                    if (shouldShowBottomBar) {
                                        KipuNavigationBar(
                                            currentRoute = rootTabRoute,
                                            registerLocked = rootTabRoute == PLANNING_ROUTE && planningLimitReached,
                                            onNavigateToDinero = { navController.selectRootTab(RootTab.MONEY) },
                                            onNavigateToMovimientos = { navController.selectRootTab(RootTab.MOVEMENTS) },
                                            onNavigateToPlanificacion = { navController.selectRootTab(RootTab.PLANNING) },
                                            onNavigateToAnalisis = { navController.selectRootTab(RootTab.ANALYSIS) },
                                            registerContentDescription = if (rootTabRoute == PLANNING_ROUTE) {
                                                planningTab.createDescription + if (planningLimitReached) ". Límite del Plan Free alcanzado" else ""
                                            } else {
                                                null
                                            },
                                            onRegisterClick = {
                                                if (accessState is LocalAccess.Available) {
                                                    if (rootTabRoute == PLANNING_ROUTE) {
                                                        navController.currentBackStackEntry?.savedStateHandle
                                                            ?.set(PLANNING_CREATE_REQUEST_KEY, true)
                                                    } else if (rootTabRoute == ANALYSIS_ROUTE) {
                                                        // Análisis has no create flow yet: register a movement from Dinero.
                                                        navController.selectRootTab(RootTab.MONEY)
                                                        navController.currentBackStackEntry?.savedStateHandle
                                                            ?.set("open_register_movement", true)
                                                    } else {
                                                        navController.currentBackStackEntry?.savedStateHandle
                                                            ?.set("open_register_movement", true)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                },
                            ) { innerPadding ->
                                val reducedMotion = rememberReducedMotionEnabled()
                                val navigationOffset = with(LocalDensity.current) { 24.dp.roundToPx() }
                                // Keep this as a getter: root-tab click handlers update selection
                                // immediately before navigating back to the retained dashboard.
                                // A captured Boolean would still describe the outgoing debt tab
                                // while NavHost chooses the incoming transition.
                                val selectedDashboardTabForMotion = {
                                    navController.dashboardEntryOrNull()?.savedStateHandle?.get<Int>(ROOT_TAB_KEY) ?: RootTab.MONEY
                                }
                                // Root tabs keep their short lateral shift. Every other push/pop slides the whole screen:
                                // forward enters from the right over the previous one, back (arrow, button
                                // or gesture) pushes it out to the right revealing the previous screen.
                                val rootTabSpec = tween<IntOffset>(KipuMotionTokens.RootTabSwitchMillis, easing = KipuEasingTokens.Standard)
                                val stackSpec = tween<IntOffset>(KipuMotionTokens.NavStackMillis, easing = KipuEasingTokens.Standard)
                                val stackFadeSpec = tween<Float>(KipuMotionTokens.NavStackMillis, easing = KipuEasingTokens.Standard)
                                val rootFadeSpec = tween<Float>(KipuMotionTokens.RootTabSwitchMillis, easing = KipuEasingTokens.Standard)
                                fun AnimatedContentTransitionScope<NavBackStackEntry>.isRootSwitch() = RootTabMotion.isRootTransition(
                                    initialState.destination.route,
                                    targetState.destination.route,
                                    selectedDashboardTabForMotion(),
                                )
                                fun AnimatedContentTransitionScope<NavBackStackEntry>.rootForward() = RootTabMotion.isForward(
                                    initialState.destination.route,
                                    targetState.destination.route,
                                    selectedDashboardTabForMotion(),
                                )
                                fun AnimatedContentTransitionScope<NavBackStackEntry>.rootEnter(): EnterTransition {
                                    val offset = if (rootForward()) navigationOffset else -navigationOffset
                                    return slideInHorizontally(rootTabSpec) { offset } + fadeIn(rootFadeSpec)
                                }
                                fun AnimatedContentTransitionScope<NavBackStackEntry>.rootExit(): ExitTransition {
                                    val offset = if (rootForward()) -navigationOffset else navigationOffset
                                    return slideOutHorizontally(rootTabSpec) { offset } + fadeOut(rootFadeSpec)
                                }
                                val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                    when {
                                        reducedMotion -> EnterTransition.None
                                        isRootSwitch() -> rootEnter()
                                        isTopLevelRoute(targetState.destination.route) -> fadeIn(
                                            animationSpec = tween(KipuMotionTokens.TopLevelMillis, easing = KipuEasingTokens.Decelerate),
                                        )
                                        else -> slideInHorizontally(stackSpec) { it }
                                    }
                                }
                                val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                    when {
                                        reducedMotion -> ExitTransition.None
                                        isRootSwitch() -> rootExit()
                                        isTopLevelRoute(targetState.destination.route) -> fadeOut(
                                            animationSpec = tween(KipuMotionTokens.NavExitMillis, easing = KipuEasingTokens.Accelerate),
                                        )
                                        else -> slideOutHorizontally(stackSpec) { -it / 4 } + fadeOut(stackFadeSpec, targetAlpha = 0.6f)
                                    }
                                }
                                val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                    when {
                                        reducedMotion -> EnterTransition.None
                                        isRootSwitch() -> rootEnter()
                                        else -> slideInHorizontally(stackSpec) { -it / 4 } + fadeIn(stackFadeSpec, initialAlpha = 0.6f)
                                    }
                                }
                                val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                    when {
                                        reducedMotion -> ExitTransition.None
                                        isRootSwitch() -> rootExit()
                                        else -> slideOutHorizontally(stackSpec) { it }
                                    }
                                }
                                NavHost(
                                    navController = navController,
                                    startDestination = AUTH_START_ROUTE,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .consumeWindowInsets(innerPadding),
                                    enterTransition = enter,
                                    exitTransition = exit,
                                    popEnterTransition = popEnter,
                                    popExitTransition = popExit,
                                    // The default predictive pop scales the outgoing screen down; the gesture
                                    // seeks the same slide as the in-app back arrow instead.
                                    predictivePopEnterTransition = { popEnter() },
                                    predictivePopExitTransition = { popExit() },
                                ) {
                                    appDestinations(
                                        navController = navController,
                                        onAuthenticated = { userId ->
                                            scope.launch {
                                                val destination = postAuthDestination(userId) ?: AUTH_LOGIN_ROUTE
                                                navController.openReminderRoute(destination)
                                                pendingReminderRoute = null
                                                sessionEntryCurtain.expectDestination(
                                                    navController.currentDestination?.route ?: destination,
                                                )
                                            }
                                        },
                                        onSignOut = {
                                            navController.clearBackStack(ACCOUNTS_DASHBOARD_ROUTE)
                                            navController.clearBackStack(MOVEMENTS_HISTORY_PATTERN)
                                            navController.clearBackStack(MOVEMENTS_HISTORY_ROUTE)
                                            navController.navigate(AUTH_LOGIN_ROUTE) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                            }

                            // Covers the first load after a sign-in; the lock screen still goes above it.
                            SessionEntryCurtainHost(sessionEntryCurtain, currentRoute, ACCOUNTS_DASHBOARD_ROUTE)

                            if (lockState == LocalLockState.LOCKED) {
                                LockScreenOverlay(
                                    gateway = localAuthenticatorGateway,
                                    onUnlockSuccess = { localLockCoordinator.unlock() },
                                    onSignOutRequested = {
                                        scope.launch {
                                            authRepository.signOut(explicit = true)
                                            navController.clearBackStack(ACCOUNTS_DASHBOARD_ROUTE)
                                            navController.clearBackStack(MOVEMENTS_HISTORY_PATTERN)
                                            navController.clearBackStack(MOVEMENTS_HISTORY_ROUTE)
                                            navController.navigate(AUTH_LOGIN_ROUTE) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.data?.let { authDeepLinkHandler.handleDeepLink(it) }
        pendingReminderRoute = reminderRoute(intent)
        sharedReceiptUri(intent)?.let { pendingReceiptUri = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_receipt_uri", pendingReceiptUri)
        super.onSaveInstanceState(outState)
    }

    private fun sharedReceiptUri(intent: Intent?): String? {
        val uri = ReceiptShareIntent.imageUri(intent)?.toString()
        if (intent?.action == Intent.ACTION_SEND && uri == null) {
            android.widget.Toast.makeText(this,
                "Comparte una sola imagen legible de Yape. También puedes registrar el movimiento manualmente.",
                android.widget.Toast.LENGTH_LONG).show()
        }
        return uri
    }

    private suspend fun postAuthDestination(rawUserId: String): String? {
        val userId = runCatching { UUID.fromString(rawUserId) }.getOrNull() ?: return null
        return com.kipu.app.navigation.resolvePostAuthDestination(pendingReminderRoute) {
            planPreferencesDao.findPreference(userId) != null
        }
    }
}


private fun reminderRoute(intent: Intent?): String? {
    val uri = intent?.data ?: return null
    if (uri.scheme != "kipu") return null
    val segments = uri.pathSegments
    return when (uri.host) {
        "debts" -> {
            if (segments.size != 2 || segments.firstOrNull() != "detail") return null
            runCatching { UUID.fromString(segments[1]).toString() }.getOrNull()?.let { "debts/detail/$it" }
        }
        // Financial alert notifications open the center on top of the dashboard so back returns home.
        "notifications" -> NOTIFICATION_CENTER_ROUTE.takeIf { segments == listOf("center") }
        // HU-43 review notification: kipu://capture/candidate/{id}
        "capture" -> {
            if (segments.size != 2 || segments.firstOrNull() != "candidate") return null
            runCatching { UUID.fromString(segments[1]).toString() }.getOrNull()?.let { "capture/candidate/$it" }
        }
        else -> null
    }
}

private fun NavController.openReminderRoute(route: String) {
    if (route.startsWith("capture/candidate/")) {
        // Dashboard > inbox > candidate, so back walks out through the inbox.
        navigate(ACCOUNTS_DASHBOARD_ROUTE) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
        navigate("capture/inbox") { launchSingleTop = true }
        navigate(route) { launchSingleTop = true }
    } else if (route == NOTIFICATION_CENTER_ROUTE) {
        navigate(ACCOUNTS_DASHBOARD_ROUTE) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
        navigate(route) { launchSingleTop = true }
    } else {
        navigate(route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
}

private fun isTopLevelRoute(route: String?): Boolean {
    if (route == null) return false
    return route == ACCOUNTS_DASHBOARD_ROUTE ||
        route == MOVEMENTS_HISTORY_PATTERN ||
        route == MOVEMENTS_HISTORY_ROUTE ||
        route.startsWith("movements/history")
}
