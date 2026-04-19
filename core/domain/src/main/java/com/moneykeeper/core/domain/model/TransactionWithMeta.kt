package com.moneykeeper.core.domain.model

data class TransactionWithMeta(
    val transaction: Transaction,
    val accountName: String,
    val accountCurrency: String,
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String,
)
