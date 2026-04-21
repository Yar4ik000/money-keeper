package com.moneykeeper.feature.transactions.ui.add

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
import com.moneykeeper.feature.transactions.domain.TransactionSaver
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
class AddTransactionViewModel @Inject constructor(
    private val transactionSaver: TransactionSaver,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val preselectedAccountId: Long? =
        savedStateHandle.get<String>("accountId")
            ?.takeIf { it.isNotEmpty() }
            ?.toLongOrNull()

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
                _uiState.update { s ->
                    s.copy(
                        availableAccounts = accounts,
                        availableCategories = categories,
                        selectedAccount = s.selectedAccount
                            ?: accounts.find { it.id == preselectedAccountId }
                            ?: accounts.firstOrNull(),
                    )
                }
            }
        }
    }

    fun onTypeChange(type: TransactionType) =
        _uiState.update { it.copy(type = type, selectedCategory = null, error = null) }

    fun onAmountInputChange(new: String) {
        // Upstream (AmountTextField) already filters to digits + single dot + 2 decimals.
        // Empty means "nothing entered yet" — the field shows a "0" placeholder; save
        // validation handles the zero-amount case via AddTxError.AmountRequired.
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
        transactionSaver.save(
            Transaction(
                accountId = account.id,
                toAccountId = s.selectedToAccount?.id,
                amount = s.amount,
                type = s.type,
                categoryId = s.selectedCategory?.id,
                date = s.date,
                note = s.note,
                createdAt = LocalDateTime.now(),
            ),
            recurringRule = rule,
        )
        _uiState.update { it.copy(saved = true) }
    }
}
