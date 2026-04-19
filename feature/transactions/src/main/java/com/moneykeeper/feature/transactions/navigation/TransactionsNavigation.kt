package com.moneykeeper.feature.transactions.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.moneykeeper.feature.transactions.ui.add.AddTransactionRoute
import com.moneykeeper.feature.transactions.ui.categories.CategoriesScreen
import com.moneykeeper.feature.transactions.ui.categories.EditCategoryScreen
import com.moneykeeper.feature.transactions.ui.edit.EditTransactionRoute

private const val ROUTE_ADD_TX = "transactions/add?accountId={accountId}"
private const val ROUTE_EDIT_TX = "transactions/{transactionId}/edit"
private const val ROUTE_CATEGORIES = "transactions/categories"
private const val ROUTE_ADD_CATEGORY = "transactions/categories/add"
private const val ROUTE_EDIT_CATEGORY = "transactions/categories/{categoryId}/edit"

fun NavGraphBuilder.transactionsGraph(navController: NavController) {

    composable(
        route = ROUTE_ADD_TX,
        arguments = listOf(
            navArgument("accountId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        AddTransactionRoute(
            onSaved = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
            onNavigateToCategories = { navController.navigate(ROUTE_CATEGORIES) },
        )
    }

    composable(
        route = ROUTE_EDIT_TX,
        arguments = listOf(
            navArgument("transactionId") { type = NavType.LongType },
        ),
    ) {
        EditTransactionRoute(
            onSaved = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
            onNavigateToCategories = { navController.navigate(ROUTE_CATEGORIES) },
        )
    }

    composable(ROUTE_CATEGORIES) {
        CategoriesScreen(
            onAddCategory = { navController.navigate(ROUTE_ADD_CATEGORY) },
            onEditCategory = { id ->
                navController.navigate("transactions/categories/$id/edit")
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable(ROUTE_ADD_CATEGORY) {
        EditCategoryScreen(
            onSaved = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = ROUTE_EDIT_CATEGORY,
        arguments = listOf(
            navArgument("categoryId") { type = NavType.LongType },
        ),
    ) {
        EditCategoryScreen(
            onSaved = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
        )
    }
}
