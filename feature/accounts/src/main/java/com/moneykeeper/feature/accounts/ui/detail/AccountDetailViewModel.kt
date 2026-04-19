package com.moneykeeper.feature.accounts.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    accountRepo: AccountRepository,
    transactionRepo: TransactionRepository,
    depositRepo: DepositRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val accountId: Long = savedStateHandle.get<Long>("accountId")!!

    val account = accountRepo.observeAllAccounts()
        .map { list -> list.find { it.id == accountId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactions = transactionRepo.observe(
        accountId = accountId,
        categoryId = null,
        type = null,
        from = LocalDate.of(2000, 1, 1),
        to = LocalDate.of(2100, 12, 31),
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deposit = depositRepo.observeAll()
        .map { list -> list.find { it.accountId == accountId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
