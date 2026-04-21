package com.moneykeeper.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.repository.BudgetRepository
import com.moneykeeper.core.domain.repository.SettingsRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class BudgetsBadge { NONE, WARNING, CRITICAL }

@HiltViewModel
class BudgetsBadgeViewModel @Inject constructor(
    private val budgetRepo: BudgetRepository,
    private val transactionRepo: TransactionRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val badge: StateFlow<BudgetsBadge> = combine(
        budgetRepo.observeAll(),
        transactionRepo.observe(
            from = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()),
            to = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()),
        ),
        settingsRepo.settings,
    ) { budgets, txs, settings ->
        val statuses = budgets.map { budget ->
            val spent = spentFor(budget, txs)
            val percent = if (budget.amount > BigDecimal.ZERO)
                (spent.toDouble() / budget.amount.toDouble() * 100).toInt()
            else 0
            val critical = budget.criticalThreshold ?: settings.budgetCriticalThreshold
            val warning = budget.warningThreshold ?: settings.budgetWarningThreshold
            when {
                percent >= critical -> BudgetsBadge.CRITICAL
                percent >= warning  -> BudgetsBadge.WARNING
                else                -> BudgetsBadge.NONE
            }
        }
        when {
            statuses.any { it == BudgetsBadge.CRITICAL } -> BudgetsBadge.CRITICAL
            statuses.any { it == BudgetsBadge.WARNING }  -> BudgetsBadge.WARNING
            else                                         -> BudgetsBadge.NONE
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsBadge.NONE)

    private fun spentFor(budget: Budget, txs: List<TransactionWithMeta>): BigDecimal = txs
        .filter { meta ->
            meta.transaction.type == TransactionType.EXPENSE &&
            meta.accountCurrency == budget.currency &&
            (budget.categoryIds.isEmpty() || meta.transaction.categoryId in budget.categoryIds) &&
            (budget.accountIds.isEmpty() || meta.transaction.accountId in budget.accountIds)
        }
        .fold(BigDecimal.ZERO) { acc, meta -> acc + meta.transaction.amount }
}
