package com.moneykeeper.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneykeeper.app.R
import com.moneykeeper.feature.accounts.navigation.accountsGraph
import com.moneykeeper.feature.analytics.navigation.analyticsGraph
import com.moneykeeper.feature.dashboard.navigation.dashboardGraph
import com.moneykeeper.feature.forecast.navigation.forecastGraph
import com.moneykeeper.feature.settings.navigation.settingsGraph
import com.moneykeeper.feature.transactions.navigation.transactionsGraph

@Composable
fun MoneyKeeperNavHost(
    navController: NavHostController = rememberNavController(),
) {
    Scaffold(
        bottomBar = { MoneyKeeperBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            dashboardGraph(navController)
            accountsGraph(navController)
            transactionsGraph(navController)
            analyticsGraph(navController)
            settingsGraph(navController)
            forecastGraph(navController)
        }
    }
}

private data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val labelRes: Int,
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Filled.Home,                 R.string.nav_dashboard),
    BottomNavItem(Screen.Accounts,  Icons.Filled.AccountBalanceWallet, R.string.nav_accounts),
    BottomNavItem(Screen.Analytics, Icons.Filled.Analytics,            R.string.nav_analytics),
    BottomNavItem(Screen.Forecast,  Icons.AutoMirrored.Filled.TrendingUp, R.string.nav_forecast),
)

@Composable
private fun MoneyKeeperBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == item.screen.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        // popUpTo без saveState/restoreState: при flat-графе (без nested
                        // графов на таб) saveState/restoreState сохраняет в «accounts»-стек
                        // всё, включая sub-экраны других табов, и восстанавливает не тот экран.
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}

