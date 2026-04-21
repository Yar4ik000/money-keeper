package com.moneykeeper.feature.accounts.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    private val _reorderDraft = MutableStateFlow<List<Account>?>(null)
    val reorderDraft = _reorderDraft.asStateFlow()

    val isReordering = _reorderDraft.value != null

    @OptIn(ExperimentalCoroutinesApi::class)
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

    fun startReorder() = viewModelScope.launch {
        // Always reorder over the active (non-archived) list, regardless of toggle state
        _reorderDraft.value = accountRepo.observeActiveAccounts().first()
    }

    fun moveAccount(id: Long, direction: Int) {
        val current = _reorderDraft.value ?: return
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val newIndex = (index + direction).coerceIn(0, current.size - 1)
        if (newIndex == index) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(newIndex, item)
        _reorderDraft.value = mutable
    }

    fun saveReorder() = viewModelScope.launch {
        val draft = _reorderDraft.value ?: return@launch
        accountRepo.updateSortOrders(draft.map { it.id })
        _reorderDraft.value = null
    }

    fun cancelReorder() {
        _reorderDraft.value = null
    }
}
