package com.kipu.app.navigation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.kipu.app.feature.budgets.presentation.BudgetListScreen
import com.kipu.app.feature.budgets.presentation.BudgetListViewModel
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.presentation.DebtListScreen
import com.kipu.app.feature.debts.presentation.DebtListViewModel
import com.kipu.app.feature.goals.presentation.GoalListScreen
import com.kipu.app.feature.goals.presentation.GoalListViewModel
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuEasingTokens
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/**
 * Bottom-bar identifier of Planning. It is a page of the dashboard destination ([RootTab.PLANNING]),
 * not a NavHost route, and hosts Goals, Budgets and Debts as tabs: switching never navigates.
 */
const val PLANNING_ROUTE = "planning"

/** Selected [PlanningTab] ordinal, kept in the dashboard entry so it survives rotation and process death. */
const val PLANNING_TAB_KEY = "planning_tab"

/** Set by the root (+) button; the Planning host turns it into the selected tab's create action. */
const val PLANNING_CREATE_REQUEST_KEY = "planning_create_request"

enum class PlanningTab(val label: String, val createDescription: String) {
    GOALS("Metas", "Crear meta"),
    BUDGETS("Presupuestos", "Crear presupuesto"),
    DEBTS("Deudas", "Agregar deuda o préstamo");

    companion object {
        fun fromIndex(index: Int?): PlanningTab = entries.getOrElse(index ?: 0) { GOALS }
    }
}

/** Opens Planning as a root tab on [tab] without stacking a new screen per tab. */
fun NavController.navigateToPlanning(tab: PlanningTab = PlanningTab.GOALS) {
    selectRootTab(RootTab.PLANNING)
    dashboardEntryOrNull()?.savedStateHandle?.set(PLANNING_TAB_KEY, tab.ordinal)
}

