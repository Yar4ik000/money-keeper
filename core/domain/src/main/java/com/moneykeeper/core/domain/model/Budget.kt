package com.moneykeeper.core.domain.model

import java.math.BigDecimal

data class Budget(
    val id: Long = 0,
    val categoryId: Long,
    val amount: BigDecimal,
    val period: BudgetPeriod,
    val currency: String,
    val accountIds: Set<Long> = emptySet(), // empty = all accounts
)

enum class BudgetPeriod { MONTHLY, WEEKLY }
