package com.moneykeeper.feature.analytics.ui.analytics

import com.moneykeeper.core.domain.model.Category
import java.math.BigDecimal
import java.time.YearMonth

data class CategoryExpense(
    val category: Category,
    val total: BigDecimal,
    val percentage: Float,
    val transactionCount: Int,
)

data class AccountBreakdown(
    val accountId: Long,
    val accountName: String,
    val total: BigDecimal,
    val percentage: Float,
    val transactionCount: Int,
)

data class MonthlyBarEntry(
    val month: YearMonth,
    val income: BigDecimal,
    val expense: BigDecimal,
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val period: YearMonth = YearMonth.now(),
    val availableCurrencies: List<String> = emptyList(),
    val selectedCurrency: String = "RUB",
    val categoryExpenses: List<CategoryExpense> = emptyList(),
    val incomeCategoryExpenses: List<CategoryExpense> = emptyList(),
    val expensesByAccount: List<AccountBreakdown> = emptyList(),
    val incomeByAccount: List<AccountBreakdown> = emptyList(),
    val monthlyTrend: List<MonthlyBarEntry> = emptyList(),
    val topExpenseCategory: Category? = null,
    val averageDailyExpense: BigDecimal = BigDecimal.ZERO,
    val periodHasTransactions: Boolean = false,
)