@Composable
internal fun PlanningRootContent(navController: NavController, backStackEntry: NavBackStackEntry, active: Boolean = true) {
    val handle = backStackEntry.savedStateHandle
    val selectedIndex by handle.getStateFlow(PLANNING_TAB_KEY, PlanningTab.GOALS.ordinal).collectAsStateWithLifecycle()
    val createRequested by handle.getStateFlow(PLANNING_CREATE_REQUEST_KEY, false).collectAsStateWithLifecycle()
    val selectedTab = PlanningTab.fromIndex(selectedIndex)
    val selectedTabIndex: State<Int> = remember { derivedStateOf { PlanningTab.fromIndex(selectedIndex).ordinal } }
    val consumeCreate = { handle[PLANNING_CREATE_REQUEST_KEY] = false }

    val goalViewModel: GoalListViewModel = hiltViewModel(backStackEntry)
    val goalState by goalViewModel.state.collectAsStateWithLifecycle()
    val budgetViewModel: BudgetListViewModel = hiltViewModel(backStackEntry)
    val budgetState by budgetViewModel.state.collectAsStateWithLifecycle()
    val debtViewModel: DebtListViewModel = hiltViewModel(backStackEntry)
    val debtState by debtViewModel.state.collectAsStateWithLifecycle()
    // Planning stays composed behind the other bottom-bar pages: refresh when it is shown or resumed.
    LifecycleResumeEffect(active) {
        if (active) {
            goalViewModel.clearFeedback()
            goalViewModel.refreshPlanAccess()
            budgetViewModel.onResume()
            debtViewModel.refreshPlanAccess()
        }
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().testTag("planning_screen")) {
        PlanningHeader(
            onNotifications = navController::navigateToNotifications,
            onSettings = { navController.navigate(PROFILE_SETTINGS_ROUTE) },
        )
        PlanningSegmentedTabs(
            selected = selectedTab,
            onSelect = { tab -> handle[PLANNING_TAB_KEY] = tab.ordinal },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        RetainedRootTabs(selectedTabIndex = selectedTabIndex, pageCount = PlanningTab.entries.size) {
            RetainedRootPage(active = selectedTab == PlanningTab.GOALS) {
                GoalListScreen(
                    state = goalState,
                    onNavigateBack = { navController.popBackStack() },
                    onCreateGoal = { navController.navigate(GOAL_CREATE_ROUTE) },
                    onOpenGoal = navController::navigateToGoal,
                    onEditGoal = { goalId -> navController.navigate("goals/$goalId/edit") },
                    onDeleteGoal = goalViewModel::deleteEmptyGoal,
                    onNavigateToPlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
                    onContributeGoal = { goalId -> navController.navigate("goals/$goalId/contribute") },
                    onWithdrawGoal = { goalId -> navController.navigate("goals/$goalId?withdraw=true") },
                    onArchiveGoal = { goalId -> navController.navigate("goals/$goalId?action=archive") },
                    onGoalHistory = { goalId -> navController.navigate("goals/$goalId?action=history") },
                    createRequested = createRequested && selectedTab == PlanningTab.GOALS,
                    onCreateRequestConsumed = consumeCreate,
                    embedded = true,
                )
            }
            RetainedRootPage(active = selectedTab == PlanningTab.BUDGETS) {
                BudgetListScreen(
                    state = budgetState,
                    onNavigateBack = { navController.popBackStack() },
                    onCreateBudget = { navController.navigate(BUDGET_CREATE_ROUTE) },
                    onOpenBudget = navController::navigateToBudget,
                    onEditBudget = { navController.navigate("budgets/$it/edit") },
                    onFinishBudget = { navController.navigate("budgets/$it?action=finish") },
                    onDeleteBudget = { navController.navigate("budgets/$it?action=delete") },
                    onRetry = budgetViewModel::reload,
                    onSeePlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
                    embedded = true,
                    createRequested = createRequested && selectedTab == PlanningTab.BUDGETS,
                    onCreateRequestConsumed = consumeCreate,
                )
            }
            RetainedRootPage(active = selectedTab == PlanningTab.DEBTS) {
                DebtListScreen(
                    debts = debtState.debts,
                    isLoading = debtState.isLoading,
                    onDebtAction = { id, action ->
                        when (action) {
                            "settle" -> navController.navigateToDebtSettlement(id)
                            "edit" -> navController.navigate("debts/detail/$id/edit")
                            "close" -> navController.navigateToDebtClosure(id)
                            else -> navController.navigate("debts/detail/$id?action=$action")
                        }
                    },
                    selectedType = debtState.selectedType,
                    onTypeSelected = debtViewModel::selectType,
                    onDebtSelected = navController::navigateToDebtDetail,
                    onAddPayable = { navController.navigateToDebtOpening(DebtObligationType.PAYABLE) },
                    onAddReceivable = { navController.navigateToDebtOpening(DebtObligationType.RECEIVABLE) },
                    scheduledInstallments = debtState.scheduledInstallments,
                    allInstallments = debtState.allInstallments,
                    errorMessage = debtState.errorMessage,
                    openRegisterDebt = createRequested && selectedTab == PlanningTab.DEBTS,
                    onConsumeRegisterDebt = consumeCreate,
                    embedded = true,
                    freeLimitReached = debtState.freeLimitReached,
                    activeDebtCount = debtState.activeDebtCount,
                    onSeePlans = { navController.navigate(PLAN_PURCHASE_ROUTE) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanningHeader(onNotifications: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "Planificación",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        },
        actions = {
            IconButton(onClick = onNotifications) {
                Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Configuración")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/**
 * Three-option version of the Planning segmented selector (track + raised selected pill).
 * The pill slides between options; with reduced motion it jumps without animating.
 */
@Composable
fun PlanningSegmentedTabs(
    selected: PlanningTab,
    onSelect: (PlanningTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val tabs = PlanningTab.entries
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.pillTrack)
            .padding(4.dp)
            .testTag("planning_selector"),
    ) {
        val tabWidth = maxWidth / tabs.size
        val showIcons = !largeText && tabWidth >= 118.dp
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selected.ordinal,
            animationSpec = tween(
                durationMillis = if (reducedMotion) 0 else KipuMotionTokens.RootTabSwitchMillis,
                easing = KipuEasingTokens.Standard,
            ),
            label = "planning_tab_indicator",
        )
        Surface(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .height(if (largeText) 64.dp else 48.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
        ) {}
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val contentColor = if (isSelected) colors.primaryDeep else colors.secondaryMuted
                Row(
                    modifier = Modifier
                        .width(tabWidth)
                        .heightIn(min = if (largeText) 64.dp else 48.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(tab) }
                        .semantics {
                            role = Role.Tab
                            this.selected = isSelected
                            contentDescription = tab.label
                        }
                        .padding(horizontal = 4.dp)
                        .testTag("planning_tab_${tab.name.lowercase()}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showIcons) {
                        Icon(tab.icon(), contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    // Labels are single words: shrink to fit rather than breaking "Presupuestos" mid-word.
                    BasicText(
                        tab.label,
                        color = { contentColor },
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}

private fun PlanningTab.icon(): ImageVector = when (this) {
    PlanningTab.GOALS -> Icons.Default.Flag
    PlanningTab.BUDGETS -> Icons.Default.PieChart
    PlanningTab.DEBTS -> Icons.Default.AccountBalance
}
