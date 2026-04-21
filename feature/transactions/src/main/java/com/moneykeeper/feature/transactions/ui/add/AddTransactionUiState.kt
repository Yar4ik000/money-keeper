package com.moneykeeper.feature.transactions.ui.add

import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

data class AddTransactionUiState(
    val amountInput: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val selectedAccount: Account? = null,
    val selectedToAccount: Account? = null,
    val selectedCategory: Category? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val recurringRule: RecurringRule? = null,
    val isRecurring: Boolean = false,
    val availableAccounts: List<Account> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val saved: Boolean = false,
    val error: AddTxError? = null,
) {
    val amount: BigDecimal get() = amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
}

sealed interface AddTxError {
    data object AmountRequired : AddTxError
    data object AccountRequired : AddTxError
    data object ToAccountRequired : AddTxError
}
