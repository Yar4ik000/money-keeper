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
private const val ROUTE_ADD_CATEGORY_PICKER = "transactions/categories/add/picker"
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
    ) { backStackEntry ->
        AddTransactionRoute(
            navBackStackEntry = backStackEntry,
            onSaved = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
            onNavigateToCategories = { navController.navigate(ROUTE_ADD_CATEGORY_PICKER) },
            onAddAccountFromPicker = { navController.navigate("accounts/-1/edit?returnTo=transaction") },
        )
    }

    composable(
        route = ROUTE_EDIT_TX,
        arguments = listOf(
            navArgument("transactionId") { type = NavType.LongType },
        ),
    ) { backStackEntry ->
        EditTransactionRoute(
            navBackStackEntry = backStackEntry,
            onSaved = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
            onNavigateToCategories = { navController.navigate(ROUTE_ADD_CATEGORY_PICKER) },
            onAddAccountFromPicker = { navController.navigate("accounts/-1/edit?returnTo=transaction") },
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

    // Picker variant: returns newCategoryId to the previous (transaction) screen
    composable(ROUTE_ADD_CATEGORY_PICKER) {
        EditCategoryScreen(
            onSaved = { id ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("newCategoryId", id)
                navController.popBackStack()
            },
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
