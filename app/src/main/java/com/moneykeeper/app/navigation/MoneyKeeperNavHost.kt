package com.moneykeeper.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moneykeeper.app.R
import com.moneykeeper.feature.accounts.navigation.accountsGraph
import com.moneykeeper.feature.dashboard.navigation.dashboardGraph
import com.moneykeeper.feature.transactions.navigation.transactionsGraph

/** Маршруты, на которых показывается глобальный FAB «Новая операция».
 *  Dashboard имеет собственный FAB, Analytics и Forecast — пока заглушки. */
private val GLOBAL_FAB_ROUTES = setOf(
    Screen.Analytics.route,
    Screen.Forecast.route,
)

@Composable
fun MoneyKeeperNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showGlobalFab = backStackEntry?.destination?.route in GLOBAL_FAB_ROUTES

    Scaffold(
        bottomBar = { MoneyKeeperBottomBar(navController) },
        floatingActionButton = {
            if (showGlobalFab) {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.AddTransaction.buildRoute()) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.nav_add_transaction),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            dashboardGraph(navController)
            accountsGraph(navController)
            transactionsGraph(navController)
            composable(Screen.Analytics.route)   { StubScreen(R.string.nav_analytics) }
            composable(Screen.Forecast.route)    { StubScreen(R.string.nav_forecast) }
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

@Composable
private fun StubScreen(titleRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.screen_placeholder, stringResource(titleRes)),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
