package com.moneykeeper.feature.analytics.ui.category

import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.TransactionWithMeta
import java.math.BigDecimal
import java.time.YearMonth

data class CategoryMonthlyEntry(
    val month: YearMonth,
    val total: BigDecimal,
)

data class CategoryAnalyticsUiState(
    val isLoading: Boolean = true,
    val category: Category? = null,
    val period: YearMonth = YearMonth.now(),
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val transactions: List<TransactionWithMeta> = emptyList(),
    val monthlyTrend: List<CategoryMonthlyEntry> = emptyList(),
)
