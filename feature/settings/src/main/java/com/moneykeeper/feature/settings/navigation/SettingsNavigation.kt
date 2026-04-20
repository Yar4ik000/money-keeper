package com.moneykeeper.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.moneykeeper.feature.settings.ui.SettingsScreen

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CATEGORIES = "transactions/categories"

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable(ROUTE_SETTINGS) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onCategories = { navController.navigate(ROUTE_CATEGORIES) },
        )
    }
}
