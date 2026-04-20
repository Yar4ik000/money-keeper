package com.moneykeeper.feature.analytics.ui.history

import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.money.CurrencyAmount
import java.time.LocalDate

data class HistoryFilter(
    val from: LocalDate = LocalDate.now().withDayOfMonth(1),
    val to: LocalDate = LocalDate.now(),
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val type: TransactionType? = null,
    val query: String = "",
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(
        val groups: List<TransactionGroup>,
        val totalsByCurrency: List<PeriodSummaryByCurrency>,
        val filter: HistoryFilter,
        val availableAccounts: List<Account> = emptyList(),
        val availableCategories: List<Category> = emptyList(),
        val selectedIds: Set<Long> = emptySet(),
        val isSelectionMode: Boolean = false,
    ) : HistoryUiState
}

data class TransactionGroup(
    val date: LocalDate,
    val items: List<TransactionWithMeta>,
    val dayTotals: List<CurrencyAmount>,
)
