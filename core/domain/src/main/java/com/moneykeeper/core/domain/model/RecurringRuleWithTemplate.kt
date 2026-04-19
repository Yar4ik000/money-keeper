package com.moneykeeper.core.domain.model

data class RecurringRuleWithTemplate(
    val rule: RecurringRule,
    val templateTransaction: Transaction,
    val accountName: String,
    val categoryName: String,
    val description: String,
)
