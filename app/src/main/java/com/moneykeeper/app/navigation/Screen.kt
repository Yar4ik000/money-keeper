package com.moneykeeper.app.navigation

/**
 * Маршруты навигации (§1.9). Route-строки параметризованы плейсхолдерами `{arg}` —
 * это паттерн, по которому `NavHost` матчит destination. Для реального перехода
 * используй [buildRoute] (для объектов с аргументами), который подставляет значения.
 *
 * Именование `buildRoute` вместо плановского `route(...)` — сознательное: в Kotlin
 * пересечение имени `val route` (property) и `fun route(...)` (member function) в
 * одном объекте читается неоднозначно, `buildRoute` однозначен на call-site.
 */
sealed class Screen(val route: String) {
    data object Dashboard    : Screen("dashboard")
    data object Accounts     : Screen("accounts")
    data object Transactions : Screen("transactions")
    data object Analytics    : Screen("analytics")
    data object Forecast     : Screen("forecast")

    /** `accountId` опционален: FAB из dashboard запускает форму без привязки к счёту. */
    data object AddTransaction : Screen("transactions/add?accountId={accountId}") {
        fun buildRoute(accountId: Long? = null): String =
            "transactions/add?accountId=${accountId ?: ""}"
    }

    data object AccountDetail : Screen("accounts/{accountId}") {
        fun buildRoute(id: Long): String = "accounts/$id"
    }

    data object EditAccount : Screen("accounts/{accountId}/edit") {
        fun buildRoute(id: Long): String = "accounts/$id/edit"
    }

    data object EditTransaction : Screen("transactions/{transactionId}/edit") {
        fun buildRoute(id: Long): String = "transactions/$id/edit"
    }

    data object Categories : Screen("transactions/categories")
}
