package com.moneykeeper.feature.accounts.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val depositRepo: DepositRepository,
) : ViewModel() {

    private val _showArchived = MutableStateFlow(false)
    val showArchived = _showArchived.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState = combine(
        _showArchived.flatMapLatest { archived ->
            if (archived) accountRepo.observeAllAccounts() else accountRepo.observeActiveAccounts()
        },
        accountRepo.observeTotalsByCurrency(),
        depositRepo.observeAll(),
    ) { accounts, totals, deposits ->
        AccountsUiState.Success(
            accounts = accounts,
            totalsByCurrency = totals,
            deposits = deposits,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState.Loading)

    fun setShowArchived(show: Boolean) {
        _showArchived.value = show
    }

    fun archiveAccount(id: Long) = viewModelScope.launch {
        accountRepo.archive(id)
    }

    fun unarchiveAccount(id: Long) = viewModelScope.launch {
        accountRepo.unarchive(id)
    }
}
