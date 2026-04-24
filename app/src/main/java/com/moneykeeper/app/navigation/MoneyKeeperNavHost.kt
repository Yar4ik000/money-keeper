package com.moneykeeper.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.ui.components.TipCard
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneykeeper.app.R
import com.moneykeeper.feature.accounts.navigation.accountsGraph
import com.moneykeeper.feature.analytics.navigation.analyticsGraph
import com.moneykeeper.feature.auth.ui.change.ChangePinScreen
import com.moneykeeper.feature.dashboard.navigation.dashboardGraph
import com.moneykeeper.feature.forecast.navigation.forecastGraph
import com.moneykeeper.feature.settings.navigation.budgetsGraph
import com.moneykeeper.feature.settings.navigation.settingsGraph
import com.moneykeeper.feature.transactions.navigation.transactionsGraph

@Composable
fun MoneyKeeperNavHost(
    navController: NavHostController = rememberNavController(),
    tipViewModel: TipViewModel = hiltViewModel(),
) {
    val tipSettings by tipViewModel.settings.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = { MoneyKeeperBottomBar(navController) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
            ) {
                dashboardGraph(navController)
                accountsGraph(navController)
                transactionsGraph(navController)
                analyticsGraph(navController)
                settingsGraph(navController)
                forecastGraph(navController)
                budgetsGraph(navController)
                composable(Screen.ChangePin.route) {
                    ChangePinScreen(
                        onBack = { navController.popBackStack() },
                        onChanged = { navController.popBackStack() },
                    )
                }
            }

            val tipText: String? = when (currentRoute) {
                Screen.Dashboard.route ->
                    if (!tipSettings.seenTipDashboard)
                        "Нажмите «+» чтобы добавить операцию. Карусель сверху — ваши счета."
                    else null
                Screen.Accounts.route ->
                    if (!tipSettings.seenTipAccounts)
                        "Нажмите «+» чтобы добавить счёт. Нажмите на счёт, чтобы посмотреть детали."
                    else null
                Screen.Analytics.route ->
                    if (!tipSettings.seenTipAnalytics)
                        "Здесь видна аналитика расходов по категориям и динамика по месяцам."
                    else null
                Screen.Forecast.route ->
                    if (!tipSettings.seenTipForecast)
                        "Прогноз будущих операций на основе регулярных транзакций и вкладов."
                    else null
                Screen.Budgets.route ->
                    if (!tipSettings.seenTipBudgets)
                        "Создайте бюджет, чтобы контролировать расходы по категориям."
                    else null
                else -> null
            }
            if (tipText != null) {
                val onDismiss: () -> Unit = when (currentRoute) {
                    Screen.Dashboard.route  -> tipViewModel::markDashboardTipSeen
                    Screen.Accounts.route   -> tipViewModel::markAccountsTipSeen
                    Screen.Analytics.route  -> tipViewModel::markAnalyticsTipSeen
                    Screen.Forecast.route   -> tipViewModel::markForecastTipSeen
                    Screen.Budgets.route    -> tipViewModel::markBudgetsTipSeen
                    else                    -> ({ })
                }
                TipCard(
                    text = tipText,
                    onDismiss = onDismiss,
                    onDismissAll = { tipViewModel.markAllTipsSeen() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val labelRes: Int,
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Filled.Home,                    R.string.nav_dashboard),
    BottomNavItem(Screen.Accounts,  Icons.Filled.AccountBalanceWallet,    R.string.nav_accounts),
    BottomNavItem(Screen.Budgets,   Icons.Filled.Savings,                 R.string.nav_budgets),
    BottomNavItem(Screen.Analytics, Icons.Filled.Analytics,               R.string.nav_analytics),
    BottomNavItem(Screen.Forecast,  Icons.AutoMirrored.Filled.TrendingUp, R.string.nav_forecast),
)

@Composable
private fun MoneyKeeperBottomBar(
    navController: NavHostController,
    badgeViewModel: BudgetsBadgeViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val budgetsBadge by badgeViewModel.badge.collectAsStateWithLifecycle()
    var lastNavMs by remember { mutableLongStateOf(0L) }
    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == item.screen.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    val now = android.os.SystemClock.uptimeMillis()
                    if (selected || now - lastNavMs < 300L) return@NavigationBarItem
                    lastNavMs = now
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                            inclusive = false
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    if (item.screen == Screen.Budgets && budgetsBadge != BudgetsBadge.NONE) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = when (budgetsBadge) {
                                        BudgetsBadge.CRITICAL -> Color(0xFFD32F2F)
                                        BudgetsBadge.WARNING  -> Color(0xFFF9A825)
                                        else                  -> Color.Transparent
                                    },
                                )
                            },
                        ) {
                            Icon(item.icon, contentDescription = null)
                        }
                    } else {
                        Icon(item.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}
