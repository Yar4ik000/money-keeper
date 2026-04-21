package com.moneykeeper.feature.transactions.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.CategoryRepository
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.feature.transactions.domain.TransactionSaver
import com.moneykeeper.feature.transactions.ui.add.AddTxError
import com.moneykeeper.feature.transactions.ui.add.AddTransactionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    private val transactionSaver: TransactionSaver,
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val recurringRuleRepo: RecurringRuleRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val transactionId: Long = checkNotNull(savedStateHandle["transactionId"])
    private var originalTransaction: Transaction? = null

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                accountRepo.observeActiveAccounts(),
                categoryRepo.observeAll(),
            ) { accounts, categories ->
                accounts to categories
            }.collect { (accounts, categories) ->
                val tx = originalTransaction
                    ?: transactionRepo.getById(transactionId)?.also { originalTransaction = it }
                    ?: return@collect

                val rule = tx.recurringRuleId?.let { recurringRuleRepo.getById(it) }
                _uiState.update { s ->
                    s.copy(
                        amountInput = tx.amount.toPlainString(),
                        type = tx.type,
                        selectedAccount = accounts.find { it.id == tx.accountId },
                        selectedToAccount = tx.toAccountId?.let { id ->
                            accounts.find { it.id == id }
                        },
                        selectedCategory = tx.categoryId?.let { id ->
                            categories.find { it.id == id }
                        },
                        date = tx.date,
                        note = tx.note,
                        isRecurring = rule != null,
                        recurringRule = rule,
                        availableAccounts = accounts,
                        availableCategories = categories,
                    )
                }
            }
        }
    }

    fun onTypeChange(type: TransactionType) =
        _uiState.update { it.copy(type = type, selectedCategory = null, error = null) }

    fun onAmountInputChange(new: String) {
        _uiState.update { it.copy(amountInput = new, error = null) }
    }

    fun onAccountSelect(account: Account) =
        _uiState.update { it.copy(selectedAccount = account, error = null) }

    fun onToAccountSelect(account: Account) =
        _uiState.update { it.copy(selectedToAccount = account, error = null) }

    fun onCategorySelect(category: Category) =
        _uiState.update { it.copy(selectedCategory = category, error = null) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

    fun onNoteChange(note: String) = _uiState.update { it.copy(note = note) }

    fun onRecurringToggle(on: Boolean) =
        _uiState.update {
            it.copy(isRecurring = on, recurringRule = if (!on) null else it.recurringRule)
        }

    fun onRecurringRuleChange(rule: RecurringRule) =
        _uiState.update { it.copy(recurringRule = rule) }

    fun onSave() = viewModelScope.launch {
        val s = _uiState.value
        val old = originalTransaction ?: return@launch
        if (s.amount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(error = AddTxError.AmountRequired) }
            return@launch
        }
        val account = s.selectedAccount ?: run {
            _uiState.update { it.copy(error = AddTxError.AccountRequired) }
            return@launch
        }
        if (s.type == TransactionType.TRANSFER && s.selectedToAccount == null) {
            _uiState.update { it.copy(error = AddTxError.ToAccountRequired) }
            return@launch
        }
        val rule = if (s.isRecurring) s.recurringRule?.copy(startDate = s.date) else null
        val newTx = Transaction(
            id = transactionId,
            accountId = account.id,
            toAccountId = s.selectedToAccount?.id,
            amount = s.amount,
            type = s.type,
            categoryId = s.selectedCategory?.id,
            date = s.date,
            note = s.note,
            createdAt = old.createdAt,
        )
        transactionSaver.replace(old, newTx, rule)
        _uiState.update { it.copy(saved = true) }
    }
}
