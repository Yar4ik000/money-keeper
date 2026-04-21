package com.moneykeeper.feature.settings.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.BudgetPeriod
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.BudgetRepository
import com.moneykeeper.core.domain.repository.CategoryRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class BudgetWithSpent(
    val budget: Budget,
    val categories: List<Category>,
    val categoryNames: String,
    val spent: BigDecimal,
    val accountNames: String,
)

data class BudgetsUiState(
    val items: List<BudgetWithSpent> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepo: BudgetRepository,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
) : ViewModel() {

    val state: StateFlow<BudgetsUiState> = combine(
        budgetRepo.observeAll(),
        categoryRepo.observeByType(CategoryType.EXPENSE),
        accountRepo.observeActiveAccounts(),
        transactionRepo.observe(
            from = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()),
            to = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()),
        ),
    ) { budgets, categories, accounts, txWithMeta ->
        val catById = categories.associateBy { it.id }
        val accountById = accounts.associateBy { it.id }

        BudgetsUiState(
            items = budgets.map { budget ->
                val spent = calculateBudgetSpent(budget, txWithMeta)

                val budgetCategories = if (budget.categoryIds.isEmpty()) emptyList()
                    else budget.categoryIds.mapNotNull { catById[it] }
                val categoryNames = if (budget.categoryIds.isEmpty()) "Все категории"
                    else budgetCategories.joinToString(", ") { it.name }

                val accountNames = if (budget.accountIds.isEmpty()) "Все счета"
                    else budget.accountIds.mapNotNull { id -> accountById[id]?.name }.joinToString(", ")

                BudgetWithSpent(
                    budget = budget,
                    categories = budgetCategories,
                    categoryNames = categoryNames,
                    spent = spent,
                    accountNames = accountNames,
                )
            },
            categories = categories,
            accounts = accounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun save(budget: Budget) = viewModelScope.launch { budgetRepo.save(budget) }

    fun delete(id: Long) = viewModelScope.launch { budgetRepo.delete(id) }
}

/**
 * Sums expense amounts that belong to [budget]. A transaction is counted only if:
 * - type is EXPENSE
 * - the source account's currency matches the budget's currency
 * - category matches (or budget targets all categories)
 * - account matches (or budget targets all accounts)
 */
internal fun calculateBudgetSpent(
    budget: Budget,
    txWithMeta: List<TransactionWithMeta>,
): BigDecimal = txWithMeta
    .filter { meta ->
        meta.transaction.type == TransactionType.EXPENSE &&
        meta.accountCurrency == budget.currency &&
        (budget.categoryIds.isEmpty() || meta.transaction.categoryId in budget.categoryIds) &&
        (budget.accountIds.isEmpty() || meta.transaction.accountId in budget.accountIds)
    }
    .fold(BigDecimal.ZERO) { acc, meta -> acc + meta.transaction.amount }
