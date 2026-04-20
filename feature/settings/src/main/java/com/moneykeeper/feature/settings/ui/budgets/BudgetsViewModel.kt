package com.moneykeeper.feature.settings.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.BudgetPeriod
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.TransactionType
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
    val category: Category?,
    val spent: BigDecimal,
)

data class BudgetsUiState(
    val items: List<BudgetWithSpent> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepo: BudgetRepository,
    private val categoryRepo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
) : ViewModel() {

    val state: StateFlow<BudgetsUiState> = combine(
        budgetRepo.observeAll(),
        categoryRepo.observeByType(CategoryType.EXPENSE),
        transactionRepo.observeByCategory(
            currency = "RUB",
            from = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()),
            to = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()),
            type = TransactionType.EXPENSE,
        ),
    ) { budgets, categories, spentList ->
        val catById = categories.associateBy { it.id }
        val spentByCat = spentList.associate { it.categoryId to it.total }
        BudgetsUiState(
            items = budgets.map { b ->
                BudgetWithSpent(
                    budget = b,
                    category = catById[b.categoryId],
                    spent = spentByCat[b.categoryId] ?: BigDecimal.ZERO,
                )
            },
            categories = categories,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun save(budget: Budget) = viewModelScope.launch { budgetRepo.save(budget) }

    fun delete(id: Long) = viewModelScope.launch { budgetRepo.delete(id) }
}
