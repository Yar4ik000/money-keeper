package com.moneykeeper.feature.analytics.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.money.CurrencyAmount
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.CategoryRepository
import com.moneykeeper.core.domain.repository.TransactionDeleter
import com.moneykeeper.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionDeleter: TransactionDeleter,
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter())
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    private val derivedTransactions: Flow<HistoryUiState> = _filter.flatMapLatest { f ->
        combine(
            transactionRepo.observe(
                accountId = f.accountId,
                categoryId = f.categoryId,
                type = f.type,
                from = f.from,
                to = f.to,
            ),
            accountRepo.observeActiveAccounts(),
            categoryRepo.observeAll(),
        ) { transactions, accounts, categories ->
            val filtered = if (f.query.isBlank()) transactions
            else transactions.filter { it.transaction.note.contains(f.query, ignoreCase = true) }

            val groups = filtered
                .groupBy { it.transaction.date }
                .entries.sortedByDescending { it.key }
                .map { (date, items) ->
                    val dayTotals = items
                        .groupBy { it.accountCurrency }
                        .map { (currency, txs) ->
                            val net = txs.sumOf { tx ->
                                when (tx.transaction.type) {
                                    TransactionType.INCOME -> tx.transaction.amount
                                    TransactionType.EXPENSE,
                                    TransactionType.SAVINGS -> -tx.transaction.amount
                                    TransactionType.TRANSFER -> BigDecimal.ZERO
                                }
                            }
                            CurrencyAmount(currency, net)
                        }
                        .filter { it.amount.signum() != 0 }
                    TransactionGroup(date = date, items = items, dayTotals = dayTotals)
                }

            val totalsByCurrency = filtered
                .groupBy { it.accountCurrency }
                .map { (currency, txs) ->
                    PeriodSummaryByCurrency(
                        currency = currency,
                        income = txs
                            .filter { it.transaction.type == TransactionType.INCOME }
                            .sumOf { it.transaction.amount },
                        expense = txs
                            .filter { it.transaction.type == TransactionType.EXPENSE }
                            .sumOf { it.transaction.amount },
                    )
                }
                .sortedBy { it.currency }

            HistoryUiState.Success(
                groups = groups,
                totalsByCurrency = totalsByCurrency,
                filter = f,
                availableAccounts = accounts,
                availableCategories = categories,
            )
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        derivedTransactions,
        _selectedIds,
        _isSelectionMode,
    ) { derived, selectedIds, isSelectionMode ->
        when (derived) {
            is HistoryUiState.Loading -> derived
            is HistoryUiState.Success -> derived.copy(
                selectedIds = selectedIds,
                isSelectionMode = isSelectionMode,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    fun updateFilter(update: (HistoryFilter) -> HistoryFilter) {
        _filter.update(update)
        clearSelection()
    }

    fun enterSelectionMode(id: Long) {
        _isSelectionMode.value = true
        _selectedIds.update { it + id }
    }

    fun toggleSelection(id: Long) {
        _selectedIds.update { ids -> if (id in ids) ids - id else ids + id }
        if (_selectedIds.value.isEmpty()) _isSelectionMode.value = false
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return@launch
        clearSelection()
        transactionDeleter.deleteMany(ids)
    }
}
