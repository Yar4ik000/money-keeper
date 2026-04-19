package com.moneykeeper.feature.accounts.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val depositRepo: DepositRepository,
) : ViewModel() {

    val uiState = combine(
        accountRepo.observeActiveAccounts(),
        accountRepo.observeTotalsByCurrency(),
        depositRepo.observeAll(),
    ) { accounts, totals, deposits ->
        AccountsUiState.Success(
            accounts = accounts,
            totalsByCurrency = totals,
            deposits = deposits,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState.Loading)

    fun archiveAccount(id: Long) = viewModelScope.launch {
        accountRepo.archive(id)
    }
}
