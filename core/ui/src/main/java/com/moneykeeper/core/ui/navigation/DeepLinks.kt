package com.moneykeeper.core.ui.navigation

object DeepLinks {
    const val SCHEME = "moneykeeper"
    const val ACCOUNT_DETAIL_PATTERN = "$SCHEME://accounts/{accountId}"
    fun accountDetail(accountId: Long) = "$SCHEME://accounts/$accountId"
}
