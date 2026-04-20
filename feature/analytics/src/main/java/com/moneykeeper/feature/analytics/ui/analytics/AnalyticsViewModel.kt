package com.moneykeeper.feature.analytics.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.CategoryRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository,
) : ViewModel() {

    private val _period = MutableStateFlow(YearMonth.now())
    private val _selectedCurrency = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        _period,
        _selectedCurrency,
        accountRepo.observeActiveAccounts(),
    ) { period, currency, accounts ->
        Triple(period, currency, accounts)
    }.flatMapLatest { (period, pickedCurrency, allAccounts) ->
        val availableCurrencies = allAccounts.map { it.currency }.distinct().sorted()
        val effectiveCurrency = pickedCurrency ?: availableCurrencies.firstOrNull() ?: "RUB"
        val from = period.atDay(1)
        val to = period.atEndOfMonth()
        val trendFrom = period.minusMonths(5).atDay(1)
        val accMap = allAccounts.associateBy { it.id }

        // Nested combine to stay within the 5-flow limit
        val catFlow = combine(
            transactionRepo.observeByCategory(effectiveCurrency, from, to, TransactionType.EXPENSE),
            transactionRepo.observeByCategory(effectiveCurrency, from, to, TransactionType.INCOME),
            categoryRepo.observeAll(),
        ) { expSums, incSums, cats -> Triple(expSums, incSums, cats) }

        val accFlow = combine(
            transactionRepo.observeByAccount(effectiveCurrency, from, to, TransactionType.EXPENSE),
            transactionRepo.observeByAccount(effectiveCurrency, from, to, TransactionType.INCOME),
        ) { expAcc, incAcc -> Pair(expAcc, incAcc) }

        combine(
            catFlow,
            transactionRepo.observeMonthlyTrend(effectiveCurrency, trendFrom, to),
            transactionRepo.observePeriodSummary(from, to),
            accFlow,
        ) { (expSums, incSums, cats), trend, periodSummary, (expAccSums, incAccSums) ->
            val catMap = cats.associateBy { it.id }
            val expenseTotal = expSums.sumOf { it.total }
            val incomeTotal = incSums.sumOf { it.total }
            val currencySummary = periodSummary.find { it.currency == effectiveCurrency }
            val periodHasTransactions = currencySummary != null &&
                (currencySummary.income > BigDecimal.ZERO || currencySummary.expense > BigDecimal.ZERO)

            fun buildCategoryItems(
                sums: List<com.moneykeeper.core.domain.analytics.CategorySum>,
                total: BigDecimal,
                fallbackType: CategoryType,
            ) = sums.sortedByDescending { it.total }.mapNotNull { sum ->
                val cat = catMap[sum.categoryId] ?: if (sum.categoryId == 0L)
                    Category(id = 0L, name = "Без категории", type = fallbackType,
                        colorHex = "#9E9E9E", iconName = "MoreHoriz")
                else return@mapNotNull null
                CategoryExpense(
                    category = cat,
                    total = sum.total,
                    percentage = if (total > BigDecimal.ZERO)
                        (sum.total.toDouble() / total.toDouble() * 100.0).toFloat() else 0f,
                    transactionCount = sum.count,
                )
            }

            fun buildAccountItems(
                sums: List<com.moneykeeper.core.domain.analytics.AccountSum>,
                total: BigDecimal,
            ) = sums.sortedByDescending { it.total }.mapNotNull { sum ->
                val acc = accMap[sum.accountId] ?: return@mapNotNull null
                AccountBreakdown(
                    accountId = acc.id,
                    accountName = acc.name,
                    accountColorHex = acc.colorHex,
                    accountIconName = acc.iconName,
                    total = sum.total,
                    percentage = if (total > BigDecimal.ZERO)
                        (sum.total.toDouble() / total.toDouble() * 100.0).toFloat() else 0f,
                    transactionCount = sum.count,
                )
            }

            AnalyticsUiState(
                isLoading = false,
                period = period,
                availableCurrencies = availableCurrencies,
                selectedCurrency = effectiveCurrency,
                categoryExpenses = buildCategoryItems(expSums, expenseTotal, CategoryType.EXPENSE),
                incomeCategoryExpenses = buildCategoryItems(incSums, incomeTotal, CategoryType.INCOME),
                expensesByAccount = buildAccountItems(expAccSums, expenseTotal),
                incomeByAccount = buildAccountItems(incAccSums, incomeTotal),
                monthlyTrend = trend.map { entry ->
                    MonthlyBarEntry(
                        month = YearMonth.parse(entry.yearMonth),
                        income = entry.income,
                        expense = entry.expense,
                    )
                },
                topExpenseCategory = expSums.maxByOrNull { it.total }?.let { catMap[it.categoryId] },
                averageDailyExpense = if (expenseTotal > BigDecimal.ZERO)
                    expenseTotal / BigDecimal(to.dayOfMonth) else BigDecimal.ZERO,
                periodHasTransactions = periodHasTransactions,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun prevPeriod() = _period.update { it.minusMonths(1) }
    fun nextPeriod() = _period.update { it.plusMonths(1) }
    fun jumpToPeriod(ym: YearMonth) { _period.value = ym }
    fun selectCurrency(code: String) { _selectedCurrency.value = code }
}
