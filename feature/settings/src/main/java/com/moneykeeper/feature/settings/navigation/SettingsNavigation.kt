package com.moneykeeper.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.moneykeeper.feature.settings.ui.SettingsScreen
import com.moneykeeper.feature.settings.ui.backup.BackupScreen
import com.moneykeeper.feature.settings.ui.budgets.BudgetsScreen

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CATEGORIES = "transactions/categories"
private const val ROUTE_BACKUP = "settings/backup"
private const val ROUTE_CHANGE_PIN = "settings/change_pin"
const val ROUTE_BUDGETS = "budgets"

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable(ROUTE_SETTINGS) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onCategories = { navController.navigate(ROUTE_CATEGORIES) },
            onBackup = { navController.navigate(ROUTE_BACKUP) },
            onChangePin = { navController.navigate(ROUTE_CHANGE_PIN) },
        )
    }

    composable(ROUTE_BACKUP) {
        BackupScreen(onBack = { navController.popBackStack() })
    }
}

fun NavGraphBuilder.budgetsGraph(navController: NavController) {
    composable(ROUTE_BUDGETS) {
        BudgetsScreen(onBack = { navController.popBackStack() })
    }
}
