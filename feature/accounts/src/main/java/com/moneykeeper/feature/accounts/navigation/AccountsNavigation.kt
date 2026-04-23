package com.moneykeeper.feature.accounts.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.moneykeeper.core.ui.navigation.DeepLinks
import com.moneykeeper.feature.accounts.ui.detail.AccountDetailScreen
import com.moneykeeper.feature.accounts.ui.edit.EditAccountScreen
import com.moneykeeper.feature.accounts.ui.list.AccountsScreen
import com.moneykeeper.feature.accounts.ui.transfer.TransferScreen

private const val ROUTE_ACCOUNTS       = "accounts"
private const val ROUTE_ACCOUNT_DETAIL = "accounts/{accountId}"
private const val ROUTE_EDIT_ACCOUNT   = "accounts/{accountId}/edit?returnTo={returnTo}"
private const val ROUTE_TRANSFER       = "accounts/transfer"

fun NavGraphBuilder.accountsGraph(navController: NavController) {
    composable(ROUTE_ACCOUNTS) {
        AccountsScreen(
            onAccountClick  = { id -> navController.navigate("accounts/$id") },
            onAddAccount    = { navController.navigate("accounts/-1/edit") },
            onEditAccount   = { id -> navController.navigate("accounts/$id/edit") },
        )
    }

    composable(
        route = ROUTE_ACCOUNT_DETAIL,
        arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
        deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.ACCOUNT_DETAIL_PATTERN }),
    ) { back ->
        val id = back.arguments!!.getLong("accountId")
        AccountDetailScreen(
            accountId      = id,
            onEditClick    = { navController.navigate("accounts/$id/edit") },
            onTransferClick = { navController.navigate(ROUTE_TRANSFER) },
            onBack         = { navController.popBackStack() },
        )
    }

    composable(
        route = ROUTE_EDIT_ACCOUNT,
        arguments = listOf(
            navArgument("accountId") { type = NavType.LongType },
            navArgument("returnTo") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { back ->
        val id = back.arguments!!.getLong("accountId")
        val returnTo = back.arguments?.getString("returnTo")
        EditAccountScreen(
            accountId = if (id == -1L) null else id,
            onSaved = { savedId ->
                if (returnTo == "transaction" && savedId != null) {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("newAccountId", savedId)
                }
                navController.popBackStack()
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable(ROUTE_TRANSFER) {
        TransferScreen(
            onSaved = { navController.popBackStack() },
            onBack  = { navController.popBackStack() },
        )
    }
}
