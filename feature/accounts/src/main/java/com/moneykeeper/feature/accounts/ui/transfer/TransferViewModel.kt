package com.moneykeeper.feature.accounts.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class TransferUiState(
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val amount: BigDecimal = BigDecimal.ZERO,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val saved: Boolean = false,
    val error: TransferError? = null,
)

sealed interface TransferError {
    data object SameAccount : TransferError
    data object InvalidAmount : TransferError
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val transactionRepo: TransactionRepository,
    private val txRunner: TransactionRunner,
) : ViewModel() {

    val accounts: StateFlow<List<Account>> = accountRepo.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    fun onFromChange(accountId: Long) = _uiState.update { it.copy(fromAccountId = accountId, error = null) }
    fun onToChange(accountId: Long) = _uiState.update { it.copy(toAccountId = accountId, error = null) }
    fun onAmountChange(amount: BigDecimal) = _uiState.update { it.copy(amount = amount, error = null) }
    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }
    fun onNoteChange(note: String) = _uiState.update { it.copy(note = note) }

    fun transfer() = viewModelScope.launch {
        val s = _uiState.value
        val fromId = s.fromAccountId ?: return@launch
        val toId = s.toAccountId ?: return@launch

        if (fromId == toId) {
            _uiState.update { it.copy(error = TransferError.SameAccount) }
            return@launch
        }
        if (s.amount <= BigDecimal.ZERO) {
            _uiState.update { it.copy(error = TransferError.InvalidAmount) }
            return@launch
        }

        txRunner.run {
            transactionRepo.save(
                Transaction(
                    accountId = fromId,
                    toAccountId = toId,
                    amount = s.amount,
                    type = TransactionType.TRANSFER,
                    categoryId = null,
                    date = s.date,
                    note = s.note,
                    createdAt = LocalDateTime.now(),
                )
            )
            accountRepo.adjustBalance(fromId, s.amount.negate())
            accountRepo.adjustBalance(toId, s.amount)
        }

        _uiState.update { it.copy(saved = true) }
    }
}
