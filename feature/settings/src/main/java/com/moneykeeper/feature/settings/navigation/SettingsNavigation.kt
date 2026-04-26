package com.moneykeeper.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.moneykeeper.feature.settings.ui.SettingsScreen
import com.moneykeeper.feature.settings.ui.backup.BackupScreen
import com.moneykeeper.feature.settings.ui.budgets.BudgetsScreen
import com.moneykeeper.feature.settings.ui.recurring.RecurringRuleDetailScreen
import com.moneykeeper.feature.settings.ui.recurring.RecurringRulesScreen

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CATEGORIES = "transactions/categories"
private const val ROUTE_BACKUP = "settings/backup"
private const val ROUTE_CHANGE_PIN = "settings/change_pin"
private const val ROUTE_RECURRING_RULES = "settings/recurring_rules"
private const val ROUTE_RECURRING_RULE_DETAIL = "settings/recurring_rules/{ruleId}"
const val ROUTE_BUDGETS = "budgets"

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable(ROUTE_SETTINGS) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onCategories = { navController.navigate(ROUTE_CATEGORIES) },
            onRecurringRules = { navController.navigate(ROUTE_RECURRING_RULES) },
            onBackup = { navController.navigate(ROUTE_BACKUP) },
            onChangePin = { navController.navigate(ROUTE_CHANGE_PIN) },
        )
    }

    composable(ROUTE_BACKUP) {
        BackupScreen(onBack = { navController.popBackStack() })
    }

    composable(ROUTE_RECURRING_RULES) {
        RecurringRulesScreen(
            onBack = { navController.popBackStack() },
            onRuleClick = { ruleId -> navController.navigate("settings/recurring_rules/$ruleId") },
        )
    }

    composable(
        route = ROUTE_RECURRING_RULE_DETAIL,
        arguments = listOf(navArgument("ruleId") { type = NavType.LongType }),
    ) {
        RecurringRuleDetailScreen(onBack = { navController.popBackStack() })
    }
}

fun NavGraphBuilder.budgetsGraph(navController: NavController) {
    composable(ROUTE_BUDGETS) {
        BudgetsScreen()
    }
}
