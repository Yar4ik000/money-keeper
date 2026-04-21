package com.moneykeeper.core.domain.model

import java.math.BigDecimal

data class Budget(
    val id: Long = 0,
    val categoryIds: Set<Long> = emptySet(), // empty = all categories
    val amount: BigDecimal,
    val period: BudgetPeriod,
    val currency: String,
    val accountIds: Set<Long> = emptySet(), // empty = all accounts
    val warningThreshold: Int? = null,  // per-budget override in %; null = use global default
    val criticalThreshold: Int? = null,
)

enum class BudgetPeriod { MONTHLY, WEEKLY }
