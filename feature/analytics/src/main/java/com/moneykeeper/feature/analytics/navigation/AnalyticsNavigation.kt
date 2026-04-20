package com.moneykeeper.feature.analytics.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.moneykeeper.feature.analytics.ui.analytics.AnalyticsRoute
import com.moneykeeper.feature.analytics.ui.category.CategoryAnalyticsRoute
import com.moneykeeper.feature.analytics.ui.history.HistoryRoute

private const val ROUTE_ANALYTICS          = "analytics"
private const val ROUTE_HISTORY            = "analytics/history"
private const val ROUTE_CATEGORY_ANALYTICS = "analytics/category/{categoryId}"

fun NavGraphBuilder.analyticsGraph(navController: NavController) {
    composable(ROUTE_ANALYTICS) {
        AnalyticsRoute(
            onCategoryClick = { id -> navController.navigate("analytics/category/$id") },
            onSeeAllTransactions = { navController.navigate(ROUTE_HISTORY) },
        )
    }

    composable(ROUTE_HISTORY) {
        HistoryRoute(
            onTransactionClick = { id -> navController.navigate("transactions/$id/edit") },
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = ROUTE_CATEGORY_ANALYTICS,
        arguments = listOf(navArgument("categoryId") { type = NavType.LongType }),
    ) {
        CategoryAnalyticsRoute(
            onTransactionClick = { id -> navController.navigate("transactions/$id/edit") },
            onBack = { navController.popBackStack() },
        )
    }
}
