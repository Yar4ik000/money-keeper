package com.moneykeeper.core.domain.analytics

import java.math.BigDecimal

data class CategorySum(val categoryId: Long, val total: BigDecimal, val count: Int)
data class AccountSum(val accountId: Long, val total: BigDecimal, val count: Int)

data class PeriodSummaryByCurrency(
    val currency: String,
    val income: BigDecimal,
    val expense: BigDecimal,
)

data class MonthlyBarEntry(
    val yearMonth: String,   // "2026-04"
    val income: BigDecimal,
    val expense: BigDecimal,
)
