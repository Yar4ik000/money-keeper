package com.moneykeeper.feature.analytics.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryAnalyticsViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: Long = checkNotNull(savedStateHandle["categoryId"])
    private val _period = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<CategoryAnalyticsUiState> = combine(
        _period,
        categoryRepo.observeAll(),
    ) { period, categories ->
        period to categories
    }.flatMapLatest { (period, categories) ->
        val category = categories.find { it.id == categoryId }
        val from = period.atDay(1)
        val to = period.atEndOfMonth()
        val trendFrom = YearMonth.now().minusMonths(5).atDay(1)
        val trendTo = LocalDate.now()

        combine(
            transactionRepo.observe(categoryId = categoryId, from = from, to = to),
            transactionRepo.observe(categoryId = categoryId, from = trendFrom, to = trendTo),
        ) { periodTxs, trendTxs ->
            val totalAmount = periodTxs.sumOf { it.transaction.amount }

            val monthlyTrend = trendTxs
                .groupBy { YearMonth.from(it.transaction.date) }
                .map { (month, txs) ->
                    CategoryMonthlyEntry(
                        month = month,
                        total = txs.sumOf { it.transaction.amount },
                    )
                }
                .sortedBy { it.month }

            CategoryAnalyticsUiState(
                isLoading = false,
                category = category,
                period = period,
                totalAmount = totalAmount,
                transactions = periodTxs,
                monthlyTrend = monthlyTrend,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryAnalyticsUiState())

    fun prevPeriod() = _period.update { it.minusMonths(1) }
    fun nextPeriod() = _period.update { it.plusMonths(1) }
}
