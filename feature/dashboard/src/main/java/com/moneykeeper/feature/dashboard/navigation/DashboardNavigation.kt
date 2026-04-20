package com.moneykeeper.feature.dashboard.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.moneykeeper.feature.dashboard.ui.DashboardRoute

private const val ROUTE_DASHBOARD = "dashboard"

fun NavGraphBuilder.dashboardGraph(navController: NavController) {
    composable(ROUTE_DASHBOARD) {
        DashboardRoute(
            onAccountClick = { id -> navController.navigate("accounts/$id") },
            onAddAccount = { navController.navigate("accounts/-1/edit") },
            onAddTransaction = { accountId ->
                navController.navigate("transactions/add?accountId=${accountId ?: ""}")
            },
            onSeeAllTransactions = { navController.navigate("analytics/history") },
            onDepositClick = { accountId -> navController.navigate("accounts/$accountId") },
            onSettings = { /* TODO §10 settings screen */ },
            onTransactionClick = { id -> navController.navigate("transactions/$id/edit") },
        )
    }
}
